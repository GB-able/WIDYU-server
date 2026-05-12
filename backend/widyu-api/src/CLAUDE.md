# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

WIDYU is a Spring Boot family photo/video sharing platform that enables secure content sharing between parents and guardians (caregivers, grandparents, family members). It features a point-based premium content system, real-time location tracking via WebSocket, and comprehensive social interaction capabilities.

**Tech Stack**: Java 21, Spring Boot 3.3.5, MySQL, Redis, WebSocket (STOMP), QueryDSL, JPA/Hibernate

## Development Commands

### Build & Run
```bash
# Build the project
./gradlew build

# Run the application locally
./gradlew bootRun

# Run with specific profile
./gradlew bootRun --args='--spring.profiles.active=local'

# Build specific module
./gradlew :backend:widyu-api:build
```

### Testing
```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :backend:widyu-api:test
./gradlew :backend:widyu-domain:test

# Run specific test class
./gradlew test --tests "com.widyu.global.util.PhoneNumberUtilTest"

# Run integration tests
./gradlew test --tests "*integration*"
```

### Development Tools
```bash
# Clean build artifacts
./gradlew clean

# Generate QueryDSL Q-classes (required after entity changes)
./gradlew compileJava

# Check dependencies
./gradlew dependencies
```

### Docker Deployment
```bash
# Development environment (with MySQL, Redis, Nginx)
./scripts/docker/dev-up.sh       # Start dev environment
./scripts/docker/dev-down.sh     # Stop dev environment
./scripts/docker/logs.sh dev     # View dev logs
./scripts/docker/logs.sh dev widyu-api  # View specific service logs

# Production environment
./scripts/docker/prod-up.sh      # Start production
./scripts/docker/prod-down.sh    # Stop production
./scripts/docker/logs.sh prod    # View production logs

# Manual docker-compose
docker compose up                                          # Local (auto-merges override)
docker compose -f docker-compose.yml -f docker-compose.dev.yml up   # Development
docker compose -f docker-compose.yml -f docker-compose.prod.yml up  # Production
```

**Note**: Development environment includes hot-reload via volume mounting, while production uses optimized Docker image builds.

## Application Architecture

### Multi-Module Architecture
The project uses Gradle multi-module structure:
- **`widyu-api`**: Main Spring Boot application (executable JAR with `main()`)
    - Controllers, services, repositories, configuration
    - Entry point: `com.widyu.WidyuApiApplication`
    - Depends on `widyu-domain`

- **`widyu-domain`**: Domain entities and core business logic (library JAR, `bootJar` disabled)
    - JPA entities with `@Entity`, `@RedisHash`
    - QueryDSL Q-classes generated here
    - No repositories (repositories are in `widyu-api`)

### Domain-Driven Design Structure
Each domain follows DDD with layered architecture:

```
auth/
├── controller/         # REST endpoints
│   └── docs/          # Swagger documentation interfaces (separate from controllers)
├── application/       # Business logic layer
│   ├── *Service.java         # Core business logic
│   ├── *Facade.java          # Orchestrates multiple services (e.g., AlbumFacade)
│   └── strategy/             # Strategy pattern implementations (e.g., OAuth providers)
├── repository/        # Data access (in widyu-api, accesses entities from widyu-domain)
├── dto/              # Request/response DTOs
│   ├── request/
│   └── response/
└── validator/        # Custom validation logic
```

**Key Pattern**: Entities live in `widyu-domain`, but repositories accessing them are in `widyu-api` module.

### Global Package Structure
Cross-cutting concerns and shared infrastructure in `com.widyu.global`:
- **`config/`** - Spring configuration (Security, WebSocket, S3, Redis, etc.)
- **`security/`** - JWT authentication, filters, UserDetailsService
- **`websocket/`** - WebSocket configuration, JWT handshake/channel interceptors
- **`aspect/`** - AOP aspects (`@ValidateFamilyAccess`, logging, etc.)
- **`error/`** - Global exception handling, custom exceptions
- **`filter/`** - Servlet filters (CORS, logging)
- **`util/`** - Utility classes (date, phone number, file handling)
- **`properties/`** - `@ConfigurationProperties` classes for YAML configs
- **`infrastructure/`** - External service clients (S3, FCM, OAuth, SMS)

