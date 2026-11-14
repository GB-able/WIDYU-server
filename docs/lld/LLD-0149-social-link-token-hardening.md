# LLD-0149: 소셜 계정 연동 안내 응답의 개인정보 제거와 미인증 이메일 매칭 차단

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1050 |
| 관련 ADR | ADR-0002 (JWT 인증/인가) |
| 작성자 | Claude |
| 작성일 | 2026-11-14 |

## 1. 목적 / 배경

보호자가 처음 쓰는 소셜 계정으로 로그인했을 때 전화번호나 이메일이 기존 회원과 같고 그 회원에게 다른 로그인 수단이 있으면, 서버는 기존 회원 ID를 담은 소셜 임시 토큰(연동 토큰)을 준다. 이때 응답 `profile`에 기존 회원의 이름, 전화번호, 이메일이 함께 실린다. 요청자가 그 계정 주인인지 확인하기 전이므로, 남의 번호나 이메일과 같은 소셜 계정만 있으면 그 회원의 개인정보를 볼 수 있다.

이메일 매칭은 공급자가 이메일 소유를 확인했는지 보지 않는다. 카카오는 `kakao_account.is_email_verified`, Apple은 `id_token`의 `email_verified`로 인증 여부를 알려 준다. 미인증 이메일은 누구나 적을 수 있으므로, 남의 이메일을 적은 소셜 계정으로 그 회원의 연동 토큰을 받을 수 있다.

이 문서는 앱 변경 없이 서버만으로 줄일 수 있는 두 가지를 다룬다. 연동 토큰만으로 기존 계정의 토큰 쌍을 얻는 근본 문제(기존 계정 소유 확인)는 앱 화면 변경이 필요해 별도로 다룬다(9절).

## 2. 범위

### In scope
- 변경 모듈: widyu-api
- 연동 토큰 응답의 `profile`에서 이름, 전화번호, 이메일을 빈 문자열로 가리고 로그인 수단 목록(`providers`)만 남긴다.
- 카카오, Apple이 이메일을 인증하지 않았다고 하거나 값을 주지 않으면 이메일로 기존 회원을 찾지 않는다. 그 로그인은 새 회원 가입으로 처리한다.
- 같은 이메일을 쓰는 회원이 둘 이상이면 이메일로 기존 회원을 고르지 않는다(단건 조회 예외 500 방지).

### Out of scope
- 기존 계정 소유 확인(기존 계정으로 로그인한 상태에서 연동, 연동 직전 재인증). 앱 화면과 API 계약이 바뀌므로 앱과 협의한 뒤 #1050에서 이어서 한다.
- 네이버 이메일 인증 여부. 네이버 회원 정보 API에는 인증 여부 필드가 없어 기존 동작(이메일 매칭)을 유지한다.
- 전화번호 매칭. 카카오, 네이버 번호는 공급자가 확인한 번호이고, Apple 번호 보강은 LLD-0145에서 로그인 회원에 묶었다.
- 이미 저장된 소셜 계정 이메일의 인증 여부 기록. 컬럼이 없어 과거 데이터는 구분하지 않는다(9절).
- 카카오, 네이버 액세스 토큰의 발급 앱(app_id, client_id) 검증.

## 3. 인터페이스 / API

경로와 요청은 바뀌지 않는다. 연동 토큰을 줄 때의 응답 `profile` 값만 바뀐다.

```http
POST /api/v1/auth/guardians/sign-in/social?provider={kakao|naver|apple}
```

변경 후 (다른 로그인 수단이 있는 기존 회원과 매칭된 경우):

```json
{
  "code": "AUTH_2004",
  "message": "소셜 로그인 성공",
  "data": {
    "isFirst": false,
    "profile": {
      "name": "",
      "phoneNumber": "",
      "email": "",
      "providers": ["kakao", "local"]
    },
    "socialTemporaryToken": "eyJ..."
  }
}
```

변경 전에는 `name`, `phoneNumber`, `email`에 기존 회원 값이 실렸다.

- 키를 빼지 않고 빈 문자열을 쓰는 이유: 보호자 앱 `SocialProfile.fromJson`이 `name`, `email`을 null 불가 `String`으로 읽고, 연동 화면이 `phoneNumber!`를 쓴다. 응답이 `@JsonInclude(NON_NULL)`이라 null이면 키가 빠져 앱이 파싱 오류를 낸다.
- 정상 로그인(토큰 쌍 발급) 응답의 `profile`은 바뀌지 않는다.

## 4. 데이터 모델

테이블 변경 없음. DTO만 바뀐다.

- `auth/dto/response/SocialClientResponse`: `boolean emailVerified` 추가. 기존 4개, 5개 인자 `of`는 `false`(미인증)로 채운다.
- `auth/dto/response/KakaoAuthResponse.KakaoAccount`: `is_email_verified`를 `Boolean emailVerified`로 받는다.
- `auth/dto/response/UserProfile`: `providersOnly(providers)` 팩토리 추가.
- `member/repository/MemberRepository`: `findBySocialAccounts_Email`(단건)을 `findDistinctTop2BySocialAccounts_Email`(최대 2건)로 바꾼다.

## 5. 처리 흐름

`SocialLoginService.socialLogin`의 트랜잭션 안이다. 바뀐 단계만 적는다.

