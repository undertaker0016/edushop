# Runbook

## Local

1. Chạy MySQL/Laragon và tạo database `edushop`.
2. Đặt Java 21.
3. Profile dev mặc định dùng local MySQL.
4. Chạy:

```powershell
$env:EDUSHOP_DB_USERNAME='root'
$env:EDUSHOP_DB_PASSWORD=''
.mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

5. Mở Swagger: http://localhost:8080/swagger-ui/index.html.
6. Import Postman collection.

## ADMIN

Thiết lập biến môi trường bootstrap một lần:

```powershell
$env:EDUSHOP_BOOTSTRAP_ADMIN_EMAIL='admin@example.test'
$env:EDUSHOP_BOOTSTRAP_ADMIN_PASSWORD='mat-khau-tu-tao-dai-it-nhat-12-ky-tu'
```

Bootstrap không nâng quyền tài khoản đã tồn tại và không ghi password vào source.

## Production

Bắt buộc có `EDUSHOP_DB_URL`, `EDUSHOP_DB_USERNAME`, `EDUSHOP_DB_PASSWORD`, `EDUSHOP_JWT_SECRET_BASE64` và `EDUSHOP_CORS_ALLOWED_ORIGINS`. Giữ `ddl-auto=validate`. Không bật DEV_SIMULATOR hoặc MOMO_SANDBOX.

## MoMo Sandbox

Cấu hình `EDUSHOP_PAYMENT_MODE=MOMO_SANDBOX`, partner code, access key, secret key, redirect URL và HTTPS IPN URL. Không dùng credentials production ở local. MoMo IPN phải được gọi server-to-server và xác minh HMAC theo tài liệu provider.

