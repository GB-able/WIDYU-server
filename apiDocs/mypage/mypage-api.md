# 마이페이지 API

> Base URL: `/api/v1/mypage`
> 인증: 모든 API는 `Authorization: Bearer {accessToken}` 헤더 필요

---

## 시니어 마이페이지

### MYPAGE_2001 | 시니어 내 정보 조회

🥔 특이사항

홈 화면 상단에 표시되는 프로필 이미지, 이름, 포인트를 반환합니다.

📤 Request

```
GET /api/v1/mypage/senior
```

params: 없음

body: 없음

📥 Response

```json
{
  "code": "MYPAGE_2001",
  "message": "시니어 내 정보 조회 성공",
  "data": {
    "seniorId": 1,
    "profileImage": "https://s3.amazonaws.com/widyu/profile/abc.png",
    "name": "오일남",
    "points": 500
  }
}
```

---

### MYPAGE_2002 | 가족코드 조회

🥔 특이사항

보호자가 가족에 참여할 때 사용하는 6자리 영문 대문자 + 숫자 코드를 반환합니다.
시니어 회원가입 시 자동 생성됩니다.

📤 Request

```
GET /api/v1/mypage/senior/family-code
```

params: 없음

body: 없음

📥 Response

```json
{
  "code": "MYPAGE_2002",
  "message": "가족코드 조회 성공",
  "data": {
    "familyCode": "AB12CD"
  }
}
```

---

### MYPAGE_2003 | 시니어 프로필 설정 조회

🥔 특이사항

프로필 설정 화면에 표시되는 상세 정보를 반환합니다.
`inviteCode`는 로그인용 7자리 숫자 코드입니다 (가족코드와 별개).

📤 Request

```
GET /api/v1/mypage/senior/profile
```

params: 없음

body: 없음

📥 Response

```json
{
  "code": "MYPAGE_2003",
  "message": "프로필 설정 조회 성공",
  "data": {
    "profileImage": "https://s3.amazonaws.com/widyu/profile/abc.png",
    "name": "오일남",
    "birthDate": "19680605",
    "phoneNumber": "01012345678",
    "address": "서울시 강서구",
    "detailAddress": "101호",
    "inviteCode": "1234567"
  }
}
```

---

### MYPAGE_2004 | 시니어 이름 수정

📤 Request

```
PATCH /api/v1/mypage/senior/profile/name
Content-Type: application/json
```

params: 없음

body:

```json
{
  "name": "새이름"
}
```

| 필드 | 타입 | 필수 | 제약 |
|------|------|------|------|
| name | String | Y | 최대 50자 |

📥 Response

```json
{
  "code": "MYPAGE_2004",
  "message": "이름 수정 성공",
  "data": null
}
```

---

### MYPAGE_2005 | 시니어 프로필 이미지 수정

🥔 특이사항

`multipart/form-data`로 전송합니다.
기존 이미지가 있을 경우 S3에서 자동으로 삭제됩니다.

📤 Request

```
PATCH /api/v1/mypage/senior/profile/image
Content-Type: multipart/form-data
```

params: 없음

body:

| 필드 | 타입 | 필수 |
|------|------|------|
| image | MultipartFile | Y |

📥 Response

```json
{
  "code": "MYPAGE_2005",
  "message": "프로필 이미지 수정 성공",
  "data": null
}
```

---

### MYPAGE_2006 | 시니어 전화번호 수정

📤 Request

```
PATCH /api/v1/mypage/senior/profile/phone
Content-Type: application/json
```

params: 없음

body:

```json
{
  "phoneNumber": "01099998888"
}
```

| 필드 | 타입 | 필수 | 제약 |
|------|------|------|------|
| phoneNumber | String | Y | 하이픈 없이 10~11자리 (예: 01012345678) |

📥 Response

```json
{
  "code": "MYPAGE_2006",
  "message": "전화번호 수정 성공",
  "data": null
}
```

---

### MYPAGE_2007 | 포인트 내역 조회

🥔 특이사항

현재 잔여 포인트와 적립/사용 내역을 최신순으로 반환합니다.
`type`: `EARN` (적립) / `USE` (사용)

