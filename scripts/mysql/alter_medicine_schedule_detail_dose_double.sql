-- #1001: 반 알약(0.5정 단위) 복용량을 저장하도록 dose를 정수에서 DOUBLE로 바꾼다.
-- 엔티티 배포 전에 dev·prod에서 1회 실행한다. ddl-auto: update(dev)는 기존 열 타입을 바꾸지 않아
-- 2.5가 INT 열에 반올림돼 3으로 저장되고, prod(ddl-auto: validate)는 이 변경 전에는 기동하지 않는다.
-- 기존 정수 값은 그대로 보존된다(1 → 1.0).
ALTER TABLE medicine_schedule_detail MODIFY COLUMN dose DOUBLE NOT NULL;