### Core Domain Modules
- **`auth`** - Multi-provider OAuth (Apple, Naver, Kakao) + local authentication with SMS verification
    - **Temporary Token Flow**: SMS verification → temporary token → signup → JWT tokens
    - Three token types: Access Token, Refresh Token, Temporary Token (for signup flow)

- **`member`** - User management with Senior/Guardian roles
    - `Member` entity with `MemberType` enum (SENIOR/GUARDIAN)
    - `SeniorProfile` with invite codes for family connections
    - `FamilyConnection` links guardians to seniors

- **`album`** - Photo/video sharing with social interactions
    - Album CRUD with media upload (S3)
    - `AlbumLike`, `AlbumComment` (2-level: comments + replies), `AlbumView`, `AlbumUnlock`
    - Video thumbnail generation via FFmpeg

- **`fcm`** - Firebase push notifications
    - Event-driven architecture with `@EventListener`
    - Album events: created, viewed, liked, commented, unlocked
    - Scheduled notifications for inactive users (3/5/7 days)

- **`pay`** - Payment processing and point management
    - Point-based unlock system (50 points per album)
    - Seniors start with 100 points

- **`goal`** - Health-related features
    - `medicine` - Medication schedules with alarm-based verification
    - `walk` - Walk tracking
    - `healthschedule` - General health schedules
    - `addressbookmark` - Address bookmarks

- **`location`** - Real-time location tracking (WebSocket-based)
    - `realtime` - WebSocket endpoints for live location updates and trail tracking
    - `parentlocation` - Senior profile location management (REST API)
    - STOMP messaging: Seniors send location updates, guardians subscribe to receive real-time updates
    - Location data stored in Redis for performance, with trail history

### Key Architectural Patterns

#### Facade Pattern
Complex business operations spanning multiple services use Facade pattern:
- `AlbumFacade` - Orchestrates album upload, S3 storage, thumbnail generation, notifications
- `HealthScheduleFacade` - Coordinates health schedule operations

#### Strategy Pattern
OAuth providers use Strategy pattern with factory:
- `SocialLoginStrategy` interface
- `AppleLoginStrategy`, `KakaoLoginStrategy`, `NaverLoginStrategy` implementations
- `SocialLoginStrategyFactory` - Returns strategy based on provider enum

#### AOP for Authorization
Custom annotation `@ValidateFamilyAccess` with AOP aspect:
```java
@ValidateFamilyAccess(memberIdParam = "memberId")
public ResponseEntity<?> getWalkDetail(@RequestParam Long memberId) {
    // Automatically validates guardian has family connection to senior
}
```

#### Event-Driven Architecture
Domain events for cross-cutting concerns:
- Album events trigger FCM notifications via `AlbumNotificationListener`
- `@EventListener` methods handle: `AlbumCreatedEvent`, `AlbumLikedEvent`, etc.
- Decouples album domain from notification logic

#### Repository Pattern with QueryDSL
- JPA repositories with custom QueryDSL implementations
- Complex queries use QueryDSL for type safety
- Q-classes generated via `./gradlew compileJava`

#### WebSocket with STOMP
Real-time features use Spring WebSocket with STOMP protocol:
- **JWT Authentication**: Custom `JwtHandshakeInterceptor` and `JwtChannelInterceptor` validate tokens
- **Message Mapping**: `@MessageMapping` for WebSocket endpoints (e.g., `/app/location/update`)
- **User-Specific Queues**: `@SendToUser` sends responses to specific users (e.g., `/queue/location/ack`)
- **Broadcasting**: `SimpMessagingTemplate` broadcasts to multiple subscribers (e.g., family members tracking senior)
- **Connection Management**: `WebSocketEventListener` handles connect/disconnect events

Example WebSocket flow:
```java
// Senior sends location update to /app/location/update
@MessageMapping("/location/update")
@SendToUser("/queue/location/ack")  // ACK to sender
public LocationUpdateResponse updateLocation(@Payload LocationUpdateRequest request) {
    // Service broadcasts to guardians via /topic/location/{seniorId}
    return service.updateAndBroadcast(request);
}
```

