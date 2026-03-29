-- 의약품 이름 FULLTEXT 인덱스 (한국어 ngram 파서 적용)
-- 최초 1회만 실행 (ddl-auto가 이 인덱스를 관리하지 않음)
--
-- 실행 방법:
--   docker compose exec mysql mysql -u<user> -p<password> widyu < scripts/mysql/add_medicine_fulltext_index.sql
--
-- ngram 파서: 한국어 부분 문자열 검색을 위해 2-gram 단위로 토크나이징
-- 예) "타이레놀" → "타이", "이레", "레놀" 단위로 인덱싱
--
-- MySQL 서버 설정 (기본값으로 동작):
--   ngram_token_size = 2 (기본값)

ALTER TABLE medicine
    ADD FULLTEXT INDEX ft_medicine_item_name (item_name) WITH PARSER ngram;
