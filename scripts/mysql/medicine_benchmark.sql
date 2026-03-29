-- ============================================================
-- 의약품 검색 성능 벤치마크: LIKE vs FULLTEXT
-- 실행 전 필수: FULLTEXT 인덱스가 생성되어 있어야 함
--   → scripts/mysql/add_medicine_fulltext_index.sql 먼저 실행
-- ============================================================

-- 프로파일링 활성화
SET profiling = 1;
SET profiling_history_size = 40;

-- ============================================================
-- Step 1. 테스트 데이터 확인
-- ============================================================
SELECT COUNT(*) AS total_medicine_count FROM medicine;

-- ============================================================
-- Step 2. 테스트 데이터 시딩 (데이터가 부족한 경우에만 실행)
-- 실제 배치 동기화 후 측정하려면 이 블록은 건너뜀
-- ============================================================
DROP PROCEDURE IF EXISTS seed_medicine_data;

DELIMITER $$
CREATE PROCEDURE seed_medicine_data()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE existing_count INT;

    SELECT COUNT(*) INTO existing_count FROM medicine;

    IF existing_count < 10000 THEN
        WHILE i <= 30000 DO
            INSERT IGNORE INTO medicine (item_seq, item_name, entp_name, item_image, use_method_qesitm, efcy_qesitm, created_at, updated_at)
            VALUES (
                CONCAT('TEST', LPAD(i, 6, '0')),
                CASE (i % 20)
                    WHEN 0  THEN CONCAT('타이레놀 ', i, 'mg 정')
                    WHEN 1  THEN CONCAT('이부프로펜 ', i, 'mg')
                    WHEN 2  THEN CONCAT('아스피린 ', i, 'mg 정')
                    WHEN 3  THEN CONCAT('판콜에이 ', i, '정')
                    WHEN 4  THEN CONCAT('게보린 ', i, '정')
                    WHEN 5  THEN CONCAT('부루펜 ', i, 'mg 시럽')
                    WHEN 6  THEN CONCAT('코대원 포르테 ', i, 'ml')
                    WHEN 7  THEN CONCAT('판피린큐 ', i, '정')
                    WHEN 8  THEN CONCAT('비타민C ', i, 'mg')
                    WHEN 9  THEN CONCAT('오메가3 ', i, 'mg 연질캡슐')
                    WHEN 10 THEN CONCAT('마그네슘 ', i, 'mg 정')
                    WHEN 11 THEN CONCAT('칼슘 디 ', i, '정')
                    WHEN 12 THEN CONCAT('종합비타민 ', i, '캡슐')
                    WHEN 13 THEN CONCAT('지르텍 ', i, 'mg 정')
                    WHEN 14 THEN CONCAT('클라리틴 ', i, '정')
                    WHEN 15 THEN CONCAT('알레그라 ', i, 'mg')
                    WHEN 16 THEN CONCAT('베아제 ', i, '정')
                    WHEN 17 THEN CONCAT('훼스탈 플러스 ', i, '정')
                    WHEN 18 THEN CONCAT('노스카나 ', i, 'mg 크림')
                    ELSE         CONCAT('일반의약품 ', i, '호')
                END,
                CONCAT('제약사', (i % 50) + 1),
                NULL,
                '성인: 1회 1정씩 1일 3회 복용',
                '해열, 진통, 소염에 효과적입니다.',
                NOW(),
                NOW()
            );
            SET i = i + 1;
        END WHILE;
        SELECT CONCAT(30000 - existing_count, '건 시딩 완료') AS result;
    ELSE
        SELECT CONCAT('기존 데이터 ', existing_count, '건 존재 - 시딩 생략') AS result;
    END IF;
END$$
DELIMITER ;

CALL seed_medicine_data();
DROP PROCEDURE IF EXISTS seed_medicine_data;

SELECT COUNT(*) AS total_after_seed FROM medicine;

-- ============================================================
-- Step 3. 쿼리 플랜 비교 (EXPLAIN)
-- ============================================================
SELECT '=== [OLD] LIKE 쿼리 플랜 ===' AS benchmark_step;
EXPLAIN SELECT * FROM medicine
WHERE LOWER(item_name) LIKE LOWER(CONCAT('%', '타이레놀', '%'));

