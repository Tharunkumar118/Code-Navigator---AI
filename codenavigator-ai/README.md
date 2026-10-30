# CodeNavigator AI

Intelligent Software Architecture & Code Understanding Platform.

## Module 1 — Project Setup (current status)

Backend skeleton is up: Spring Boot 3.3, Java 21, MySQL, layered architecture,
global exception handling, CORS, and a working health check.

### Prerequisites
- JDK 21
- Maven 3.9+
- MySQL 8.0+ running locally

### Run it

```bash
cd backend

# 1. Create the database (or let createDatabaseIfNotExist=true handle it)
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS codenavigator_ai;"

# 2. Set credentials (or edit application-dev.yml directly)
export DB_USERNAME=root
export DB_PASSWORD=your_password

# 3. Run
mvn spring-boot:run
```

### Verify it worked

```bash
curl http://localhost:8080/api/v1/health
curl http://localhost:8080/actuator/health
```

Expected response from `/api/v1/health`:
```json
{
  "success": true,
  "message": "Service is healthy",
  "data": { "status": "UP", "service": "CodeNavigator AI Backend", "version": "0.0.1-SNAPSHOT" },
  "timestamp": "2026-07-03T..."
}
```

### Run tests
```bash
mvn test
```

## Module 2 — Authentication (JWT, Roles)

### What was added
- `User` / `Role` entities (many-to-many), `ERole` enum: `ROLE_STUDENT`, `ROLE_FACULTY`, `ROLE_ADMIN`
- BCrypt password hashing
- Stateless JWT auth (`JwtUtils`, `JwtAuthFilter`, `SecurityConfig`)
- `DataSeeder` auto-inserts the 3 roles on first boot
- Endpoints: register, login, get-my-profile

### API List

| Method | Endpoint | Auth required | Purpose |
|---|---|---|---|
| POST | `/api/v1/auth/register` | No | Create account, returns JWT immediately |
| POST | `/api/v1/auth/login` | No | Authenticate, returns JWT |
| GET  | `/api/v1/users/me` | Yes (`Bearer <token>`) | Get current user's profile |

### Step-by-step: how to verify Module 2 is actually working

**Step 1 — Start MySQL and the app**
```bash
cd backend
export DB_USERNAME=root
export DB_PASSWORD=your_password
mvn spring-boot:run
```
Watch the startup logs for lines like:
```
Seeding missing role: ROLE_STUDENT
Seeding missing role: ROLE_FACULTY
Seeding missing role: ROLE_ADMIN
```
If you see these once (and never again on restart), `DataSeeder` is working correctly.

**Step 2 — Check the database directly**
```sql
USE codenavigator_ai;
SHOW TABLES;               -- expect: users, roles, user_roles
SELECT * FROM roles;       -- expect 3 rows
```

**Step 3 — Register a user**
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Aditi Sharma","email":"aditi@example.com","password":"SecurePass123"}'
```
Expected: `201 Created`, `data.accessToken` present, `data.roles = ["ROLE_STUDENT"]`.

**Step 4 — Confirm duplicate email is rejected**
Run the exact same curl command again.
Expected: `409 Conflict`, `"An account with email 'aditi@example.com' already exists"`.

**Step 5 — Login**
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"aditi@example.com","password":"SecurePass123"}'
```
Expected: `200 OK` with a fresh `accessToken`. Copy this token for Step 7.

**Step 6 — Confirm wrong password is rejected**
Re-run Step 5 with an incorrect password.
Expected: `401 Unauthorized`, `"Invalid email or password"`.

**Step 7 — Access the protected profile endpoint WITHOUT a token**
```bash
curl http://localhost:8080/api/v1/users/me
```
Expected: `401 Unauthorized` (Spring Security blocks it — proves the filter chain is active).

**Step 8 — Access it WITH the token**
```bash
curl http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer <paste token from step 5>"
```
Expected: `200 OK` with your profile (`fullName`, `email`, `roles`, `createdAt`).

**Step 9 — Run the automated tests (no MySQL needed — uses H2)**
```bash
mvn test
```
Expected: `AuthControllerTest` passes both `register_thenLogin_thenAccessProtectedProfile_fullFlowWorks` and `login_withWrongPassword_returns401`, plus the Module 1 tests.

If all 9 steps behave as described, Module 2 is confirmed working end-to-end.

### Database Changes
New tables: `users`, `roles`, `user_roles` (join table). Auto-created via `ddl-auto: update` in dev.

### Common Errors & Troubleshooting

