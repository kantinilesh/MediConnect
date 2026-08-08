# MediConnect — Project Context

> **Single source of truth.** Update this file at the end of every development
> phase. Future sessions should read this document before touching any code.

---

## 1. Project Overview

**MediConnect** is a stateless, RESTful healthcare appointment-booking
microservice. It enables patients to discover doctors, view their availability,
and book/cancel appointments. Doctors can manage their schedule. Admins have
full oversight. A notification dispatcher handles async communication events.

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
| API Docs           | SpringDoc OpenAPI (Swagger UI)         | 2.6.0       |
| Deployment Target  | AWS EC2 (later phase)                  | —           |

**Why Maven over Gradle:** canonical Spring Initializr format, simpler
multi-module layout for future phases, better CI/CD script consistency.

---

## 3. Architecture Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| A1 | Stateless JWT auth (no server-side sessions) | Horizontally scalable; fits EC2 auto-scaling |
| A2 | Joined-table JPA inheritance for User/Doctor/Patient | Clean schema, avoids NULLs, simple cross-join queries |
| A3 | Template-based availability (not pre-generated slots) | Reduces row explosion; slots derived at booking time |
| A4 | Specialization as DB ENUM column | Type-safe at DB level; migration needed to add values |
| A5 | Clinic is a column on Doctor (not its own entity) | Simplest for Phase 0; promote to entity in Phase 2 if multi-doctor clinics needed |
| A6 | Notification.payload as JSON column | Flexible dispatcher input without schema churn |
| A7 | JWT secret via env-var `MEDICONNECT_JWT_SECRET` | Upgrade path → AWS Secrets Manager in prod phase |
| A8 | `ddl-auto: update` for local dev, `validate` for prod | Fast iteration locally; safety in prod |

---

## 4. Entity List & Relationships

```
User (parent)
 ├── Doctor  (JOINED inheritance — one Doctor row per Doctor User)
 └── Patient (JOINED inheritance — one Patient row per Patient User)

Doctor  ──< Availability  (1:N)
Doctor  ──< Appointment   (1:N)
Patient ──< Appointment   (1:N)
User    ──< Notification  (1:N)
```

### User
| Field          | Type          | Notes                          |
|----------------|---------------|--------------------------------|
| id             | UUID (PK)     | Generated                      |
| email          | VARCHAR unique| Indexed                        |
| passwordHash   | VARCHAR       | BCrypt                         |
| role           | ENUM          | PATIENT, DOCTOR, ADMIN         |
| firstName      | VARCHAR       |                                |
| lastName       | VARCHAR       |                                |
| phone          | VARCHAR       |                                |
| enabled        | BOOLEAN       | Account active flag            |
| emailVerified  | BOOLEAN       |                                |
| createdAt      | TIMESTAMP     | @CreatedDate                   |
| updatedAt      | TIMESTAMP     | @LastModifiedDate              |

### Doctor
| Field               | Type     | Notes                          |
|---------------------|----------|--------------------------------|
| id                  | UUID (FK)| → User.id                      |
| specialization      | ENUM     | CARDIOLOGY, DERMATOLOGY, …     |
| qualifications      | TEXT     |                                |
| clinicName          | VARCHAR  |                                |
| clinicAddress       | VARCHAR  |                                |
| yearsOfExperience   | INT      |                                |
| consultationFee     | DECIMAL  |                                |
| bio                 | TEXT     |                                |
| rating              | DECIMAL  | Cached aggregate               |
| Composite Index     | —        | (specialization)               |

### Patient
| Field         | Type      | Notes              |
|---------------|-----------|--------------------|
| id            | UUID (FK) | → User.id          |
| dateOfBirth   | DATE      |                    |
| gender        | ENUM      | MALE, FEMALE, OTHER|
| bloodGroup    | VARCHAR   |                    |
| medicalHistory| TEXT      |                    |

### Availability
| Field               | Type      | Notes                               |
|---------------------|-----------|-------------------------------------|
| id                  | UUID (PK) |                                     |
| doctorId            | UUID (FK) | → Doctor.id                         |
| dayOfWeek           | ENUM      | MONDAY … SUNDAY                     |
| startTime           | TIME      |                                     |
| endTime             | TIME      |                                     |
| slotDurationMinutes | INT       | e.g. 30                             |
| isActive            | BOOLEAN   |                                     |
| Composite Index     | —         | (doctorId, dayOfWeek)               |

