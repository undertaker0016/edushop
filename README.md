# EduShop backend

Java 21 / Spring Boot REST API for the EduShop course marketplace. The application uses MySQL, Flyway and stateless JWT access tokens. Buyer accounts have no application role; instructor and admin access is granted by an administrator.

## Configure and run on Windows PowerShell

Create a MySQL database named `edushop` and set credentials for the database account you will use. Set the application configuration in the current PowerShell session:

```powershell
$env:EDUSHOP_DB_USERNAME = 'your-mysql-user'
$env:EDUSHOP_DB_PASSWORD = 'your-mysql-password'
$jwtBytes = New-Object byte[] 32
$jwtGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
$jwtGenerator.GetBytes($jwtBytes)
$env:EDUSHOP_JWT_SECRET_BASE64 = [Convert]::ToBase64String($jwtBytes)
$jwtGenerator.Dispose()
Remove-Variable jwtBytes, jwtGenerator
$env:EDUSHOP_CORS_ALLOWED_ORIGINS = 'http://localhost:3000,http://localhost:5173'
```

The JWT key must decode to at least 32 bytes. Keep it in a secret manager or environment variable; changing it invalidates tokens already issued. Set `EDUSHOP_CORS_ALLOWED_ORIGINS` to the exact frontend origins used in each environment.

Run the application and tests:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd package
java -jar .\target\edushop-0.0.1-SNAPSHOT.jar
```

Flyway applies migrations at startup. Tests use the separate `edushop_test` schema from `src/test/resources/application-test.yml` and run with the `test` profile.

To bootstrap the first administrator, set these variables before starting the app:

```powershell
$env:EDUSHOP_BOOTSTRAP_ADMIN_EMAIL = 'admin@example.com'
$env:EDUSHOP_BOOTSTRAP_ADMIN_PASSWORD = 'use-a-unique-password-of-your-own'
```

The bootstrap creates the account only if it does not exist. It does not grant ADMIN to an existing account. Clear the bootstrap variables after initial setup. An administrator can grant instructor access and create the instructor profile with `POST /api/v1/admin/instructors` for an existing account.

## API quick start

Hướng dẫn Postman đầy đủ bằng tiếng Việt: [POSTMAN_GUIDE_VI.md](D:\Projects\edushop\POSTMAN_GUIDE_VI.md).

- Base URL: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Register a buyer: `POST /api/v1/auth/register`
- Login: `POST /api/v1/auth/login`
- Use the returned Bearer access token with Swagger's **Authorize** button.
- Public catalog endpoints work without a token. Checkout requires a logged-in buyer and an `Idempotency-Key` header.
- Orders with a VND total of zero complete and enroll immediately. Paid orders hold capacity for 15 minutes.
- No real payment provider is configured. Paid orders remain pending until a provider adapter and its credentials are supplied; the test profile's payment adapter is test-only.

Migration V5 changes enrollment uniqueness to preserve cancelled enrollment history while allowing a later repurchase. Order items keep the original course/price snapshot. Private classroom links are returned only from the enrollment classroom API after checking ownership, paid order and active enrollment.
