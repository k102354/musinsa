# 🛍️ Musinsa Payments - Point System API

대규모 트래픽 환경에서도 데이터 무결성을 보장하는 **고성능 포인트 지갑 시스템**입니다.  
포인트의 생명주기(적립, 사용, 취소, 만료)를 관리하며, **복합 우선순위 차감**, **만료 포인트 재적립(부활)**, **대용량 배치 처리** 등 고도화된 비즈니스 요구사항을 구현했습니다.

## 🛠 Tech Stack

| Category | Technology | Version | Description |
| :--- | :--- | :--- | :--- |
| **Language** | Java | **21 (LTS)** | Record, Pattern Matching 등 최신 문법 활용 |
| **Framework** | Spring Boot | **3.4.x** | Web, JPA, Batch, Validation |
| **Database** | H2 | | In-Memory (Runtime) |
| **ORM** | JPA (Hibernate) | | Auditing, Dirty Checking, Pessimistic Lock |
| **Batch** | Spring Batch | **5.x** | 대용량 데이터 커서 기반 처리 (`JpaCursorItemReader`) |
| **Utils** | **TSID** | **2.1.1** | **Twitter Snowflake 대안 (DB Indexing 성능 최적화 ID)** |
| **DevOps** | P6Spy | **1.9.1** | 쿼리 파라미터 바인딩 로그 포맷팅 (디버깅용) |
---

## 🚀 Key Features (핵심 기능)

### 1. 동시성 제어 및 데이터 무결성 (Concurrency & Integrity)
- **비관적 락(Pessimistic Lock)**: `UserPointWallet` 조회 시 `SELECT ... FOR UPDATE`를 사용하여 잔액 갱신 시 발생하는 경쟁 조건(Race Condition)을 원천 차단했습니다.
- **멱등성(Idempotency) 보장**: `User ID`와 `Ref Id` 조합에 대한 복합 인덱스(idx_user_ref)를 활용한 중복 검사 로직을 통해 네트워크 지연으로 인한 중복 적립/결제 요청을 방어합니다.

### 2. 스마트 차감 & 정교한 환불 로직
- **복합 우선순위 차감**: 포인트를 사용할 때 다음 순서로 차감하여 유저 이익을 극대화합니다.
    1. 관리자 수기 지급분 (`isManual=true`) 우선 소진
    2. 만료 임박 포인트 (`ExpireAt ASC`) 순차 소진
- **정교한 환불 정책 **:
    - **부분 취소 지원 **: 하나의 주문 건에 대해 여러 번 취소가 발생할 경우, **기 취소된 금액만큼은 건너뛰고(Skip)** 남은 잔액 범위 내에서만 정확히 환불 처리합니다.
    - **만료 포인트 재적립 (Re-issue)**: 환불 시점에 **이미 만료된 포인트**가 포함되어 있다면, 원본을 복구하는 대신 **신규 유효기간을 가진 포인트로 재적립(RESTORE)**합니다.

### 3. 대용량 만료 처리 (Batch Processing)
- **Spring Batch**를 사용하여 매일 자정(`00:00:00`) 만료된 포인트를 일괄 소멸 처리합니다.
- **Memory Efficient**: `JpaCursorItemReader`를 도입하여 대량의 데이터를 처리할 때도 OOM(Out Of Memory) 없이 안정적으로 동작합니다.
- **Traceability**: 만료 처리 시에도 `EXPIRE` 타입의 히스토리를 남겨 자금 흐름을 투명하게 관리합니다.

### 4. 보안 및 정책 관리 (Security & Policy)
- **Custom Interceptor**: `@AdminOnly` 어노테이션과 `HandlerInterceptor`를 통해 관리자 API 접근 권한(`X-ADMIN-KEY`)을 중앙에서 통제합니다.
- **동적 정책 관리**: 적립 한도, 보유 한도, 1회 최대적립 한도 등의 정책을 운영 중단 없이 실시간으로 `변경`할 수 있습니다.

### 5. Performance & Architecture Highlights
- **TSID (Time-Sorted Unique Identifier) 적용**:
    - 일반적인 `UUID`는 무작위성으로 인해 DB Insert 시 인덱스 단편화(Fragmentation)를 유발하여 성능을 저하시킵니다.
    - 이를 방지하기 위해 **시간순 정렬이 보장**되면서도 고유성을 가지는 **TSID**를 Primary Key로 채택하여 **DB 인덱싱 성능을 최적화**했습니다.
- **SQL Logging (P6Spy)**:
    - 개발 및 테스트 단계에서 실행되는 SQL의 파라미터를 명시적으로 확인하여 쿼리 효율성을 점검할 수 있도록 구성했습니다.
---

## 🏗 Domain Model

