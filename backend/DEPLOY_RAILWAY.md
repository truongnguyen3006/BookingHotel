# Deploy Spring Boot + MySQL to Railway

This project is prepared for a simple portfolio deployment on Railway using the existing `backend/Dockerfile` and the `prod` Spring profile.

## 1. Create the Railway project

1. Create a new Railway project.
2. Add a **MySQL** database service.
3. Add the GitHub repository as a service.
4. Set the GitHub service **Root Directory** to `/backend` so Railway detects `backend/Dockerfile` as the service Dockerfile.

Railway's MySQL service exposes connection variables such as `MYSQLHOST`, `MYSQLPORT`, `MYSQLUSER`, `MYSQLPASSWORD`, and `MYSQLDATABASE`.

## 2. Backend environment variables

In the backend service Variables tab, adapt `deploy/railway.env.example`.

Required values:

```text
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USERNAME=${{MySQL.MYSQLUSER}}
DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
JWT_SECRET_B64=<strong base64 secret>
```

Railway injects `PORT`; `application-prod.yml` binds Spring Boot to that port automatically.

Generate a JWT secret in PowerShell:

```powershell
$bytes = New-Object byte[] 64
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
[Convert]::ToBase64String($bytes)
```

Store the output only in Railway Variables / a secret manager.

## 3. Bootstrap the ADMIN account

For the first deployment, set:

```text
ADMIN_BOOTSTRAP_ENABLED=true
ADMIN_EMAIL=<your admin email>
ADMIN_PASSWORD=<at least 8 characters>
ADMIN_DISPLAY_NAME=Hotel Admin
```

If the email already exists as a USER, startup promotes that account to ADMIN without changing its password. If it does not exist, startup creates it using `ADMIN_PASSWORD`.

After the admin exists, `ADMIN_BOOTSTRAP_ENABLED=false` is recommended. The ADMIN row remains in MySQL.

## 4. Health check and public URL

After deployment:

1. Generate a Railway public domain for the backend service.
2. Configure the health check path as `/actuator/health` in the Railway service settings.
3. Verify:

```text
https://<your-domain>/actuator/health
https://<your-domain>/api/rooms
```

The first endpoint should return an `UP` health status and the second should return the room catalog.

## 5. Connect the Android release build

Copy the generated HTTPS backend URL, including the trailing `/`, for example:

```text
https://<actual-production-backend-domain>/
```

Use it as `BOOKING_API_PROD_URL` when building locally, and create the same **Repository Variable** under GitHub `Settings -> Secrets and variables -> Actions -> Variables` for the Android Release workflow.

Do not put DB credentials, JWT secrets, or signing passwords into Git.


## Phase 5 environment separation

Staging must use a separate backend service and MySQL service. See `deploy/staging.env.example`;
set `SPRING_PROFILES_ACTIVE=staging` and the required `STAGING_DB_URL`, `STAGING_DB_USERNAME`,
`STAGING_DB_PASSWORD`, `STAGING_JWT_SECRET_B64`, `STAGING_VNP_TMN_CODE`,
`STAGING_VNP_HASH_SECRET`, `STAGING_VNP_RETURN_URL` values. Configure the staging Sandbox IPN URL
using the **actual** staging service domain. Build with `BOOKING_API_STAGING_URL` set to that domain.

Production (`prod`) continues using `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET_B64`,
`VNP_TMN_CODE`, `VNP_HASH_SECRET`, `VNP_PAY_URL`, `VNP_RETURN_URL`. CARD/QR demo payments are
disabled in this profile. VNPAY uses native VND: `vnp_Amount = amountVnd * 100`.
No deployment is performed by this patch; complete all gates in `PHASE5_FINAL_AUDIT_FIXES.md` first.
