# LLD-0120: 시니어 주소 요청에 위도·경도 추가

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #951 |
| 관련 ADR | - |
| 작성자 | Claude |
| 작성일 | 2026-11-02 |

## 1. 목적 / 배경

시니어 주소를 받는 세 API는 주소 문자열만 받는다. 서버는 그 주소를 카카오 좌표 변환(`GeocodingService.geocode`)으로 바꿔 시니어의 집 안심구역(`ParentLocation`, `LocationType.HOME`) 좌표로 저장한다.

- 좌표 변환이 주소를 찾지 못하거나 호출에 실패하면 400으로 가입·추가·수정 전체가 실패한다.
- 앱은 주소 검색에서 이미 좌표를 받는데, 서버가 같은 좌표를 다시 구한다.

앱이 위도·경도를 함께 보내면 그 값을 쓴다.

| API | 요청 DTO | 처리 |
| --- | --- | --- |
| 부모님 추가 `POST /api/v1/mypage/guardian/seniors` | `SeniorSignUpRequest` | `GuardianMyPageService.addSenior` |
| 시니어 일괄 회원가입 `POST /api/v1/auth/seniors/sign-up` | `List<SeniorSignUpRequest>` | `SeniorAuthService.seniorSignUpBulk` |
| 시니어 주소 수정 `PATCH /api/v1/mypage/guardian/seniors/{memberId}/address` | `UpdateSeniorAddressRequest` | `GuardianMyPageService.updateSeniorAddress` |

## 2. 범위

### In scope
- 변경 모듈: widyu-api
- 두 요청 DTO에 `latitude`, `longitude`(선택) 추가와 검증.
- 좌표가 있으면 좌표 변환 없이 집 안심구역 좌표로 저장.

### Out of scope
- DB 변경. 좌표는 이미 있는 집 안심구역에 저장한다. `SeniorProfile`에 좌표 컬럼을 더하면 같은 좌표를 두 곳에 두게 된다.
- 좌표 필수화. 현재 앱은 좌표를 보내지 않는다.
- 보낸 좌표와 주소가 실제로 일치하는지 검증.

## 3. 인터페이스 / API

두 DTO 끝에 필드 두 개를 더한다.

| 필드 | 타입 | 검증 |
| --- | --- | --- |
| `latitude` | Double, 선택 | -90 이상 90 이하 |
| `longitude` | Double, 선택 | -180 이상 180 이하 |

- 둘 다 보내거나 둘 다 생략해야 한다(`@AssertTrue isCoordinatePaired`). 하나만 보내면 400 "위도와 경도는 함께 보내거나 둘 다 생략해야 합니다."
- 범위 검증 메시지는 기존 좌표 요청(`AddressBookmarkCreateRequest`, `ParentLocationCreateRequest`)과 같다.
- 일괄 가입은 `@Valid List<…>`라 Spring 6.1 메서드 검증이 요소마다 돌고, `HandlerMethodValidationException`을 `GlobalExceptionHandler`가 400으로 응답한다. `SeniorSignUpBulkValidationTest`(MockMvc)로 확인한다.

## 4. 데이터 모델

변경 없음.

## 5. 처리 흐름

두 서비스의 private 메서드 `resolveCoordinates(address, latitude, longitude)`:
- `latitude`가 있으면(검증이 짝을 보장) `GeocodingResponse.of(latitude, longitude)`.
- 없으면 기존처럼 `geocodingService.geocode(address)`.

집 안심구역 저장·갱신 로직은 그대로 두고 좌표 출처만 바꾼다. 주소 수정에서 좌표를 보내지 않으면 새 주소로 좌표를 다시 구하므로 옛 좌표가 남지 않는다.

`GeocodingService`에 메서드를 두지 않은 이유: 서비스 테스트가 `GeocodingService`를 mock하므로 새 메서드는 mock에서 null을 돌려 기존 테스트가 깨진다. 네 줄짜리 메서드를 두 서비스에 둔다.

## 6. 예외 / 에러 처리

| 조건 | HTTP | 메시지 |
| --- | --- | --- |
| 위도·경도 중 하나만 보냄 | 400 | 위도와 경도는 함께 보내거나 둘 다 생략해야 합니다. |
| 위도 범위 밖 | 400 | 위도는 -90 이상 90 이하여야 합니다. |
| 경도 범위 밖 | 400 | 경도는 -180 이상 180 이하여야 합니다. |
| 좌표 없음 + 좌표 변환 실패 | 400 | 기존과 같음 |

## 7. 인수조건 (Acceptance Criteria)

- [x] 좌표와 함께 부모님을 추가하면 좌표 변환 없이 보낸 좌표로 집 안심구역을 만든다.
- [x] 좌표와 함께 주소를 수정하면 좌표 변환 없이 보낸 좌표로 집 안심구역을 갱신한다.
- [x] 일괄 가입에서 좌표를 보낸 시니어만 좌표 변환을 건너뛰고, 보내지 않은 시니어는 주소로 좌표를 구한다.
- [x] 좌표를 보내지 않으면 기존 동작과 같다(기존 테스트 통과).
- [x] 위도·경도 중 하나만 보내거나 범위를 벗어나면 검증 오류가 난다.
- [x] `./gradlew compileJava`, mypage/auth 테스트 통과.

## 8. 영향 범위 / 마이그레이션

DB 변경 없음. 새 필드는 선택이라 지금 앱은 그대로 동작한다.

## 9. 미결정 사항 (Open Questions)

- 없음.

## 10. 참고
- `GuardianMyPageService.saveHomeParentLocation`, `SeniorAuthService.saveHomeParentLocations`
- `AddressBookmarkCreateRequest`(좌표 범위 검증 선례)
