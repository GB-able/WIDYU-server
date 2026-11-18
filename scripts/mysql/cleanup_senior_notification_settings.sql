-- LLD-0095 §8 / #1105. 선택 정리 스크립트이며 자동 실행하지 않는다.
-- 시니어 푸시 설정은 #1105 배포부터 켜짐 고정이라 시니어의 설정 행은 조회, 발송 판정 어디에도 쓰이지 않는다.
-- 지우지 않아도 동작은 같다. 지우면 나중에 시니어 스위치를 되살릴 때 예전 OFF가 다시 살아나지 않는다.
-- 실행 시점: #1105 서버 배포 뒤(옛 서버가 행을 다시 읽지 않을 때). 실행 전 백업한다.
-- 확인: SELECT s.category, s.enabled, COUNT(*)
--       FROM member_notification_setting s JOIN member m ON m.id = s.member_id
--       WHERE m.type = 'SENIOR' GROUP BY s.category, s.enabled;
DELETE FROM member_notification_setting
WHERE member_id IN (SELECT id FROM member WHERE type = 'SENIOR');
-- 확인: 위 조회 결과 0행. 보호자 행 수는 실행 전과 같다.
-- 재실행해도 추가 변경이 없다.
