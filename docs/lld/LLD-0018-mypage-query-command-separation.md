# LLD-0018: 마이페이지 조회·명령 책임 분리

| 항목 | 값 |
| --- | --- |
| 상태 | Approved |
| Issue | #441 |
| 관련 ARCH | ARCH-032, ARCH-011 |
| 관련 ADR | ADR-0002 |
| 작성일 | 2026-07-25 |

## 1. 목적

마이페이지의 조회와 변경 유스케이스를 분리하고, 보호자 가족 접근 및 방장 권한 검증을 공통 정책 서비스로 모은다. 기존 HTTP API, 응답 형식, 오류 의미는 변경하지 않는다.

## 2. 구조

- `GuardianMyPageQueryService`: 보호자 정보, 연결 시니어, 시니어 프로필, 가족 코드·구성원 조회를 담당한다.
- `SeniorMyPageQueryService`: 시니어 정보, 프로필, 포인트 내역, 비상연락처 조회를 담당한다.
- `GuardianMyPageCommandService`: 보호자·시니어 프로필 변경, 시니어 추가, 전화번호 인증, 가족 구성원·방장 변경을 담당한다.
- `SeniorMyPageCommandService`: 시니어 자신의 프로필·전화번호 변경과 대표 연락처 변경을 담당한다.
- `FamilyAccessService`: 보호자의 가족 소속, 대상 시니어 접근, 방장 권한 확인에 필요한 공통 정책을 제공한다.
- Controller는 요청 성격에 맞는 Query 또는 Command Service만 호출한다.

`MyPageProfileService`의 정적 유틸리티는 Service 책임으로 이동한다. 프로필 이미지 교체 시 새 파일 업로드 후 Member URL 갱신, 기존 파일 삭제 순서는 유지한다.

## 3. 유스케이스 규칙

1. 보호자의 대상 시니어 조회·변경은 가족 연결을 먼저 확인한다.
2. 시니어의 전화번호·이름·주소·이미지·초대 코드 변경과 가족 구성원 삭제·방장 변경은 방장만 수행한다.
3. 가족 구성원 삭제 시 본인 삭제 금지, 다른 가족의 시니어 삭제 금지, 마지막 시니어 삭제 금지, 포인트 이력 삭제 규칙을 유지한다.
4. 시니어 주소 변경은 시니어 프로필 주소와 HOME `ParentLocation`을 같은 트랜잭션에서 함께 갱신하거나 새로 생성한다.
5. 마이페이지 시니어 추가는 기존 가족에 시니어를 생성·연결하고 HOME `ParentLocation`을 만든다. auth signup과의 정책 통합은 이 범위에 포함하지 않는다.

## 4. 트랜잭션 및 외부 연동

- Query Service는 `@Transactional(readOnly = true)`를 사용한다.
- Command Service는 `@Transactional`을 사용한다.
- 주소 생성·변경의 geocoding, 프로필 이미지의 S3 업로드·삭제는 기존 호출 순서와 오류 전파를 유지한다.

## 5. 인수조건

- [ ] 기존 Guardian/Senior 마이페이지 API의 경로·응답·오류 코드를 바꾸지 않는다.
- [ ] 조회 메서드가 Query Service로, 변경 메서드가 Command Service로 분리된다.
- [ ] 대상 시니어 가족 접근과 방장 권한 검증이 공통 `FamilyAccessService`로 이동한다.
- [ ] 시니어 주소 변경 시 SeniorProfile과 HOME ParentLocation이 함께 갱신되는 회귀 테스트가 통과한다.
- [ ] 권한 없는 보호자의 시니어 조회·변경, 비방장의 가족 변경, 마지막 시니어 삭제 거부를 테스트로 보장한다.
- [ ] `./gradlew :backend:widyu-api:test --tests 'com.widyu.mypage.application.*' --tests 'com.widyu.member.application.FamilyAccessServiceTest'`가 통과한다.

## 6. 미결정 사항 (Open Questions)

없음. 마이페이지 시니어 추가는 기존 가족에 시니어를 생성·연결하는 현재 정책을 유지하며, auth signup과의 정책 통합은 별도 작업으로 다룬다.
