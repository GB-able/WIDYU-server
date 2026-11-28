-- 보호자 카드의 체류 시간을 안심구역 상태가 바뀐 시각부터 센다(LLD-0191).
-- 기존 행은 언제 구역 상태가 바뀌었는지 모르므로 30m 체류 시작 시각으로 채운다.
ALTER TABLE `senior_stay_state`
  ADD COLUMN `zone_changed_at` datetime(6) DEFAULT NULL AFTER `stay_started_at`;

UPDATE `senior_stay_state` SET `zone_changed_at` = `stay_started_at` WHERE `zone_changed_at` IS NULL;

ALTER TABLE `senior_stay_state`
  MODIFY COLUMN `zone_changed_at` datetime(6) NOT NULL;
