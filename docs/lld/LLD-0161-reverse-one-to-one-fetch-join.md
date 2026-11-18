# LLD-0161: 회원, 결제 목록 쿼리에서 역방향 일대일을 함께 읽어 행마다 나가던 조회 없애기

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1057 |
| 관련 ADR | - (조회는 Spring Data JPA JPQL로 쓴다는 [ADR-0044](../adr/ADR-0044-remove-querydsl.md)를 따른다) |
| 작성자 | Claude |
| 작성일 | 2026-11-16 |

## 1. 목적 / 배경

`Member.localAccount`, `Member.seniorProfile`과 `Payment.card`, `virtualAccount`, `transfer`, `easyPay`는 `@OneToOne(mappedBy = ...)` 역방향 매핑이고 fetch를 지정하지 않아 EAGER다. 외래 키가 상대 테이블에 있어 바이트코드 강화 없이는 LAZY 프록시를 만들 수 없다. 그래서 JPQL로 목록을 읽으면 Hibernate가 행마다 상대 테이블을 한 건씩 더 조회한다. 가족 구성원 목록은 보호자 1명마다 2건, 결제 목록은 결제 1건마다 5건(수단 4건과 취소 이력 1건), 매분 도는 복약 알림 대상 조회는 시니어 1명마다 2건이 더 나갔다.

먼저 시도한 전역 `default_batch_fetch_size: 100`은 역방향 일대일을 묶지 못했다(Hibernate 6.5.3, 취소 이력 컬렉션만 묶임). 측정 결과는 개인 측정 기록에 남겼고 이 문서 7절에 요약한다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api (리포지토리 JPQL만, 엔티티와 테이블은 그대로)
- 아래 다섯 조회가 역방향 일대일(결제 목록은 취소 이력 컬렉션 포함)을 `LEFT JOIN FETCH`로 함께 읽는다.

| 조회 | 쓰는 곳 | 함께 읽는 연관 |
| --- | --- | --- |
| `FamilyMembershipRepository.findAllByFamilyIdWithGuardianOrdered` | 가족 구성원 목록 | 보호자의 `localAccount`, `seniorProfile` |
| `FamilyMembershipRepository.findAllByFamilyIdWithGuardian` | 복약, 앨범, 안전구역, 사건 알림 등 가족 보호자 조회 13곳 | 보호자의 `localAccount`, `seniorProfile` |
| `SeniorProfileRepository.findAllByFamilyIdWithMember` | 가족 구성원 목록, 보호자 홈, 위치 조회 등 | 시니어의 `localAccount` (`seniorProfile`은 조회 대상 자신이라 추가 조회가 없다) |
| `MedicineScheduleRepository.findByAlarmTimeAndStatusEffectiveOn` | 복약 알림 대상(매분 3번) | 시니어의 `localAccount`, `seniorProfile` |
| `PaymentRepository.findByMemberId` | 결제 목록(`GET` 내 결제) | `card`, `virtualAccount`, `transfer`, `easyPay`, `cancellations` |

- 회귀 테스트 `ListQueryCountTest`: 다섯 조회 중 네 경로를 5행, 20행으로 실행해 SQL 실행 수가 같음을 단언한다.

### Out of scope
- 역방향 매핑 제거, 바이트코드 강화(아래 3절에서 기각).
- 다른 목록 조회의 같은 문제: 관리자 회원, 결제 페이지(`findAllForAdmin` 등), 앨범 댓글 작성자, 단건 `findById`. 단건 조회는 행 수와 무관하게 2건(결제는 4건)이 고정으로 더 나간다.
- 복약 보호자 알림이 시니어마다 가족 보호자를 따로 조회하는 구조(시니어 수만큼 `findAllByFamilyIdWithGuardian` 호출). 이번 변경으로 호출 1번이 1건이 되지만 호출 횟수는 그대로다.

## 3. 선택과 대안

