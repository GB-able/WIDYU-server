-- 운영 DB에서 애플리케이션 배포 전에 1회 실행합니다.
-- ddl-auto: update는 기존 테이블에 NOT NULL 컬럼과 unique 제약을 안전하게 추가하지 못합니다.
-- 목적: 같은 날 동일 스케줄의 복약 인증 중복을 DB에서 차단합니다 (이슈 #510).

-- 1. 일자 컬럼을 nullable로 추가합니다.
ALTER TABLE medication_proof
    ADD COLUMN verified_date DATE NULL;

-- 2. 기존 행의 일자를 백필합니다.
UPDATE medication_proof
SET verified_date = DATE(verified_at)
WHERE verified_date IS NULL;

-- 3. 중복 행을 확인합니다. 결과가 있으면 4번을 실행하고, 없으면 4번을 건너뜁니다.
SELECT medicine_schedule_id, verified_date, COUNT(*) AS cnt
FROM medication_proof
GROUP BY medicine_schedule_id, verified_date
HAVING cnt > 1;

-- 4. 중복이 있으면 스케줄·일자별로 verified_at이 가장 이른 행만 남깁니다.
--    (조회 응답의 대표 인증 선택 기준과 동일합니다.)
DELETE p
FROM medication_proof p
         JOIN (
    SELECT medicine_schedule_id,
           verified_date,
           MIN(verified_at) AS earliest_verified_at
    FROM medication_proof
    GROUP BY medicine_schedule_id, verified_date
    HAVING COUNT(*) > 1
) d
              ON p.medicine_schedule_id = d.medicine_schedule_id
                  AND p.verified_date = d.verified_date
                  AND p.verified_at > d.earliest_verified_at;

-- 5. NOT NULL로 전환하고 unique 제약을 겁니다.
ALTER TABLE medication_proof
    MODIFY COLUMN verified_date DATE NOT NULL,
    ADD CONSTRAINT uk_medication_proof_schedule_date UNIQUE (medicine_schedule_id, verified_date);