📤 Request

```
GET /api/v1/mypage/senior/points/history
```

params: 없음

body: 없음

📥 Response

```json
{
  "code": "MYPAGE_2007",
  "message": "포인트 내역 조회 성공",
  "data": {
    "currentPoints": 490,
    "histories": [
      {
        "type": "EARN",
        "amount": 50,
        "description": "포인트 적립",
        "createdAt": "2026-04-16T14:30:00"
      },
      {
        "type": "USE",
        "amount": 50,
        "description": "앨범 해금",
        "createdAt": "2026-04-16T13:00:00"
      }
    ]
  }
}
```

---

### MYPAGE_2008 | 비상연락처 조회

🥔 특이사항

연결된 보호자 전체 목록과 대표 보호자 정보를 반환합니다.
대표 보호자가 지정되지 않은 경우 `representative`는 `null`입니다.

📤 Request

```
GET /api/v1/mypage/senior/emergency-contact
```

params: 없음

body: 없음

📥 Response

```json
{
  "code": "MYPAGE_2008",
  "message": "비상연락처 조회 성공",
  "data": {
    "representative": {
      "name": "한토마",
      "phoneNumber": "01011112222"
    },
    "familyMembers": [
      {
        "guardianId": 10,
        "name": "한토마",
        "phoneNumber": "01011112222",
        "isRepresentative": true
      },
      {
        "guardianId": 11,
        "name": "한채희",
        "phoneNumber": "01033334444",
        "isRepresentative": false
      }
    ]
  }
}
```

---

### MYPAGE_2009 | 대표 비상연락처 변경

🥔 특이사항

선택한 보호자를 대표 비상연락처로 지정합니다.
기존 대표는 자동으로 해제되고, 요청한 guardianId가 가족 구성원이 아닌 경우 403 에러가 발생합니다.

📤 Request

```
PATCH /api/v1/mypage/senior/emergency-contact/{guardianId}
```

| path param | 타입 | 설명 |
|------------|------|------|
| guardianId | Long | 대표로 지정할 보호자 ID |

body: 없음

📥 Response

```json
{
  "code": "MYPAGE_2009",
  "message": "대표 비상연락처 변경 성공",
  "data": null
}
```

---

## 보호자 마이페이지

### MYPAGE_2010 | 보호자 내 정보 조회

📤 Request

```
GET /api/v1/mypage/guardian
```

params: 없음

body: 없음

📥 Response

```json
{
  "code": "MYPAGE_2010",
  "message": "보호자 내 정보 조회 성공",
  "data": {
    "profileImage": "https://s3.amazonaws.com/widyu/profile/xyz.png",
    "name": "한토마"
  }
}
```

---

### MYPAGE_2011 | 보호자 프로필 설정 조회

🥔 특이사항

로컬 계정: `email`은 `LocalAccount.email`, `socialProviders`는 빈 배열
소셜 계정: `email`은 첫 번째 소셜 계정 이메일, `socialProviders`에 제공자 목록 (예: `["kakao"]`)
`birthDate`는 소셜 로그인 계정의 경우 `null`일 수 있습니다.

📤 Request

```
GET /api/v1/mypage/guardian/profile
```

params: 없음

body: 없음

📥 Response

```json
{
  "code": "MYPAGE_2011",
  "message": "보호자 프로필 조회 성공",
  "data": {
    "profileImage": "https://s3.amazonaws.com/widyu/profile/xyz.png",
    "name": "한토마",
    "birthDate": "19900101",
    "phoneNumber": "01011112222",
    "email": "toma@daum.net",
    "socialProviders": []
  }
}
```

---

### MYPAGE_2012 | 보호자 이름 수정

📤 Request

```
PATCH /api/v1/mypage/guardian/profile/name
Content-Type: application/json
```

body:

```json
{
  "name": "새이름"
}
```

| 필드 | 타입 | 필수 | 제약 |
|------|------|------|------|
| name | String | Y | 최대 50자 |

📥 Response

```json
{
  "code": "MYPAGE_2012",
  "message": "이름 수정 성공",
  "data": null
}
```

---

### MYPAGE_2013 | 보호자 프로필 이미지 수정

