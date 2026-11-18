-- #1103 / LLD-0167 8절. 응원(X02), 하트(X01) 메시지 원문이 남은 옛 알림센터 행과 재시도 큐 행을 정리한다.
-- 적용 시점: 이 변경이 배포된 뒤 한 번(개발 DB). 운영 구축 때 이전 데이터를 옮겨 오면 그 DB에도 한 번.
-- 배포 전에 실행하면 옛 코드가 다시 원문 행을 만든다. 실행 전에 두 테이블을 백업하고 대상 수를 센다.
--
-- 실행 절차(발송 작업자를 멈춘 채 실행한다):
--   발송 작업자는 재시도 큐 행을 claim할 때 본문을 메모리에 복사해 보낸다. SQL이 DB 행만 바꾸면 이미 claim된 행은
--   SQL 뒤에도 원문으로 나갈 수 있다. 그래서 작업자를 멈추고, 진행 중인 발송이 끝난 것을 확인한 뒤 실행한다.
--   1. 이 변경이 배포된 것을 확인한다.
--   2. API 컨테이너를 멈춘다(발송 작업자 정지). 개발 서버: docker stop widyu-api-dev
--   3. claim된 채 남은 옛 행이 없는지 확인한다. 0이 아니면 lease_until이 지날 때까지 기다린 뒤 다시 센다.
--        SELECT COUNT(*) FROM fcm_outbox WHERE notification_type IS NULL AND state = 'CLAIMED'
--          AND lease_until > NOW()
--          AND (data_type IN ('CHEER_MESSAGE_RECEIVED', 'HEART_MESSAGE_RECEIVED')
--               OR title LIKE '%님에게 응원메시지를 보냈어요.'
--               OR (fcm_category = 'HEART_MESSAGE' AND title LIKE '%님이 메시지를 보냈어요.'));
--   4. 백업, 대상 수 확인, 이 스크립트 실행.
--   5. 아래 대상 조회를 다시 돌려 원문이 남은 행이 0인지 확인하고 API 컨테이너를 다시 켠다. docker start widyu-api-dev
--   남는 위험: 배포로 새 코드가 뜬 뒤 2번 전까지 재시도 중이던 옛 행이 원문으로 나갈 수 있다. 개발 서버의 시험 계정
--   자료에만 해당한다. 운영 DB는 새 코드로 시작하므로 옛 행이 없다(이전 데이터를 옮겨 오면 같은 절차를 따른다).
--   SELECT COUNT(*) FROM fcm_notification
--   WHERE type IS NULL AND expires_at IS NULL
--     AND (title LIKE '%님에게 응원메시지를 보냈어요.'
--          OR (fcm_category = 'HEART_MESSAGE' AND title LIKE '%님이 메시지를 보냈어요.'));
--
-- 고르는 기준: 새 코드는 type을 채우므로 type IS NULL 행은 옛 코드가 만든 행이다. 옛 제목 형식으로
-- 응원(「A님이 B님에게 응원메시지를 보냈어요.」, 카테고리 ETC 또는 HEART_MESSAGE)과
-- 하트(「A님이 메시지를 보냈어요.」, HEART_MESSAGE)를 고른다. 위급 알림 등 다른 HEART_MESSAGE 행은 제목이 달라 빠진다.
-- 다시 실행해도 결과가 같다(센터는 expires_at IS NULL, 재시도 큐는 이미 고정 문구인 행을 건너뛴다).
--
-- 알림센터(fcm_notification)에서 바꾸는 것
--   body: 공백이 아닌 제어 문자를 지우고, 줄바꿈, 탭, 연속 공백을 공백 하나로 바꾸고, 앞뒤 공백을 지운 뒤
--         40자를 넘으면 앞 39자 + '…'.
--   expires_at: created_at + 90일(ROUTINE_90D). 이미 90일이 지난 행은 바로 목록에서 빠지고
--               보관 기간 정리(LLD-0136, expires_at < now) 대상이 된다.
--   retention_policy_version: 'v1'.
--   type은 채우지 않는다. 타입이 있는 행은 v2 응답 eventId로 event_id 칸을 쓰는데 옛 행은 비어 있을 수 있다.
--   제목은 원문을 담지 않으므로 두고, 목록에는 계속 LEGACY로 나온다.
--
-- 한계: MySQL은 그래핌(사람이 보는 글자)을 모르고 NFC 정규화도 없다. CHAR_LENGTH와 LEFT는 코드포인트 기준이다.
--   - ZWJ 조합 이모지(가족 이모지는 코드포인트 5개), 피부색 이모지, 국기, 분해형 한글은 코드포인트가 여러 개라
--     앱 규칙(그래핌 40)보다 일찍 잘린다. 화면에서 40글자를 넘지는 않는다.
--   - 자르는 자리가 조합 중간이면 앞부분만 남는다(가족 이모지가 사람 하나로 보이거나 피부색이 떨어진다).
--     끝에 남은 ZWJ(U+200D)와 변형 선택자(U+FE0F)는 지운다.
--   - 분해형 한글을 음절로 합치지 않는다.
--
-- 재시도 큐(fcm_outbox)에서 바꾸는 것: 옛 행 body(원문)를 잠금화면 고정 문구로 바꾼다. 상태와 상관없이 바꾸므로
-- 아직 보내지 않은 행도 원문 없이 나간다. 30일 뒤 보관 기간 정리로 지워지는 행이다.
START TRANSACTION;

UPDATE fcm_notification
SET body = TRIM(REGEXP_REPLACE(REGEXP_REPLACE(body, '[\\p{Cc}&&[^\\s]]', ''), '\\s+', ' '))
WHERE type IS NULL AND expires_at IS NULL AND body IS NOT NULL
  AND (title LIKE '%님에게 응원메시지를 보냈어요.'
       OR (fcm_category = 'HEART_MESSAGE' AND title LIKE '%님이 메시지를 보냈어요.'));

UPDATE fcm_notification
SET body = CASE
        WHEN CHAR_LENGTH(body) > 40
            THEN CONCAT(REGEXP_REPLACE(LEFT(body, 39), '[\\x{200D}\\x{FE0F}\\s]+$', ''), '…')
        ELSE body
    END,
    expires_at = created_at + INTERVAL 90 DAY,
    retention_policy_version = 'v1'
WHERE type IS NULL AND expires_at IS NULL
  AND (title LIKE '%님에게 응원메시지를 보냈어요.'
       OR (fcm_category = 'HEART_MESSAGE' AND title LIKE '%님이 메시지를 보냈어요.'));

UPDATE fcm_outbox
SET body = CASE
        WHEN data_type = 'CHEER_MESSAGE_RECEIVED' OR title LIKE '%님에게 응원메시지를 보냈어요.'
            THEN '앱에서 응원메시지를 확인해주세요.'
        ELSE '앱에서 메시지를 확인해주세요.'
    END
WHERE notification_type IS NULL
  AND (data_type IN ('CHEER_MESSAGE_RECEIVED', 'HEART_MESSAGE_RECEIVED')
       OR title LIKE '%님에게 응원메시지를 보냈어요.'
       OR (fcm_category = 'HEART_MESSAGE' AND title LIKE '%님이 메시지를 보냈어요.'))
  AND (body IS NULL OR body NOT IN ('앱에서 응원메시지를 확인해주세요.', '앱에서 메시지를 확인해주세요.'));

COMMIT;
