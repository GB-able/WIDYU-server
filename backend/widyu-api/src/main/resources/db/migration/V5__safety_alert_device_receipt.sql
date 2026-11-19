-- 긴급 알림이 폰에 실제로 닿은 시각을 앱 신호로 받는다(LLD-0181).
-- 비어 있으면 「도착 미확인」이다. 기존 행은 신호가 없었으므로 NULL로 둔다.
ALTER TABLE `fcm_outbox`
  ADD COLUMN `device_received_at` datetime(6) DEFAULT NULL,
  ADD COLUMN `device_reported_at_ms` bigint DEFAULT NULL;

ALTER TABLE `decision_record`
  ADD COLUMN `alert_device_received_at_ms` bigint DEFAULT NULL AFTER `alert_delivered`;