## Configuration Profiles

Spring profiles in `application.yml`:
```yaml
spring:
  profiles:
    group:
      local: "local, datasource, fcm, pay, s3"
      dev: "dev, datasource, fcm, pay, s3"
      test: "test, fcm, pay, s3"
```

Environment-specific YAML files:
- `application-datasource.yml` - Database (MySQL/H2)
- `application-security.yml` - JWT secrets and expiration times
- `application-oauth.yml` - Social login provider configs (Apple, Naver, Kakao)
- `application-fcm.yml` - Firebase service account
- `application-pay.yml` - Payment gateway configuration (TossPayments)
- `application-redis.yml` - Redis connection
- `application-s3.yml` - AWS S3 credentials and bucket configuration
- `application-coolsms.yml` - SMS service for verification
- `application-video.yml` - FFmpeg paths and multipart file upload limits
- `application-medicine.yml` - Medicine API settings
- `application-actuator.yml` - Actuator endpoints

## Key Business Logic

### Authentication Flow

**Local Registration with SMS Verification:**
1. Guardian sends SMS verification (`POST /api/auth/guardian/sms/send`)
2. Verify code → receives **Temporary Token** (`POST /api/auth/guardian/sms/verify`)
3. Submit email/password with Temporary Token → receives JWT tokens (`POST /api/auth/guardian/signup`)
4. Temporary token expires after 30 minutes (`TemporaryMember.ttl = 1800`)

**OAuth Flow:**
1. Social login → returns either JWT tokens (existing user) or Social Temporary Token (new user)
2. If new user, must provide phone number to complete registration
3. Apple users: phone number collected separately due to privacy restrictions

**Token Management:**
- Access Token: Short-lived, sent in `Authorization: Bearer` header
- Refresh Token: Stored in Redis with TTL, used to reissue access token
- Temporary Token: One-time use for signup flow, stored in Redis as `TemporaryMember`

### User Roles & Relationships
- **Seniors**: Create albums, manage points, invite guardians via 7-character invite codes
    - Called "Parents" in UI/business context
    - Have `SeniorProfile` with unique `inviteCode`

- **Guardians**: View/interact with albums, unlock premium content using points
    - Caregivers, grandparents, family members
    - Connect to seniors by entering invite code

- **Family Connection**: `FamilyConnection` entity links guardian to `SeniorProfile`
    - Validated via `@ValidateFamilyAccess` annotation on controller methods
    - Guardians can only access seniors they're connected to

### Album System
- **Content Types**: Photos and videos with thumbnail generation (FFmpeg)
- **Social Features**:
    - 2-level hierarchical commenting (comments + replies)
    - Like/unlike functionality
    - View counting (first-time only per user)
- **Access Control**: Point-based unlocking system
    - Premium albums cost 50 points to unlock (`AlbumUnlock` entity)
    - Once unlocked, access is permanent for that guardian
- **Notifications**: Album interactions trigger FCM events

### Medicine Schedule System
- **Alarm-based Verification**: Proof submission restricted to ±30 minutes of scheduled alarm time
- **Duplicate Prevention**: Cannot submit multiple proofs on same day for same schedule
- **Monthly Statistics**: Calculates adherence rates for medication compliance tracking
- **Schedule Details**: Multiple alarm times per schedule with weekly frequency (e.g., Mon/Wed/Fri)

### Point Economy
- Seniors start with 100 points upon registration
- Unlocking albums costs 50 points (via `AlbumUnlock` entity)
- Points can be purchased through integrated payment system
- Supports culture expense benefits for families

### Real-Time Location Tracking
- **WebSocket Protocol**: STOMP over WebSocket for bidirectional communication
- **Architecture**: Seniors broadcast GPS coordinates; guardians subscribe to location updates
- **Data Flow**:
    1. Senior connects to WebSocket with JWT authentication
    2. Senior sends location updates to `/app/location/update`
    3. Service validates family connections and broadcasts to authorized guardians
    4. Guardians subscribe to `/topic/location/{seniorId}` to receive real-time updates
- **Location Trail**: Historical location points stored in Redis with configurable retention
- **Authorization**: Only connected family members can track senior locations (validated server-side)

