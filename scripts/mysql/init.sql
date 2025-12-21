-- WIDYU 개발 환경용 MySQL 초기화 스크립트
-- MySQL 컨테이너 최초 시작 시 자동으로 실행됩니다

-- UTF-8 인코딩 설정
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

-- 권한 부여 (커스텀 사용자 설정이 필요한 경우)
-- GRANT ALL PRIVILEGES ON *.* TO 'widyu_user'@'%';
-- FLUSH PRIVILEGES;

-- 선택사항: 추가 데이터베이스 또는 테이블 생성
-- CREATE DATABASE IF NOT EXISTS widyu_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

SELECT 'MySQL initialization completed!' AS status;