SELECT '=== [NEW] FULLTEXT 쿼리 플랜 ===' AS benchmark_step;
EXPLAIN SELECT * FROM medicine
WHERE MATCH(item_name) AGAINST('타이레놀' IN BOOLEAN MODE)
LIMIT 10;

-- ============================================================
-- Step 4. 실행 시간 측정 (타임스탬프 기반, 각 10회)
-- ============================================================

-- LIKE 측정 시작 (COUNT로 데이터 전송 오버헤드 제거)
SET @like_start = NOW(6);

SELECT COUNT(*) FROM medicine WHERE LOWER(item_name) LIKE LOWER(CONCAT('%', '타이레놀', '%'));
SELECT COUNT(*) FROM medicine WHERE LOWER(item_name) LIKE LOWER(CONCAT('%', '이부프로펜', '%'));
SELECT COUNT(*) FROM medicine WHERE LOWER(item_name) LIKE LOWER(CONCAT('%', '아스피린', '%'));
SELECT COUNT(*) FROM medicine WHERE LOWER(item_name) LIKE LOWER(CONCAT('%', '비타민', '%'));
SELECT COUNT(*) FROM medicine WHERE LOWER(item_name) LIKE LOWER(CONCAT('%', '게보린', '%'));
SELECT COUNT(*) FROM medicine WHERE LOWER(item_name) LIKE LOWER(CONCAT('%', '판콜', '%'));
SELECT COUNT(*) FROM medicine WHERE LOWER(item_name) LIKE LOWER(CONCAT('%', '오메가', '%'));
SELECT COUNT(*) FROM medicine WHERE LOWER(item_name) LIKE LOWER(CONCAT('%', '마그네슘', '%'));
SELECT COUNT(*) FROM medicine WHERE LOWER(item_name) LIKE LOWER(CONCAT('%', '지르텍', '%'));
SELECT COUNT(*) FROM medicine WHERE LOWER(item_name) LIKE LOWER(CONCAT('%', '베아제', '%'));

SET @like_end = NOW(6);

-- FULLTEXT 측정 시작 (COUNT로 동일 조건)
SET @fulltext_start = NOW(6);

SELECT COUNT(*) FROM medicine WHERE MATCH(item_name) AGAINST('타이레놀' IN BOOLEAN MODE);
SELECT COUNT(*) FROM medicine WHERE MATCH(item_name) AGAINST('이부프로펜' IN BOOLEAN MODE);
SELECT COUNT(*) FROM medicine WHERE MATCH(item_name) AGAINST('아스피린' IN BOOLEAN MODE);
SELECT COUNT(*) FROM medicine WHERE MATCH(item_name) AGAINST('비타민' IN BOOLEAN MODE);
SELECT COUNT(*) FROM medicine WHERE MATCH(item_name) AGAINST('게보린' IN BOOLEAN MODE);
SELECT COUNT(*) FROM medicine WHERE MATCH(item_name) AGAINST('판콜' IN BOOLEAN MODE);
SELECT COUNT(*) FROM medicine WHERE MATCH(item_name) AGAINST('오메가' IN BOOLEAN MODE);
SELECT COUNT(*) FROM medicine WHERE MATCH(item_name) AGAINST('마그네슘' IN BOOLEAN MODE);
SELECT COUNT(*) FROM medicine WHERE MATCH(item_name) AGAINST('지르텍' IN BOOLEAN MODE);
SELECT COUNT(*) FROM medicine WHERE MATCH(item_name) AGAINST('베아제' IN BOOLEAN MODE);

SET @fulltext_end = NOW(6);

-- ============================================================
-- Step 5. 결과 출력
-- ============================================================
SELECT
    'LIKE (OLD)'                                                              AS search_type,
    10                                                                        AS query_count,
    ROUND(TIMESTAMPDIFF(MICROSECOND, @like_start, @like_end) / 10 / 1000, 3) AS avg_ms,
    ROUND(TIMESTAMPDIFF(MICROSECOND, @like_start, @like_end) / 1000, 3)      AS total_ms
UNION ALL
SELECT
    'FULLTEXT (NEW)',
    10,
    ROUND(TIMESTAMPDIFF(MICROSECOND, @fulltext_start, @fulltext_end) / 10 / 1000, 3),
    ROUND(TIMESTAMPDIFF(MICROSECOND, @fulltext_start, @fulltext_end) / 1000, 3);

SET profiling = 0;
