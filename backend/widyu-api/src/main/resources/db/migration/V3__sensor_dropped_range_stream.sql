-- 버린 구간 기록에 데이터 종류를 더한다(LLD-0166 4.1).
-- 기존 행은 모두 워치 IMU 큐 넘침(stream "imu_dropped")이라 imu_watch로 채운다.
ALTER TABLE `sensor_dropped_range`
  ADD COLUMN `dropped_stream` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'imu_watch' AFTER `session_id`;

-- 엔티티가 늘 값을 넣는다. 기본값이 남으면 값을 빠뜨린 코드가 imu_watch로 조용히 저장된다.
ALTER TABLE `sensor_dropped_range`
  ALTER COLUMN `dropped_stream` DROP DEFAULT;