시스템은 **집계(Aggregate)**와 **원장(Ledger)**, **기록(History)**을 명확히 분리하여 설계되었습니다.

### Core Entities (`BaseTimeEntity` 상속)

| Entity | Role | Key Fields                                                                                   |
| :--- | :--- |:---------------------------------------------------------------------------------------------|
| **`UserPointWallet`** | **[지갑]** 총 잔액 관리 | • `balance`: 동시성 제어의 진입점<br>• **Lock**: 비관적 락 적용 대상                                          |
| **`PointItem`** | **[원장]** 개별 포인트 낱장 | • `remainAmount`: 잔액<br>• `expireAt`: 만료일<br>• `status`: `AVAILABLE`, `EXHAUSTED`, `EXPIRED` |
| **`PointHistory`** | **[영수증]** 불변 기록 (Master) | • `type`: `EARN`, `USE`, `RESTORE`, `EXPIRE` 등<br>• `refId`: 주문번호, 이벤트 적립번호 등 (멱등성 키)        |
| **`PointHistoryDetail`** | **[상세]** 원장 연결 (Detail) | • `pointItem`: 사용된 원장 매핑<br>• `restoredFromItemId`: 재적립 시 원본 추적                              |

---

## 🔌 API Specification

모든 응답은 `CommonResponse<T>` 표준 포맷을 따릅니다.

### 1. Point Command API (User/System)
> **Endpoint**: `/api/v1/points`

| Method | URI | Description | Request Body                                                            |
| :--- | :--- | :--- |:------------------------------------------------------------------------|
| `POST` | `/earn` | **포인트 적립**<br>정책(한도) 체크 후 적립 | `{ "userId": 1, "amount": 1000, "isManual": false, "refId" : ORD_001 }` |
| `POST` | `/use` | **포인트 사용**<br>주문 연동 및 차감 | `{ "userId": 1, "amount": 500, "orderId": "ORD-001" }`                  |
| `POST` | `/use/cancel` | **사용 취소 (환불)**<br>만료 여부에 따라 분기 처리 | `{ "userId": 1, "cancelAmount": 500, "orderId": "ORD-001" }`            |
| `POST` | `/earn/cancel` | **적립 취소**<br>미사용 건에 한해 회수 | `{ "userId": 1, "pointItemId": 105 }`                                   |

### 2. User Query API
> **Endpoint**: `/api/v1/points` (Header: `X-User-Id`)

| Method | URI | Description | Response |
| :--- | :--- | :--- | :--- |
| `GET` | `/balance` | **내 잔액 조회** | `{ "currentBalance": 1500 }` |
| `GET` | `/search` | **이용 내역 조회** | `{ "content": [ ...history ], "page": ... }` |
| `GET` | `/expiring` | **소멸 예정 포인트**<br>30일 내 만료 목록 | `[ { "amount": 100, "expireDate": "..." } ]` |

### 3. Admin API (Back-office)
> **Endpoint**: `/api/v1/admin/points` (Header: `X-ADMIN-KEY` 필수)

| Method | URI | Description | Note |
| :--- | :--- | :--- | :--- |
| `GET` | `/search` | **통합 이력 조회** | 전체 유저 대상, 거래번호 검색 |
| `GET` | `/statistics` | **기간별 통계** | 일/월별 적립 및 사용량 집계 |
| `PUT` | `/policies` | **정책 변경** | 적립 한도, 유효기간 등 설정 |

---

## ⏱️ Batch Processing (Automated Expiration)

`PointExpireJob`은 매일 자정 실행되어 만료된 포인트의 상태를 변경하고 잔액을 차감합니다.

* **Scheduler**: `PointJobScheduler` (`@Scheduled(cron = "0 0 0 * * *")`)
* **Reader**: `JpaCursorItemReader`를 사용하여 메모리 부하 없이 데이터를 스트리밍합니다.
* **Writer**:
    1. `PointItem` 상태 변경 (`AVAILABLE` -> `EXPIRED`)
    2. `UserPointWallet` 총 잔액 차감
    3. `PointHistory` (Type: `EXPIRE`) 생성

---

## 🧪 Testing Strategies

본 프로젝트는 금융 성격의 데이터를 다루므로 **데이터 정합성**과 **동시성 제어** 검증에 최우선 순위를 두었습니다.
단순 단위 테스트(`Unit Test`)뿐만 아니라, 실제 운영 환경과 유사한 **통합 테스트(`Integration Test`)** 및 **동시성 테스트(`Concurrency Test`)**를 수행합니다.

### 1. 시나리오 기반 통합 테스트 (`PointScenarioTest.java`)
복잡한 비즈니스 정책이 실제 흐름에서 의도대로 동작하는지 검증합니다.