### Appointment
| Field               | Type      | Notes                                   |
|---------------------|-----------|-----------------------------------------|
| id                  | UUID (PK) |                                         |
| patientId           | UUID (FK) | → Patient.id                            |
| doctorId            | UUID (FK) | → Doctor.id                             |
| appointmentDate     | DATE      |                                         |
| startTime           | TIME      |                                         |
| endTime             | TIME      |                                         |
| status              | ENUM      | PENDING, CONFIRMED, CANCELLED, COMPLETED|
| reason              | TEXT      | Chief complaint                         |
| notes               | TEXT      | Doctor's post-consult notes             |
| cancellationReason  | TEXT      |                                         |
| createdAt           | TIMESTAMP |                                         |
| updatedAt           | TIMESTAMP |                                         |
| Composite Index 1   | —         | (doctorId, appointmentDate, status)     |
| Composite Index 2   | —         | (patientId, status)                     |

### Notification
| Field       | Type      | Notes                                             |
|-------------|-----------|---------------------------------------------------|
| id          | UUID (PK) |                                                   |
| userId      | UUID (FK) | → User.id                                         |
| type        | ENUM      | EMAIL, SMS, PUSH                                  |
| event       | ENUM      | APPOINTMENT_BOOKED, CANCELLED, REMINDER, …        |
| payload     | JSON      | Flexible dispatcher input                         |
| status      | ENUM      | PENDING, SENT, FAILED                             |
| scheduledAt | TIMESTAMP |                                                   |
| sentAt      | TIMESTAMP | Nullable                                          |
| createdAt   | TIMESTAMP |                                                   |
| Index 1     | —         | (userId, status)                                  |
| Index 2     | —         | (scheduledAt, status) — dispatcher polling        |

---

## 5. Package Structure

```
com.mediconnect
 ├── MediConnectApplication.java
 ├── config/          SecurityConfig, JwtConfig, CorsConfig, SwaggerConfig
 ├── controller/      AuthController, DoctorController, AppointmentController, …
 ├── service/         interfaces + Impl classes
 ├── repository/      Spring Data JPA repos
 ├── entity/          JPA entities
 ├── dto/             Request / Response DTOs
 ├── security/        JwtTokenProvider, JwtAuthenticationFilter, UserDetailsServiceImpl
 ├── exception/       GlobalExceptionHandler, custom exception classes
 └── dispatcher/      NotificationDispatcher (stub → async impl in Phase 3)
```

---

## 6. API Conventions

- **Base path:** `/api/v1`
- **Auth header:** `Authorization: Bearer <jwt>`
- **Response envelope:**
  ```json
  { "success": true, "data": {}, "message": "OK", "timestamp": "…" }
  ```
- **Error envelope:**
  ```json
  { "success": false, "error": "NOT_FOUND", "message": "Doctor not found", "timestamp": "…" }
  ```
- **Pagination:** `?page=0&size=20&sort=createdAt,desc`
- **Date format:** ISO-8601 (`yyyy-MM-dd`, `HH:mm`)

---

## 7. Configuration Profiles

| Profile | Active when              | DB host            | DDL auto  |
|---------|--------------------------|--------------------|-----------|
| local   | `docker compose up`      | `mysql` (compose)  | update    |
| prod    | EC2 deployment           | RDS / EC2 MySQL    | validate  |

---

## 8. Folder / File Map

```
mediconnect/
├── src/main/java/com/mediconnect/…  (see §5)
├── src/main/resources/
│   ├── application.yml
│   ├── application-local.yml
│   └── application-prod.yml
├── src/test/java/com/mediconnect/…
├── docker-compose.yml
├── Dockerfile
├── pom.xml
└── PROJECT_CONTEXT.md   ← this file
```

---

## 9. Decisions Log

| Date       | Phase | Decision                                              |
|------------|-------|-------------------------------------------------------|
| 2026-08-08 | 0     | Project initialised; Maven chosen over Gradle         |
| 2026-08-08 | 0     | Joined-table inheritance approved for User hierarchy  |
| 2026-08-08 | 0     | Template-based availability (not pre-generated slots) |
| 2026-08-08 | 0     | Specialization as ENUM column on Doctor               |
| 2026-08-08 | 0     | Clinic kept as columns on Doctor (revisit Phase 2)    |
| 2026-08-08 | 0     | JWT secret via env-var; upgrade to AWS SM in prod     |

---

## 10. Future Phases (Planned)

| Phase | Theme                                                   |
|-------|---------------------------------------------------------|
| 1     | Auth endpoints (register/login/refresh), RBAC           |
| 2     | Doctor discovery, availability CRUD, slot generation    |
| 3     | Appointment booking, conflict detection                 |
| 4     | Notification dispatcher (async / email / SMS)           |
| 5     | EC2 deployment, RDS, nginx reverse proxy                |
| 6     | Rating system, admin dashboard APIs                     |
