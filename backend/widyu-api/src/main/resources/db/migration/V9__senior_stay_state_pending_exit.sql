-- 안심구역 안에서 밖으로 처음 찍힌 시각(LLD-0192). 이 시각부터 확정 시간이 지나도록 계속 밖이면 이탈로 확정한다.
-- 비어 있으면 확정 대기가 없다. 기존 행은 대기 중이 아니므로 NULL로 둔다.
ALTER TABLE `senior_stay_state`
  ADD COLUMN `pending_exit_since` datetime(6) DEFAULT NULL;