## Testing Strategy

The application uses:
- **JUnit 5**: Primary testing framework with `@Test` annotations
- **Test Configuration**: H2 in-memory database with `application-test.yml` profile
- **Test Structure**: Minimal test coverage currently - test directories exist but are mostly empty
- **Test Profiles**: Isolated environment with mock configurations

## Database & Persistence

### MySQL (Production)
- Primary database with JPA/Hibernate
- Entities in `widyu-domain` with `@Entity`
- Relationships: `@OneToMany`, `@ManyToOne`, `@OneToOne`
- Auditing via `BaseTimeEntity` for `createdAt`/`updatedAt`

### Redis (Session & Cache)
Redis used for **temporary data storage** with TTL, NOT query caching:
- `RefreshToken` - JWT refresh tokens
- `VerificationCode` - SMS verification codes (short TTL)
- `TemporaryMember` - Temporary signup state (30 min TTL)
- `OAuthState` - OAuth CSRF protection
- `SeniorLocation` - Real-time location data with trail history (WebSocket feature)

Entities annotated with `@RedisHash` and `@TimeToLive` for automatic expiration.

### QueryDSL
- Q-classes generated in `build/generated/sources/annotationProcessor/java/main`
- Regenerate after entity changes: `./gradlew compileJava`
- Used for complex queries with joins, dynamic conditions

### H2 (Testing)
- In-memory database for tests
- Configured in `application-test.yml`

## External Integrations

- **Firebase**: FCM push notifications, service account authentication
- **AWS S3**: Media file storage (photos/videos), presigned URLs
- **OAuth Providers**:
    - Apple (JWT-based, requires public key fetching)
    - Naver (REST API)
    - Kakao (REST API)
- **TossPayments**: Payment gateway for multiple payment methods (card, virtual account, transfer, easy pay)
- **Coolsms**: SMS verification codes (Korean service)
- **FFmpeg**: Video thumbnail generation and processing
- **Public Medicine API**: Drug information lookup for medication schedules

## Security Implementation

- **JWT Authentication**:
    - Stateless tokens (Access + Refresh)
    - Three separate secrets in `application-security.yml`
    - Refresh tokens stored in Redis with TTL

- **OAuth2 Integration**:
    - Strategy pattern for multiple providers
    - CSRF protection via `OAuthState` stored in Redis

- **SMS Verification**:
    - Phone number validation via Coolsms
    - Required for local registration

- **Role-Based Access**:
    - `MemberType` enum (SENIOR/GUARDIAN)
    - AOP-based family connection validation

- **CORS Configuration**: Configured for cross-origin requests

## Development Guidelines

When implementing new features:

1. **Module Placement**:
    - Entities → `widyu-domain` with `@Entity` or `@RedisHash`
    - Repositories → `widyu-api` (accesses domain entities)
    - Services, controllers, DTOs → `widyu-api`

2. **Regenerate QueryDSL**: After entity changes, run `./gradlew compileJava` to regenerate Q-classes

3. **Swagger Documentation**:
    - Create separate `Docs` interface in `controller/docs/`
    - Keep actual controller clean, delegate documentation to interface

4. **Complex Operations**:
    - Use Facade pattern when spanning multiple services
    - Keep services focused on single responsibility

5. **Cross-Domain Logic**:
    - Consider event-driven approach with `@EventListener`
    - Avoids tight coupling between domains

6. **Authorization**:
    - Use `@ValidateFamilyAccess` for guardian-senior access control
    - Annotation automatically validates family connections

7. **Temporary Data**:
    - Use Redis with `@RedisHash` and `@TimeToLive` for expiring data
    - Examples: verification codes, temporary tokens, OAuth state

8. **Strategy Pattern**:
    - For pluggable implementations (like OAuth providers)
    - Use factory pattern to select strategy at runtime

9. **WebSocket Real-Time Features**:
    - Use `@MessageMapping` for WebSocket endpoints
    - Authenticate with JWT via custom handshake and channel interceptors
    - Use `SimpMessagingTemplate` to broadcast messages to subscribers
    - Validate family connections before broadcasting location data
    - Store real-time data in Redis for fast access and automatic expiration