1. 공급자별 전략이 `SocialClientResponse.emailVerified`를 채운다.
   - 카카오: `kakao_account.is_email_verified`가 `true`일 때만 `true`.
   - Apple: `id_token`의 `email_verified`가 `true`일 때만 `true`. 문자열 `"true"`와 불리언 `true`를 모두 받는다(Jackson 기본 변환).
   - 네이버: 항상 `true`(기존 동작 유지).
   - `enrichWithRefreshToken`은 값을 그대로 넘긴다.
2. 처음 쓰는 소셜 계정이면 `findExistingMemberByUserInfo`가 기존 회원을 찾는다.
   1. 전화번호가 있으면 기존과 같이 번호로 찾는다(LLD-0116).
   2. 이메일이 없거나 `emailVerified=false`이면 찾지 않는다.
   3. `findDistinctTop2BySocialAccounts_Email`로 최대 2명을 읽는다. 2명이면 고르지 않고 경고 로그만 남긴다. 1명이면 그 회원을 쓴다.
3. 찾은 회원에게 다른 로그인 수단이 있으면 연동 토큰을 만들고 `profile`을 `UserProfile.providersOnly(로그인 수단 목록)`로 채운다.
4. 기존 회원을 못 찾으면 기존과 같이 새 보호자 회원을 만들고 토큰 쌍을 준다. `social_account.email`과 `member.phone_number`에는 DB 유일 제약이 없어 같은 이메일로도 저장된다.

null 처리 근거: 카카오는 이메일 제공에 동의하면 `is_email_verified`를 함께 주고, Apple은 이메일을 줄 때 `email_verified`를 함께 준다. 값이 없는 응답은 정상 경로가 아니므로, 기존 회원 연결(계정 탈취 위험)보다 새 회원 가입(중복 계정 불편)이 안전하다. 공급자가 필드를 빼면 기존 회원과 연결되지 않고 새 계정이 생기므로 증상이 드러나고, 되돌리기도 쉽다.

## 6. 예외 / 에러 처리

새 에러 코드는 없다. 같은 이메일 회원이 여럿일 때 이전에는 `IncorrectResultSizeDataAccessException`(500)이 날 수 있었고, 이제는 이메일 매칭을 건너뛴다.

## 7. 인수조건 (Acceptance Criteria)

- [x] AC1: 다른 로그인 수단이 있는 기존 회원과 매칭돼 연동 토큰을 주면 `profile`의 이름, 전화번호, 이메일은 빈 문자열이고 `providers`만 실린다.
- [x] AC2: 카카오 `is_email_verified`가 `false`이거나 없으면 `emailVerified=false`다. `true`일 때만 `true`다.
- [x] AC3: Apple `email_verified`가 문자열 `"true"`, 불리언 `true`이면 `true`, `"false"`, `false`, 없음이면 `false`다.
- [x] AC4: `emailVerified=false`이면 이메일로 기존 회원을 조회하지 않고 새 회원을 만들며 연동 토큰을 주지 않는다.
- [x] AC5: `emailVerified=true`이고 이메일이 다른 로그인 수단이 있는 기존 회원 한 명과 같으면 기존처럼 연동 토큰을 준다.
- [x] AC6: 같은 이메일의 회원이 둘 이상이면 어느 회원에도 연동 토큰을 주지 않고 새 회원을 만든다.
- [x] AC7: Swagger의 연동 토큰 응답 예시가 빈 개인정보와 `providers`를 보여 준다.
- [ ] AC8: `./gradlew :backend:widyu-api:test`가 통과한다.

## 8. 영향 범위 / 마이그레이션

- 스키마 변경 없음.
- 보호자 앱: 연동 안내 화면(`IntegrateView`)의 이름, 연락처가 빈 값으로 보인다. 소셜 로그인 수단 목록은 그대로 보인다. 앱 파싱은 깨지지 않는다(3절). 화면 문구 정리는 소유 확인 작업에서 앱과 함께 한다.
- 미인증 이메일을 주는 카카오 계정은 이전에는 같은 이메일의 기존 회원에 연결될 수 있었고, 이제는 새 회원이 된다.

## 9. 미결정 사항 (Open Questions)

- [ ] 기존 계정 소유 확인 방식(기존 계정 로그인 후 연동, 연동 직전 재인증 등)과 앱 화면 범위. 앱과 협의한다. 이 작업 전까지 연동 토큰만으로 기존 계정 토큰 쌍을 얻는 경로는 남는다.
- [ ] 기본값으로 정함, 확인 필요: 인증 여부 값이 없으면 미인증으로 본다(5절 근거).
- [ ] 기본값으로 정함, 확인 필요: 같은 이메일 회원이 둘 이상이면 예외 대신 이메일 매칭을 건너뛰고 새 회원을 만든다. 전화번호 중복은 `PHONE_NUMBER_SHARED_BY_MEMBERS`로 거부하지만, 이메일 중복은 미인증 이메일 가입으로 남이 만들 수 있어 거부하면 원래 주인의 로그인을 막는다.
- [ ] 미인증 이메일로 만든 회원이나 연동으로 붙은 미인증 이메일도 이후 인증된 이메일 로그인의 매칭 대상이 된다. 인증 여부를 저장하지 않아 서버만으로는 구분할 수 없다. 소유 확인이 들어가면 남의 계정에 연동할 수 없으므로 함께 해소된다.

## 10. 참고

- LLD-0094: Apple 로그인 신뢰 경계
- LLD-0116: 전화번호 중복 회원 처리
- LLD-0145: Apple 회원 전화번호 보강을 Apple 로그인 결과와 결합
- 카카오 사용자 정보 `kakao_account.is_email_verified`, Apple `id_token` `email_verified`