🥔 특이사항

`multipart/form-data`로 전송합니다.
기존 이미지가 있을 경우 S3에서 자동으로 삭제됩니다.

📤 Request

```
PATCH /api/v1/mypage/guardian/profile/image
Content-Type: multipart/form-data
```

| 필드 | 타입 | 필수 |
|------|------|------|
| image | MultipartFile | Y |

📥 Response

```json
{
  "code": "MYPAGE_2013",
  "message": "프로필 이미지 수정 성공",
  "data": null
}
```

---

### MYPAGE_2014 | 연결된 시니어 목록 조회

🥔 특이사항

보호자가 가족으로 연결된 시니어 전체 목록을 반환합니다.

📤 Request

```
GET /api/v1/mypage/guardian/seniors
```

params: 없음

body: 없음

📥 Response

```json
{
  "code": "MYPAGE_2014",
  "message": "연결된 시니어 목록 조회 성공",
  "data": {
    "seniors": [
      {
        "seniorId": 1,
        "profileImage": "https://s3.amazonaws.com/widyu/profile/abc.png",
        "name": "오일남"
      },
      {
        "seniorId": 2,
        "profileImage": null,
        "name": "송애순"
      }
    ]
  }
}
```

---

### MYPAGE_2015 | 시니어 프로필 조회 (보호자용)

🥔 특이사항

보호자가 연결된 시니어의 상세 프로필을 조회합니다.
가족으로 연결되지 않은 시니어 조회 시 403 에러가 발생합니다.
`inviteCode`는 시니어 로그인용 7자리 숫자 코드입니다.

📤 Request

```
GET /api/v1/mypage/guardian/seniors/{seniorId}
```

| path param | 타입 | 설명 |
|------------|------|------|
| seniorId | Long | 조회할 시니어의 Member ID |

body: 없음

📥 Response

```json
{
  "code": "MYPAGE_2015",
  "message": "시니어 프로필 조회 성공",
  "data": {
    "seniorId": 1,
    "profileImage": "https://s3.amazonaws.com/widyu/profile/abc.png",
    "name": "오일남",
    "birthDate": "19680605",
    "phoneNumber": "01012345678",
    "address": "서울시 강서구",
    "detailAddress": "101호",
    "inviteCode": "1234567"
  }
}
```

---

### MYPAGE_2022 | 시니어 가족코드 조회 (보호자용)

🥔 특이사항

연결된 시니어의 가족코드(6자리 영문 대문자 + 숫자)를 조회합니다.
다른 보호자를 가족에 초대할 때 이 코드를 공유합니다.
가족으로 연결되지 않은 시니어 조회 시 403 에러가 발생합니다.

📤 Request

```
GET /api/v1/mypage/guardian/seniors/{seniorId}/family-code
```

| path param | 타입 | 설명 |
|------------|------|------|
| seniorId | Long | 조회할 시니어의 Member ID |

body: 없음

📥 Response

```json
{
  "code": "MYPAGE_2022",
  "message": "가족코드 조회 성공",
  "data": {
    "familyCode": "AB12CD"
  }
}
```

---

### MYPAGE_2016 | 시니어 전화번호 수정

📤 Request

```
PATCH /api/v1/mypage/guardian/seniors/{seniorId}/phone
Content-Type: application/json
```

| path param | 타입 | 설명 |
|------------|------|------|
| seniorId | Long | 수정할 시니어의 Member ID |

body:

```json
{
  "phoneNumber": "01099998888"
}
```

| 필드 | 타입 | 필수 | 제약 |
|------|------|------|------|
| phoneNumber | String | Y | 하이픈 없이 10~11자리 |

📥 Response

```json
{
  "code": "MYPAGE_2016",
  "message": "시니어 전화번호 수정 성공",
  "data": null
}
```

---

### MYPAGE_2017 | 시니어 주소 수정

📤 Request

```
PATCH /api/v1/mypage/guardian/seniors/{seniorId}/address
Content-Type: application/json
```

| path param | 타입 | 설명 |
|------------|------|------|
| seniorId | Long | 수정할 시니어의 Member ID |

body:

