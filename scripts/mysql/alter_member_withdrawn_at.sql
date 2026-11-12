-- #1017: member에 회원 본인의 탈퇴 시각을 추가한다 (LLD-0137 4.1절).
-- 적용 시점: 운영 구축 때 적용. 개발 서버는 ddl-auto: update가 칸을 만들므로 백필만 필요하면 실행한다.
--
-- INACTIVE는 회원 탈퇴와 관리자 정지를 함께 뜻한다. withdrawn_at은 탈퇴에만 채워 둘을 가른다.
ALTER TABLE member
    ADD COLUMN withdrawn_at DATETIME(6) NULL;

-- 기존 탈퇴자 백필. 탈퇴는 이름을 '탈퇴회원'으로 가리고 번호를 비우므로 관리자 정지와 구분된다.
-- 기준 시각은 마지막 수정 시각이다(LLD-0137 9절 3번). 실행 전에 대상 수를 먼저 센다.
--   SELECT COUNT(*) FROM member
--   WHERE status = 'INACTIVE' AND name = '탈퇴회원' AND phone_number IS NULL AND withdrawn_at IS NULL;
UPDATE member
SET withdrawn_at = updated_at
WHERE status = 'INACTIVE'
  AND name = '탈퇴회원'
  AND phone_number IS NULL
  AND withdrawn_at IS NULL;
