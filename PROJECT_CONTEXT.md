# MediConnect — Project Context

> **Single source of truth.** Update this file at the end of every development
> phase. Future sessions should read this document before touching any code.

---

## 1. Project Overview

**MediConnect** is a stateless, RESTful healthcare appointment-booking
microservice. It enables patients to discover doctors, view their availability,
and book/cancel/reschedule appointments. Doctors can manage recurring schedule templates
and generate bookable slots. A notification dispatcher handles async communication events.

---

## 2. Tech Stack

| Layer              | Technology                             | Version     |
|--------------------|----------------------------------------|-------------|
| Language           | Java                                   | 17          |
| Framework          | Spring Boot                            | 3.3.5       |
| Security           | Spring Security 6 + JWT (jjwt)         | 0.12.6      |
| ORM                | Spring Data JPA / Hibernate 6          | —           |
| Database           | MySQL                                  | 8.0         |
| Build Tool         | Maven                                  | 3.9+        |
| Containerisation   | Docker + docker-compose                | —           |
| Testing            | JUnit 5, Spring Boot Test, Testcontainers| 1.19.8    |
| API Docs           | SpringDoc OpenAPI (Swagger UI)         | 2.6.0       |
| Deployment Target  | AWS EC2 (later phase)                  | —           |

---

## 3. Architecture Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| A1 | Stateless JWT auth (no server-side sessions) | Horizontally scalable; fits EC2 auto-scaling |
| A2 | Joined-table JPA inheritance for User/Doctor/Patient | Clean schema, avoids NULLs, simple cross-join queries |
| A3 | Template-based availability + concrete `Slot` generation | Doctors define recurring weekly rules (`Availability`); system generates bookable `Slot` rows for date ranges |
| A4 | Specialization as DB ENUM column | Type-safe at DB level |
| A5 | Clinic as columns on Doctor | Simplest model for early phases |
| A6 | Notification.payload as JSON column | Flexible dispatcher input without schema churn |
| A7 | JWT secret via env-var `MEDICONNECT_JWT_SECRET` | Upgrade path → AWS Secrets Manager in prod phase |
| A8 | `ddl-auto: update` for local dev, `validate` for prod | Fast iteration locally; safety in prod |
| A9 | **Pessimistic Write Locking (`PESSIMISTIC_WRITE`) + DB Unique Constraint (`uk_doctor_date_time`)** | Eliminates double-booking race conditions under high concurrency; serialized locking prevents retry storms; DB unique constraint provides engine-level defense-in-depth |

---

## 4. Entity List & Relationships

```
User (parent)
 ├── Doctor  (JOINED inheritance — one Doctor row per Doctor User)
 └── Patient (JOINED inheritance — one Patient row per Patient User)

Doctor  ──< Availability  (1:N)
Doctor  ──< Slot          (1:N)
Doctor  ──< Appointment   (1:N)
Patient ──< Appointment   (1:N)
Slot    ──── Appointment   (1:1)
User    ──< Notification  (1:N)
```

### User
- `id` (UUID PK), `email` (unique), `passwordHash`, `role` (PATIENT, DOCTOR, ADMIN), `firstName`, `lastName`, `phone`, `enabled`, `emailVerified`, `createdAt`, `updatedAt`.

### Doctor
- `id` (UUID FK → User.id), `specialization` (ENUM), `qualifications`, `clinicName`, `clinicAddress`, `yearsOfExperience`, `consultationFee`, `bio`, `rating`.

### Patient
- `id` (UUID FK → User.id), `dateOfBirth`, `gender`, `bloodGroup`, `medicalHistory`.

### Availability
- `id` (UUID PK), `doctorId` (FK), `dayOfWeek` (MONDAY..SUNDAY), `startTime`, `endTime`, `slotDurationMinutes`, `isActive`.
- Composite Index: `(doctorId, dayOfWeek)`

### Slot
- `id` (UUID PK), `doctorId` (FK), `slotDate` (DATE), `startTime` (TIME), `endTime` (TIME), `status` (AVAILABLE, BOOKED, BLOCKED), `version`.
- **Unique Constraint**: `uk_doctor_date_time` on `(doctor_id, slot_date, start_time)`.
- Index: `(doctor_id, slot_date, status)`.

### Appointment
- `id` (UUID PK), `patientId` (FK), `doctorId` (FK), `slotId` (FK), `appointmentDate` (DATE), `startTime` (TIME), `endTime` (TIME), `status` (PENDING, CONFIRMED, CANCELLED, COMPLETED), `reason`, `notes`, `cancellationReason`, `createdAt`, `updatedAt`.
- Composite Index 1: `(doctorId, appointmentDate, status)`
- Composite Index 2: `(patientId, status)`

---

## 5. API Contracts

### Response Envelope (`ApiResponse<T>`)
```json
{
  "success": true,
  "message": "Success",
  "data": {},
  "timestamp": "2026-08-09T02:15:00Z"
}
```

### Endpoints Implemented

#### Doctor Discovery
- `GET /api/v1/doctors/search?specialization=CARDIOLOGY&name=Smith&location=City&availableDate=2026-08-10&minRating=4.0&page=0&size=10`
  - Returns paginated list of doctors matching criteria.
- `GET /api/v1/doctors/{id}`
  - Returns doctor profile details.

#### Availability & Slot Management
- `POST /api/v1/doctors/{doctorId}/availabilities`
  - Body: `{ "dayOfWeek": "MONDAY", "startTime": "09:00", "endTime": "17:00", "slotDurationMinutes": 30 }`
- `GET /api/v1/doctors/{doctorId}/availabilities`
  - Lists doctor availability templates.
