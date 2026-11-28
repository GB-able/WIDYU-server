-- 어르신 탈퇴가 운영자 처리에서 본인 즉시 탈퇴로 바뀌어 요청 기록 표를 지운다(LLD-0186).
-- 앱에 탈퇴 메뉴가 없어 운영 요청 행이 생긴 적이 없다. 탈퇴 사실은 member.withdrawn_at이 남긴다.
DROP TABLE IF EXISTS member_withdrawal_request;
