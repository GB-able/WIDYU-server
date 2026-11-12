-- #987: 센서 묶음이 처음 저장된 경로(WS, REST)를 남긴다 (LLD-0128).
-- 운영은 ddl-auto: validate라 배포 전에 적용한다. NULL 허용이라 기존 버전 애플리케이션과 충돌하지 않는다.
-- 중복 수신은 기존 행을 돌려주므로 수신 시도 전체가 아니라 최초 저장 경로다.
-- 기존 행은 경로를 추측해 채우지 않고 NULL로 둔다. MySQL ENUM이 아니라 VARCHAR라 값 추가에 ALTER가 필요 없다.
ALTER TABLE sensor_batch
    ADD COLUMN ingest_channel VARCHAR(8) NULL;
