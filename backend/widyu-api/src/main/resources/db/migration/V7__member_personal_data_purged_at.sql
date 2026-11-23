-- 탈퇴 30일 뒤 개인 자료를 지운 시각(LLD-0187). 비어 있는 탈퇴 회원만 정리 작업이 고른다.
ALTER TABLE member ADD COLUMN personal_data_purged_at datetime(6) DEFAULT NULL;
