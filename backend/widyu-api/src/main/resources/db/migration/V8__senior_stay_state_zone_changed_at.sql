-- 보호자 카드의 체류 시간을 안심구역 상태가 바뀐 시각부터 센다(LLD-0191).
-- 기존 행은 언제 구역 상태가 바뀌었는지 모르므로 30m 체류 시작 시각으로 채운다.
-- 기본값(지금)은 이 칸을 모르는 이전 버전으로 되돌렸을 때 첫 행 INSERT가 실패하지 않게 둔다. 새 버전은 늘 값을 넣는다.
ALTER TABLE `senior_stay_state`
  ADD COLUMN `zone_changed_at` datetime(6) DEFAULT NULL AFTER `stay_started_at`;

UPDATE `senior_stay_state` SET `zone_changed_at` = `stay_started_at` WHERE `zone_changed_at` IS NULL;

ALTER TABLE `senior_stay_state`
  MODIFY COLUMN `zone_changed_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