| Error | Cause | Fix |
|---|---|---|
| `500` on register: "Role not found" | `DataSeeder` didn't run / roles table empty | Restart app; check startup logs for seeding messages |
| `401` on every request including `/auth/login` | `SecurityConfig` `permitAll()` matcher typo | Confirm `/api/v1/auth/**` exactly matches your request path |
| JWT `WeakKeyException` at startup | `app.jwt.secret` too short for HS256 (needs ≥256 bits / 32 chars) | Use a longer secret in `JWT_SECRET` env var |
| `403 Forbidden` instead of `401` on protected routes | CORS preflight (`OPTIONS`) being blocked | Confirm `.cors(Customizer.withDefaults())` is present and `WebConfig` origins match your frontend port |
| `mvn test` fails trying to reach MySQL | Test not using `@ActiveProfiles("test")` | Every test class must have this annotation to use H2 |

### Git Commit
```
git commit -m "Module 2: Authentication - JWT, BCrypt, Role-based access (Student/Faculty/Admin)"
```

## Module 3 — Dashboard (Student + Admin)

### What was added
- Entities: `CodeRepository`, `AnalysisRecord`, `Report`, `ActivityLog` (empty/zeroed until Modules 4-14 write into them)
- `DashboardController` with role-scoped endpoints
- Auth flow now writes to `activity_logs` on register/login, feeding the Admin "Recent Activity" feed

### API List

| Method | Endpoint | Auth required | Purpose |
|---|---|---|---|
| GET | `/api/v1/dashboard/student` | Yes (any authenticated user) | Own repos, analysis/report/AI-usage counts |
| GET | `/api/v1/dashboard/admin` | Yes (`ROLE_ADMIN` only) | Platform-wide stats, DB health, recent activity |

### Step-by-step verification

**Step 1 — Rebuild and run**
```bash
mvn clean install
mvn spring-boot:run
```
Watch for new tables in the startup logs (Hibernate DDL): `code_repositories`, `analysis_records`, `reports`, `activity_logs`.

**Step 2 — Register a normal user and hit the student dashboard**
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"fullName":"Priya Patel","email":"priya@example.com","password":"SecurePass123"}'
```
Copy the `accessToken`, then:
```bash
curl http://localhost:8080/api/v1/dashboard/student \
  -H "Authorization: Bearer <token>"
```
Expected: `200 OK`, all counts `0`, `recentRepositories: []` — correct, since no repos exist yet (Module 4 changes this).

**Step 3 — Confirm a Student is blocked from the Admin dashboard**
```bash
curl -i http://localhost:8080/api/v1/dashboard/admin \
  -H "Authorization: Bearer <same student token>"
```
Expected: `403 Forbidden` — proves `@PreAuthorize("hasRole('ADMIN')")` works.

**Step 4 — Create a real Admin and verify the Admin dashboard**
Public registration deliberately cannot create Admins. Insert one directly:
```sql
INSERT INTO users (full_name, email, password, enabled, created_at, updated_at)
VALUES ('System Admin', 'admin@example.com',
  '$2a$10$replace-with-a-real-bcrypt-hash', true, NOW(), NOW());

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'admin@example.com' AND r.name = 'ROLE_ADMIN';
```
(Generate a real BCrypt hash for a known password — e.g. temporarily log `passwordEncoder.encode("yourpassword")` from a scratch test, or use an online BCrypt generator for local dev only.)

Then log in as that admin and call:
```bash
curl http://localhost:8080/api/v1/dashboard/admin \
  -H "Authorization: Bearer <admin token>"
```
Expected: `200 OK` with `totalUsers`, `totalStudents`, `databaseStatus: "UP"`, and `recentActivity` showing your register/login events.

**Step 5 — Run automated tests**
```bash
mvn test
```
`DashboardControllerTest` covers all four scenarios above automatically (it seeds its own Admin via the repository directly — no manual SQL needed for tests).

### Database Changes
New tables: `code_repositories`, `analysis_records`, `reports`, `activity_logs`.

### Common Errors & Troubleshooting

| Error | Cause | Fix |
|---|---|---|
| Admin endpoint returns `403` even with correct role | `hasRole('ADMIN')` vs authority mismatch | Our authorities are already stored as `ROLE_ADMIN`; `hasRole('ADMIN')` auto-prefixes `ROLE_` — don't add `ROLE_` yourself in `@PreAuthorize` |
| `500` fetching student dashboard | User has no row anywhere but a valid JWT (edge case: user deleted after token issued) | Expected `ResourceNotFoundException` → verify it maps to `404`, not `500` |
| `databaseStatus: "DOWN"` on admin dashboard | MySQL connection pool exhausted or DB down | Check `HikariPool` logs; confirm MySQL is reachable |
| Counts always `0` even after Module 4/5 work | Forgot to set `owner` / `requestedBy` on new entities | Every `CodeRepository`/`AnalysisRecord`/`Report` must be linked to a real `User` for counts to attribute correctly |

### Git Commit
```
git commit -m "Module 3: Dashboard - Student and Admin views with role-based access and activity logging"
```

## Modules Roadmap
1. ✅ Project Setup
2. ✅ Authentication (JWT, Roles)
3. ✅ Dashboard
4. ⬜ GitHub Integration
5. ⬜ Project Scanner
... (see full spec)
