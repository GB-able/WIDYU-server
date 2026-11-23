# LLD-0186: 어르신 탈퇴 요청을 처리 완료하면 탈퇴를 실행하고 위치 기록을 바로 지운다

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1157 |
| 관련 ADR | ADR-0043 결정 8(어르신 탈퇴 때 위치 즉시 삭제), 결정 4(실증 참가자 연구 자료 제외) |
| 관련 LLD | LLD-0137(탈퇴 요청과 처리 기록, 9절 2번을 닫는다), LLD-0059(탈퇴 이벤트), LLD-0087(비상 수신자 스냅샷) |
| 작성자 | Claude |
| 작성일 | 2026-11-23 |

## 1. 목적 / 배경

어르신은 앱에서 탈퇴를 요청하고 운영자가 관리자 API로 「처리 완료」를 누른다(LLD-0137). 지금 처리 완료는 기록만 바꾼다. 어르신 계정은 이름과 번호가 남은 `ACTIVE`이고, 가족 보호자는 계속 그 어르신의 위치와 건강 정보를 본다. 위치 원본(`location_fix`)과 위치 열람 기록(`location_access_log`)도 남는다.

개인정보 처리방침은 「어르신이 탈퇴하면 위치정보와 위치 열람 기록은 바로 지운다」고 약속한다(노션 「보관 기간이 지난 자료 지우기와 탈퇴 처리를 처리방침대로 맞추기」 2-3). 2026-11-23 사용자가 이 즉시 삭제를 켜서 구현하기로 정했다(ADR-0043 결정 8). 법무 확인은 아직이다.

## 2. 범위

### In scope
- 변경 모듈: widyu-domain(`SeniorProfile.leaveFamily`), widyu-api(`MemberWithdrawService.withdrawSenior`, `MemberWithdrawalRequestService.complete`, 삭제 쿼리 3개)
- 처리 완료와 같은 트랜잭션에서 어르신 탈퇴를 실행한다.
- 그 어르신의 위치 열람 기록과 위치 원본을 지운다.
- 보호자 탈퇴와 어르신 탈퇴가 계정 정리 단계를 함께 쓴다.

### Out of scope
- 탈퇴 30일 뒤 개인 자료 삭제와 앨범 삭제(노션 2-2, 2-4). 별도 LLD.
- 보관 기간 자동 삭제(LLD-0136 2, 3단계).
- 어르신 본인 즉시 탈퇴 API. 요청과 운영자 처리 방식(LLD-0137)을 유지한다.
- 30일 안 복구(ADR-0043 후속 6). 지금처럼 이름과 번호를 즉시 가리므로 복구하지 않는다.

## 3. 인터페이스 / API

새 API는 없다. 기존 `POST /api/v1/admin/members/withdrawal-requests/{requestId}/complete`의 동작만 바뀐다.

- 응답 형식과 오류 코드는 그대로다(닫힌 요청 409, 없는 요청 404).
- 요청한 회원이 어르신이 아니면 403(`FORBIDDEN`). 요청 API가 어르신만 받으므로 정상 경로에서는 생기지 않는다.

## 4. 데이터 모델

스키마 변경은 없다.

- `SeniorProfile.leaveFamily()`: `family`를 null로 둔다. 방장 보호자가 탈퇴해 가족이 해체될 때(`clearFamilyByFamilyId`)와 같은 상태다.
- `LocationAccessLogRepository.deleteBySeniorMemberId`, `LocationFixRepository.deleteByMemberId`: JPQL 벌크 DELETE. 각각 인덱스 `(senior_member_id, accessed_at)`, `(member_id, ts_ms)`를 탄다.
- `StudyParticipationRepository.existsByMemberIdAndDeletionProcessedAtIsNull`: 연구 자료 수동 삭제를 마치지 않은 참가자인가. `RetentionTarget`의 참가자 조건과 같다.

## 5. 처리 흐름

### 5.1 처리 완료 (`MemberWithdrawalRequestService.complete`, 한 트랜잭션)

1. 요청 행을 잠그고 `PROCESSED`로 바꾼다(기존).
2. `MemberWithdrawService.withdrawSenior(memberId)`를 부른다.
3. 감사 로그를 남긴다(기존). 2나 3이 실패하면 요청 처리, 계정 변경, 삭제가 모두 롤백된다.

### 5.2 어르신 탈퇴 (`MemberWithdrawService.withdrawSenior`)