| 방법 | 코드 영향 | 판단 |
| --- | --- | --- |
| 필요한 쿼리에 fetch join (선택) | 리포지토리 JPQL 4개 파일 | 엔티티, 저장, 삭제 경로가 그대로라 동작 위험이 가장 작다. 대신 목록 쿼리를 새로 만들 때마다 fetch join을 직접 챙겨야 한다. |
| 역방향 매핑 제거 후 리포지토리로 읽기 | `getLocalAccount()`, `getSeniorProfile()` 호출 약 60곳(서비스 20여 개)과 결제 응답 매퍼 3곳, 테스트 다수 | 근본 해결이지만 `cascade = ALL, orphanRemoval`에 기대는 저장(회원가입, 시니어 생성, 결제 승인 저장)과 탈퇴 마스킹(`Member.maskPersonalInfo`의 `localAccount.maskEmail()`)을 모두 다시 써야 한다. 이슈 범위에 비해 변경이 크다. |
| LAZY + 바이트코드 강화 | Gradle 플러그인과 빌드 설정, 엔티티 전체의 동작 | 모든 엔티티의 지연 로딩 방식이 바뀌어 영향 범위를 가늠하기 어렵다. |
| `default_batch_fetch_size` | 설정 한 줄 | 측정으로 역방향 일대일에 효과가 없음을 확인했다. 취소 이력 컬렉션은 fetch join이 이미 읽으므로 설정을 남길 이유가 없어 되돌렸다. |

결제 목록에서 취소 이력 컬렉션까지 fetch join하면 결과 행이 취소 건수만큼 늘지만 Hibernate 6은 루트 엔티티 중복을 자동으로 제거한다(`DISTINCT` 불필요). 결제 한 건의 취소는 몇 건을 넘지 않고 이 쿼리는 페이지 처리를 하지 않아 메모리 페이징 경고 대상이 아니다. 일대일 연관은 상대 테이블의 `member_id`, `payment_id`가 유일 키라 행을 늘리지 않는다.

## 4. 데이터 모델

변경 없음. 엔티티 매핑과 테이블이 그대로라 Flyway 버전 파일이 필요 없다.

## 5. 처리 흐름

트랜잭션 경계와 호출 순서는 그대로다. 각 조회의 SQL이 한 건으로 바뀌고, 이후 `getLocalAccount()`, `getSeniorProfile()`, `getCard()` 등은 영속성 컨텍스트에 적재된 값을 돌려준다. 연관이 없는 행(보호자의 `seniorProfile`, 시니어의 `localAccount`, 쓰지 않은 결제 수단)은 `LEFT JOIN`의 null로 채워지고 추가 조회를 하지 않는다(측정으로 확인).

## 6. 예외 / 에러 처리

변경 없음.

## 7. 인수조건 (Acceptance Criteria)

- [x] 가족 구성원 목록(보호자 목록 + 시니어 목록) SQL 실행 수가 보호자 5명, 20명에서 같다. 측정: 13/43 → 2/2
- [x] 결제 목록 SQL 실행 수가 결제 5건, 20건에서 같고 취소 이력 2건이 있는 결제가 중복되지 않는다. 측정: 26/101 → 1/1
- [x] 복약 알림 대상 SQL 실행 수가 시니어 5명, 20명에서 같다. 측정: 11/41 → 1/1
- [x] 가족 알림 대상 보호자 조회 SQL 실행 수가 보호자 5명, 20명에서 같다. 측정: 11/41 → 1/1
- [x] 기존 리포지토리 테스트(가족 정렬, 복약 유효기간)와 결제 통합 테스트가 통과한다.
- [x] `./gradlew :backend:widyu-api:test`가 통과한다(`verify.sh`, 2159건 중 실패 0, 건너뜀 50).

측정 조건: H2 in-memory(`@DataJpaTest`, `application-test.yml`), Hibernate `Statistics.getPrepareStatementCount()`, 실행 전 영속성 컨텍스트와 통계를 비운다. 표기는 5행/20행.

## 8. 영향 범위 / 마이그레이션

- 다섯 조회의 SQL에 `LEFT JOIN`이 붙는다. 반환하는 엔티티와 순서는 같다(`findByMemberId`는 원래도 정렬이 없다).
- 엔티티, 테이블, 설정, API 응답 변경 없음. 배포 순서 제약 없음.
- MySQL에서는 측정하지 않았다. 조인 대상 컬럼은 모두 유일 키 또는 외래 키라 인덱스를 탄다.

## 9. 미결정 사항 (Open Questions)

없음.

## 10. 참고

- 점검 근거: 2026-11-14 저장소 점검 db.md [높음-3]
- [LLD-0106](LLD-0106-db-redis-timeouts-and-osiv.md) (OSIV 끔: 응답 변환 전에 필요한 연관을 서비스 트랜잭션 안에서 읽어야 하는 이유)