* **복합 우선순위 차감 검증**:
    * 상황: `수기지급(Priority 1)` vs `만료임박(Priority 2)` vs `일반(Priority 3)` 포인트가 혼재된 상황.
    * 검증: 사용 시 **우선순위가 높은 순서대로 차감**되는지, 부분 취소 시 **유효기간이 넉넉한 순서대로 복구**되는지 확인.
* **Time Travel & Re-issue (만료된 포인트 환불)**:
    * 상황: 포인트 사용 후 주문 취소 시점에 원래 포인트가 만료된 경우.
    * 검증: 원본 포인트(`Item`)는 만료 상태(`EXPIRED`)로 유지되고, **새로운 유효기간을 가진 포인트(`New Item`)가 적립**되는지 검증.

### 2. 강력한 동시성 테스트 (`PointConcurrencyTest.java`)
`ExecutorService`와 `CountDownLatch`를 사용하여 멀티 스레드 환경에서의 **Race Condition**을 시뮬레이션했습니다.

* **따닥(Double Spending) 방지**:
    * 상황: 잔액 1,000원인 유저에게 동시에 3번의 1,000원 결제 요청 (`Thread=3`).
    * 결과: **1건만 성공, 2건은 실패**하며 최종 잔액은 정확히 0원 유지. (`Pessimistic Lock` 검증)
* **멱등성(Idempotency) 검증**:
    * 상황: 동일한 주문 번호(`orderId`)로 동시에 여러 번 결제 요청.
    * 결과: **단 1건만 처리**되고 나머지는 중복 요청 오류 반환.

### 3. 배치 정합성 테스트 (`PointExpireBatchJobTest.java`)
대용량 데이터 처리를 담당하는 Spring Batch Job의 로직을 검증합니다.

* **만료 처리 검증**:
    * 배치 실행 시 `expireAt`이 지난 포인트만 정확히 `EXPIRED` 상태로 변경되고, 지갑 잔액이 차감되는지 확인.
    * `AVAILABLE` 상태인 유효한 포인트는 건드리지 않는지 확인.

### 4. Mocking & Unit Test (`PointServiceTest.java`)
* **Skip Logic 검증**: 부분 취소 시 이미 환불된 금액을 건너뛰고(`Skip`), 남은 금액에 대해서만 정확히 환불 로직이 수행되는지 Mock 객체를 통해 검증.

---

### ✅ Test Execution
```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 실행 (예: 동시성 테스트)
./gradlew test --tests "com.musinsa.payment.point.application.point.PointConcurrencyTest"
```
---

## ⚙️ How to Run

### Prerequisites
* JDK 21+
* Gradle 8.x

## ⚙️ Build & Run

이 프로젝트는 **Java 21**과 **Gradle**을 기반으로 동작합니다. 로컬 환경에서 실행하기 위해 아래 설정을 진행해 주세요.

### 1. Prerequisites (환경 설정)

#### ✅ Java 21 Installation (Required)
프로젝트 실행을 위해 **JDK 21** 설치가 필수입니다.

* **Windows/Mac (직접 다운로드)**:
    * [Oracle JDK 21 다운로드](https://www.oracle.com/java/technologies/downloads/#java21)
    * 또는 [Eclipse Temurin(OpenJDK) 21 다운로드](https://adoptium.net/temurin/releases/?version=21)

* **Mac (Homebrew 사용 시)**:
    ```bash
    brew install openjdk@21
    ```

* **SDKMAN! (Linux/Mac 권장)**:
    ```bash
    sdk install java 21.0.2-temurin
    ```

#### ✅ Gradle Installation (Optional)
이 프로젝트는 **Gradle Wrapper**(`gradlew`)를 포함하고 있어 별도의 Gradle 설치 없이도 빌드가 가능합니다.
하지만, 로컬에 Gradle을 직접 설치하고 싶다면 아래 방법을 따르세요.

* **Mac (Homebrew)**:
    ```bash
    brew install gradle
    ```
* **Direct Download**: [Gradle 설치 가이드](https://gradle.org/install/)

---

### 2. Project Build

프로젝트 루트 경로에서 다음 명령어를 실행하여 의존성을 설치하고 빌드합니다.
(Gradle이 설치되지 않은 경우, 포함된 `gradlew` 스크립트를 사용하세요.)

* **Mac / Linux**:
    ```bash
    # 실행 권한 부여
    chmod +x gradlew
    
    # 빌드 (테스트 포함)
    ./gradlew clean build
    ```

* **Windows (CMD/PowerShell)**:
    ```cmd
    gradlew.bat clean build
    ```

---

### 3. Run Application

빌드가 성공적으로 완료되면, 생성된 JAR 파일을 실행합니다.

```bash
java -jar build/libs/musinsa-point-api-0.0.1.jar