- `POST /api/v1/doctors/{doctorId}/slots/generate`
  - Body: `{ "startDate": "2026-08-10", "endDate": "2026-08-15" }`
  - Generates bookable slots for the date range.
- `GET /api/v1/doctors/{doctorId}/slots?date=2026-08-10`
  - Returns AVAILABLE slots for doctor on specified date.

#### Appointment Booking
- `POST /api/v1/appointments`
  - Body: `{ "patientId": "...", "slotId": "...", "reason": "Routine checkup" }`
  - Acquires `PESSIMISTIC_WRITE` lock on slot; sets slot to `BOOKED` & creates appointment.
- `PUT /api/v1/appointments/{id}/cancel`
  - Body: `{ "cancellationReason": "Feeling unwell" }`
  - Cancels appointment & frees slot back to `AVAILABLE`.
- `PUT /api/v1/appointments/{id}/reschedule`
  - Body: `{ "newSlotId": "..." }`
  - Atomically locks new slot, frees old slot, & updates appointment.
- `GET /api/v1/appointments/{id}`
- `GET /api/v1/appointments/patient/{patientId}?page=0&size=10`
- `GET /api/v1/appointments/doctor/{doctorId}?date=2026-08-10&page=0&size=10`

---

## 6. Decisions Log

| Date       | Phase | Decision                                              |
|------------|-------|-------------------------------------------------------|
| 2026-08-08 | 0     | Project initialised; Maven chosen over Gradle         |
| 2026-08-08 | 0     | Joined-table inheritance for User hierarchy           |
| 2026-08-08 | 0     | Specialization as ENUM column on Doctor               |
| 2026-08-08 | 0     | JWT secret via env-var                                |
| 2026-08-09 | 1     | Concrete `Slot` entity created from `Availability` templates |
| 2026-08-09 | 1     | **Pessimistic Write Locking (`PESSIMISTIC_WRITE`) + `uk_doctor_date_time` DB Unique Constraint** adopted for atomic double-booking prevention |
| 2026-08-09 | 1     | Integrated Testcontainers MySQL 8 concurrency test proving 100% race condition protection under 10 parallel threads |
| 2026-08-09 | 2     | DB-backed refresh tokens (`RefreshToken` table) with explicit delete-on-rotation (not just revoke) to avoid `@OneToOne` unique constraint violation |
| 2026-08-09 | 2     | JWT `token_type` claim (`ACCESS`/`REFRESH`) added to prevent refresh tokens being used as access tokens |
| 2026-08-09 | 2     | `signingKey()` catches `RuntimeException` (not just `IllegalArgumentException`) to handle JJWT's `DecodingException` for non-base64 secrets |
| 2026-08-09 | 2     | Java 26 (Homebrew default) breaks Lombok annotation processing; project must be built with Java 17 (`JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`) |
| 2026-08-09 | 2     | `Slot` entity explicit getters added alongside `@Getter` as a Lombok apt-ordering workaround on Java 17 + Maven 3.9 |

---

## 7. Auth & Security — Phase 2

### Authentication Flow

```
Client → POST /api/v1/auth/register/patient  →  201 { accessToken, refreshToken, userId, role }
Client → POST /api/v1/auth/register/doctor   →  201 { accessToken, refreshToken, userId, role }
Client → POST /api/v1/auth/login             →  200 { accessToken, refreshToken, userId, role }
Client → POST /api/v1/auth/refresh           →  200 { accessToken (new), refreshToken (rotated) }
```

- **Passwords**: BCrypt (strength 12)
- **Access token**: short-lived JWT (default 24 h, `MEDICONNECT_JWT_EXPIRATION`)
- **Refresh token**: opaque random string stored in `refresh_tokens` table (default 7 days, `MEDICONNECT_JWT_REFRESH_EXPIRATION`). One active token per user — rotation deletes old token before inserting new one.
- **JWT claims**: `sub` (email), `roles` (comma-separated), `token_type` (`ACCESS`)

### Role-Based Access Control (RBAC)

| Endpoint | Method | Required Role(s) |
|----------|--------|-----------------|
| `/api/v1/auth/**` | ANY | **Public** — no JWT required |
| `/api/v1/doctors/search` | GET | **Public** — no JWT required |
| `/api/v1/doctors/{id}` | GET | **Public** — no JWT required |
| `/api/v1/appointments` | POST (book) | `ROLE_PATIENT` |
| `/api/v1/appointments/{id}/cancel` | PUT | `ROLE_PATIENT`, `ROLE_ADMIN` |
| `/api/v1/appointments/{id}/reschedule` | PUT | `ROLE_PATIENT`, `ROLE_ADMIN` |
| `/api/v1/appointments/{id}` | GET | Any authenticated |
| `/api/v1/appointments/patient/{id}` | GET | `ROLE_PATIENT`, `ROLE_ADMIN` |
| `/api/v1/appointments/doctor/{id}` | GET | `ROLE_DOCTOR`, `ROLE_ADMIN` |
| `/api/v1/doctors/{id}/availabilities` | POST | `ROLE_DOCTOR` |
| `/api/v1/doctors/{id}/availabilities` | GET | `ROLE_DOCTOR`, `ROLE_ADMIN` |
| `/api/v1/doctors/{id}/slots/generate` | POST | `ROLE_DOCTOR` |
| `/api/v1/doctors/{id}/slots` | GET | Any authenticated |

### Error Responses

| HTTP | When |
|------|------|
| 401  | No JWT, expired JWT, malformed JWT, or wrong `token_type` |
| 403  | Valid JWT but insufficient role for the endpoint |
| 400  | Validation failure (missing fields, expired refresh token, duplicate email) |

### Build Note

> **Always build with Java 17**, not the Homebrew default Java 26:
> ```bash
> JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn clean test
> ```

