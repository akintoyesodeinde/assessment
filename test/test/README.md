# Wallet System (Spring Boot)

Production-ready wallet service with:
- JWT authentication for API access
- Double-entry ledger for transfers
- Transactional integrity and rollback safety
- Concurrency protection (row locking + optimistic versioning + idempotency)
- API rate limiting and centralized error handling

## Assumptions
- Java 21 is installed.
- Maven Wrapper (`mvnw` / `mvnw.cmd`) is used (system `mvn` is optional).
- Port `9090` is available.
- Database is in-memory H2 (`jdbc:h2:mem:testdb`), so data resets on restart.

## 1) Clone From GitHub

```bash
git clone https://github.com/akintoyesodeinde/test-3line.git
cd <your file path>/test/test
```

Windows PowerShell:

```powershell
git clone https://github.com/akintoyesodeinde/test-3line.git
Set-Location https://github.com/akintoyesodeinde/test-3line\test\test
```

## 2) Build Commands (Maven)

Linux/macOS:

```bash
./mvnw clean install
./mvnw test
```

Windows:

```powershell
.\mvnw.cmd clean install
.\mvnw.cmd test
```



INTELLIJ:

```Terminal
mvn clean install
.\mvnw clean install
```


Optional fast build (skip tests):

```powershell
.\mvnw.cmd clean package -Dskip


````Intellij Terminal
mvn clean package -DskipTests

## 3) Run / Start Application

### Option A: Run via Spring Boot Maven plugin
Windows:

```powershell
.\mvnw.cmd spring-boot:
```

````Intellij Terminal
mvn spring-boot:run

Enable H2 console explicitly at runtime:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--spring.h2.console.enabled=true"
```

### Option B: Run packaged JAR
Build first:

```powershell
.\mvnw.cmd clean package -DskipTests
```

Run:

```powershell
java -jar target\test-0.0.1-SNAPSHOT.jar --spring.h2.console.enabled=true --server.port=9090
```

## 4) Open in Browser
- UI Login: `http://localhost:9090/login`
- Register page: `http://localhost:9090/register`
- Dashboard: `http://localhost:9090/dashboard`
- H2 Console: `http://localhost:9090/h2-console/`

H2 Console login values:
- JDBC URL: `jdbc:h2:mem:testdb`
- User Name: `sa`
- Password: *(leave blank)*

## 5) Authentication Model
- Web UI: Spring Security form login (`/login`, `/register`)
- API: JWT Bearer tokens
  - Public: `/api/auth/register`, `/api/auth/login`
  - Protected: `/api/wallet/**` (requires `Authorization: Bearer <token>`)

## 6) API Endpoints (with sample payloads)

## `POST /api/auth/register`
Creates a user and returns JWT.

Request body:

```json
{
  "email": "alice@example.com",
  "password": "Str0ngPass!123"
}
```

## `POST /api/auth/login`
Authenticates and returns JWT.

Request body:

```json
{
  "email": "alice@example.com",
  "password": "Str0ngPass!123"
}
```

Example response:

```json
{
  "userId": 1,
  "email": "alice@example.com",
  "accessToken": "<jwt-token>",
  "tokenType": "Bearer",
  "expiresAt": "2026-02-21T10:30:00Z"
}
```

## `POST /api/wallet/users` (JWT required)
Creates a user + wallet account (internal/admin-style endpoint).

Request body:

```json
{
  "email": "bob@example.com"
}
```

Example response:

```json
{
  "userId": 2,
  "email": "bob@example.com",
  "accountNumber": "WAL1234ABCDEF56",
  "balance": 0.00
}
```

## `POST /api/wallet/fund/me` (JWT required)
Funds authenticated user wallet from treasury.

Request body:

```json
{
  "amount": 5000.00
}
```

Example response:

```json
{
  "idempotencyId": "TOPUP_20260221_abc123",
  "fromAccount": "WAL_TREASURY_ACCOUNT",
  "toAccount": "WAL1234ABCDEF56",
  "amount": 5000.00,
  "fromBalance": 999995000.00,
  "toBalance": 5000.00
}
```

## `POST /api/wallet/transfers` (JWT required)
Transfers between wallets.

Request body:

```json
{
  "fromAccount": "WAL1234ABCDEF56",
  "toAccount": "WAL6543FEDCBA98",
  "amount": 250.00,
  "idempotencyId": "tx_20260221_0001"
}
```

Validation notes:
- `fromAccount` / `toAccount`: `^WAL[0-9A-Z]{12}$`
- `amount` >= `0.01`, max 2 decimal places
- `idempotencyId`: 8-64 chars `[A-Za-z0-9_-]`

## 7) Web Endpoints (UI flow)
- `GET /login` - login page
- `GET /register` - register page
- `POST /register` - register via web form
- `GET /dashboard` - authenticated wallet dashboard
- `POST /wallet/create` - create wallet for current user
- `POST /wallet/fund` - fund current user wallet
- `POST /wallet/transfer` - transfer from current user wallet

## 8) Error Response Format (API)

```json
{
  "timestamp": "2026-02-21T08:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "requestId": "b45f52b2-f5ec-4be5-8dbf-7f49829d4ef7",
  "validationErrors": {
    "amount": "amount must be at least 0.01"
  }
}
```

## 9) Quick Test Sequence (API)

1. Register user: `POST /api/auth/register`
2. Login user: `POST /api/auth/login`
3. Use JWT to call:
   - `POST /api/wallet/fund/me`
   - `POST /api/wallet/transfers`

## 10) Stop Application
If running in foreground, press `Ctrl + C`.

If running in background on Windows (port 9090):

```powershell
$pid = (Get-NetTCPConnection -LocalPort 9090 | Select-Object -First 1 -ExpandProperty OwningProcess)
Stop-Process -Id $pid -Force
```
