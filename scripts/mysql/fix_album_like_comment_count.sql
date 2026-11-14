-- #811: 동시 요청으로 어긋난 앨범 좋아요·댓글 수를 실제 행 수로 맞춘다 (LLD-0151 8절).
-- 적용 시점: 원자 UPDATE 코드가 배포된 뒤 한 번. 배포 전에 맞추면 옛 코드가 다시 어긋나게 만든다.
-- 실행 전에 album 테이블을 백업하고, 어긋난 앨범 수를 먼저 센다. 하위 질의는 잠그지 않고 읽으므로
-- 실행 중 들어온 좋아요·댓글 한두 건이 빠질 수 있다. 사용이 적은 시간에 실행한다.
--   SELECT COUNT(*) FROM album a
--   WHERE a.like_count <> (SELECT COUNT(*) FROM album_like al WHERE al.album_id = a.album_id)
--      OR a.comment_count <> (SELECT COUNT(*) FROM album_comment ac
--                             WHERE ac.album_id = a.album_id AND ac.status = 'ACTIVE');
--
-- 좋아요 취소는 행을 지우므로 album_like 행 수가 곧 좋아요 수다.
-- 댓글 삭제는 status만 DELETED로 바꾸고(답글 포함) 그만큼 댓글 수를 줄이므로 ACTIVE 댓글만 센다.
-- 상태와 무관하게 모든 앨범을 맞춘다. 삭제된 앨범도 코드가 같은 규칙으로 카운터를 바꾼다.
-- 조회 수(view_count)는 이 스크립트 범위가 아니다(LLD-0099 9절).
START TRANSACTION;

UPDATE album a
SET a.like_count = (SELECT COUNT(*) FROM album_like al WHERE al.album_id = a.album_id),
    a.comment_count = (SELECT COUNT(*) FROM album_comment ac
                       WHERE ac.album_id = a.album_id AND ac.status = 'ACTIVE');

COMMIT;