```json
{
  "address": "서울시 강서구",
  "detailAddress": "101호"
}
```

| 필드 | 타입 | 필수 | 제약 |
|------|------|------|------|
| address | String | Y | 최대 200자 |
| detailAddress | String | N | 최대 200자 |

📥 Response

```json
{
  "code": "MYPAGE_2017",
  "message": "시니어 주소 수정 성공",
  "data": null
}
```

---

### MYPAGE_2018 | 시니어 프로필 이미지 수정

🥔 특이사항

`multipart/form-data`로 전송합니다.
기존 이미지가 있을 경우 S3에서 자동으로 삭제됩니다.

📤 Request

```
PATCH /api/v1/mypage/guardian/seniors/{seniorId}/image
Content-Type: multipart/form-data
```

| path param | 타입 | 설명 |
|------------|------|------|
| seniorId | Long | 수정할 시니어의 Member ID |

| body 필드 | 타입 | 필수 |
|-----------|------|------|
| image | MultipartFile | Y |

📥 Response

```json
{
  "code": "MYPAGE_2018",
  "message": "시니어 프로필 이미지 수정 성공",
  "data": null
}
```

---

### MYPAGE_2019 | 가족 멤버 목록 조회

🥔 특이사항

특정 시니어 가족에 연결된 보호자 전체 목록을 반환합니다.
`isCurrentUserLeader`: 요청한 보호자가 방장인지 여부
`isLeader`: 각 멤버의 방장 여부

📤 Request

```
GET /api/v1/mypage/guardian/family/{seniorId}/members
```

| path param | 타입 | 설명 |
|------------|------|------|
| seniorId | Long | 조회할 시니어의 Member ID |

body: 없음

📥 Response

```json
{
  "code": "MYPAGE_2019",
  "message": "가족 멤버 목록 조회 성공",
  "data": {
    "isCurrentUserLeader": true,
    "members": [
      {
        "guardianId": 10,
        "name": "한채희",
        "isLeader": true
      },
      {
        "guardianId": 11,
        "name": "한토마",
        "isLeader": false
      }
    ]
  }
}
```

---

### MYPAGE_2020 | 방장 변경

🥔 특이사항

현재 방장만 호출 가능합니다. 방장이 아닌 사용자가 호출하면 403 에러가 발생합니다.
지정한 guardianId가 가족 구성원이 아닌 경우 404 에러가 발생합니다.

📤 Request

```
PATCH /api/v1/mypage/guardian/family/{seniorId}/members/{guardianId}/leader
```

| path param | 타입 | 설명 |
|------------|------|------|
| seniorId | Long | 대상 시니어의 Member ID |
| guardianId | Long | 새 방장으로 지정할 보호자 ID |

body: 없음

📥 Response

```json
{
  "code": "MYPAGE_2020",
  "message": "방장 변경 성공",
  "data": null
}
```

---

### MYPAGE_2021 | 가족 멤버 삭제

🥔 특이사항

현재 방장만 호출 가능합니다. 방장이 아닌 사용자가 호출하면 403 에러가 발생합니다.
본인(방장 자신)을 삭제하려는 경우 400 에러가 발생합니다.
삭제 대상이 가족 구성원이 아닌 경우 404 에러가 발생합니다.

📤 Request

```
DELETE /api/v1/mypage/guardian/family/{seniorId}/members/{guardianId}
```

| path param | 타입 | 설명 |
|------------|------|------|
| seniorId | Long | 대상 시니어의 Member ID |
| guardianId | Long | 삭제할 보호자 ID |

body: 없음

📥 Response

```json
{
  "code": "MYPAGE_2021",
  "message": "가족 멤버 삭제 성공",
  "data": null
}
```

---

## 에러 코드

| HTTP Status | 상황 |
|-------------|------|
| 400 | 유효성 검사 실패 (이름 50자 초과, 전화번호 형식 오류 등) / 본인 삭제 시도 |
| 403 | 가족으로 연결되지 않은 시니어 접근 / 방장이 아닌 사용자의 방장 변경·멤버 삭제 시도 |
| 404 | 존재하지 않는 시니어 / 가족 구성원이 아닌 보호자 지정 |