1. 회원 행을 `PESSIMISTIC_WRITE`로 읽는다. 어르신이 아니면 403. 이미 `withdrawn_at`이 있으면 아무것도 하지 않는다.
2. **가족에서 뺀다**: `senior_profile.family_id`를 null로 둔다. 가족 확인(`FamilyAccessService`)이 시니어 프로필의 가족으로 관계를 보므로 가족 보호자는 이 어르신의 위치, 심박, 목표를 더 볼 수 없다. 커밋 뒤 이 어르신의 비상 수신자 스냅샷 키를 지운다(`evictAfterCommit`).
3. **위치 열람 기록을 지운다.**
4. **위치 원본을 지운다.** 단, 연구 자료 수동 삭제를 마치지 않은 실증 참가자면 남긴다. 위치 원본은 ADR-0043 결정 4의 연구 자료이고, 참가자 탈퇴 때 위치를 즉시 지울지는 후속 2번에서 정해지지 않았다. 열람 기록은 연구 자료가 아니므로 참가자도 지운다.
5. **계정 정리**(보호자 탈퇴와 같은 `eraseAccount`): 복약 인증 사진 삭제와 S3 삭제 작업, 리프레시 토큰 삭제, 소셜 연동 해제 작업 저장, 이름과 번호 가림, `INACTIVE`와 `withdrawn_at`, 프로필 사진 삭제 작업, 커밋 뒤 `MemberWithdrawnEvent`(Redis 실시간 위치 삭제, 소셜 연동 해제).

로그에는 회원 ID와 지운 행 수만 남긴다. 좌표는 남기지 않는다(LLD-0029).

### 5.3 가족 처리의 선택

- **채택: 가족 연결만 끊는다(`family_id = null`).** 기존 코드에 이미 있는 상태라(방장 보호자 탈퇴) 조회 코드가 null 가족을 처리한다. 시니어 프로필 행은 남아 포인트 이력(`point_history.senior_profile_id` FK)과 결제 기록을 깨지 않는다.
- 기각: 보호자가 어르신을 가족에서 빼는 경로처럼 시니어 프로필 행을 지운다(`deleteSeniorFromFamily`). 포인트 이력까지 지워야 하고, 30일 뒤 삭제 작업이 정리할 자료를 앞당겨 지운다.
- 가족의 마지막 어르신이 탈퇴해도 가족방은 남는다. 보호자는 새 어르신을 등록할 수 있다. 가족방 정리는 ADR-0043 후속 7번으로 남긴다.

## 6. 예외 / 에러 처리

- 삭제나 계정 정리 중 예외는 트랜잭션 전체를 롤백한다. 운영자는 같은 요청을 다시 처리 완료할 수 있다(요청이 `REQUESTED`로 남는다).
- 커밋 뒤 작업(스냅샷 삭제, Redis 위치 삭제, 소셜 연동 해제)의 실패는 기존 경로의 재시도와 로그를 따른다.

## 7. 인수조건 (Acceptance Criteria)

- [x] 처리 완료하면 어르신이 `INACTIVE`, `withdrawn_at`이 채워지고 이름이 「탈퇴회원」, 번호가 비어 있다.
- [x] 처리 완료하면 어르신의 가족 연결이 끊기고 커밋 뒤 그 어르신의 비상 수신자 스냅샷이 지워진다.
- [x] 그 어르신의 위치 열람 기록과 위치 원본이 지워지고 다른 어르신의 행은 남는다.
- [x] 연구 자료 삭제를 마치지 않은 실증 참가자면 위치 원본은 남고 열람 기록은 지워진다.
- [x] 보호자 ID로 어르신 탈퇴를 부르면 403이고 아무것도 지우지 않는다.
- [x] 이미 탈퇴한 어르신이면 아무것도 바꾸지 않는다.
- [x] 취소는 탈퇴를 실행하지 않는다.
- [x] 보호자 탈퇴 테스트가 그대로 통과한다.

## 8. 영향 범위 / 마이그레이션

- 스키마 변경 없음.
- 운영자 처리 절차: 처리 완료 버튼이 곧 탈퇴 실행이다. 누르기 전에 본인 확인을 마쳐야 한다. 되돌릴 수 없다.
- 기존에 `PROCESSED`로 닫힌 요청의 어르신은 탈퇴가 실행되지 않은 채 남아 있다. 개발 서버에만 있고 운영 서버는 아직 없다. 필요하면 운영자가 새 요청을 받아 처리한다.
- `backend/CLAUDE.md` auth, location.access 절을 갱신한다. LLD-0137 9절 2번을 이 문서로 닫는다.

## 9. 미결정 사항 (Open Questions)

- [ ] 1. 실증 참가자 어르신의 위치 원본을 탈퇴 때 지울지(ADR-0043 후속 2). 지금은 남긴다.
- [ ] 2. 위치정보법의 열람 기록 6개월 이상 보존과 탈퇴 즉시 삭제의 관계(ADR-0043 후속 1). 사용자 결정으로 즉시 지우고, 법무 확인을 기다린다.
- [ ] 3. 가족의 마지막 어르신이 탈퇴했을 때 가족방 처리(ADR-0043 후속 7). 지금은 남긴다.

## 10. 참고

- `MemberWithdrawService`, `MemberWithdrawalRequestService`, `SeniorProfile`
- 테스트: `MemberWithdrawServiceTest`, `MemberWithdrawalRequestServiceTest`, `SeniorWithdrawalLocationDeletionTest`
