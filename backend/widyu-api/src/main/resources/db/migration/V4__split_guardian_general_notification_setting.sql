-- 보호자 「일반」 스위치를 위듀케어 알림 정책서 2절의 스위치로 나눈다(LLD-0175).
-- 「일반」을 끈 보호자는 그 스위치가 덮던 알림과 새 운영 알림까지 모두 끈 상태로 옮긴다.
-- 이미 같은 스위치 행이 있으면 건드리지 않아 두 번 실행해도 결과가 같다.
INSERT INTO `member_notification_setting` (`member_id`, `category`, `enabled`)
SELECT s.`member_id`, g.`category`, 0
FROM `member_notification_setting` s
JOIN `member` m ON m.`id` = s.`member_id` AND m.`type` = 'GUARDIAN'
CROSS JOIN (
    SELECT 'SENIOR_OK_NOTICE' AS `category`
    UNION ALL SELECT 'HEALTH_SCHEDULE'
    UNION ALL SELECT 'GOAL_ACHIEVED'
    UNION ALL SELECT 'ALBUM_NEW_POST'
    UNION ALL SELECT 'ALBUM_COMMENT'
    UNION ALL SELECT 'ALBUM_LIKE'
    UNION ALL SELECT 'ALBUM_SENIOR_VIEWED'
    UNION ALL SELECT 'ALBUM_UPLOAD_COMPLETE'
    UNION ALL SELECT 'CHEER_MESSAGE'
    UNION ALL SELECT 'LEADER_CHANGED'
    UNION ALL SELECT 'SERVICE_NOTICE'
) g
WHERE s.`category` = 'GENERAL' AND s.`enabled` = 0
  AND NOT EXISTS (
      SELECT 1 FROM `member_notification_setting` e
      WHERE e.`member_id` = s.`member_id` AND e.`category` = g.`category`
  );

-- 「일반」은 더 이상 어떤 알림도 가리키지 않는다. 켜 둔 행은 기본값(켜짐)과 같아 지워도 동작이 같다.
DELETE FROM `member_notification_setting` WHERE `category` = 'GENERAL';
