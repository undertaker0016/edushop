# EduShop Backend Status

Ngày kiểm tra: 2026-10-28, profile `dev`.

## Đã hoàn thành

- Java 21, Spring Boot, MySQL, Flyway, Hibernate validate.
- JWT HS256, issuer, expiration, account status.
- Buyer không cần application role.
- Ownership order, enrollment, course, offering, classroom.
- Catalog, course, offering, sessions, coupon, checkout, order, payment attempt, enrollment, review, wishlist, notification.
- Admin dashboard và admin browse API.
- Instructor dashboard và danh sách học viên theo offering.
- Hồ sơ buyer, đổi mật khẩu, hồ sơ instructor.
- Cleanup reservation định kỳ.
- V6 chống active enrollment/provider transaction trùng và hỗ trợ payment reconciliation.
- DEV_SIMULATOR chỉ chạy profile dev.
- Adapter MoMo Sandbox theo request/IPN/query signature chính thức.

## Kết quả kiểm thử

- Maven: 22 tests, 0 failures, 0 errors.
- Package: đã compile/package thành công.
- Dev runtime: MySQL/Flyway/Hibernate/Tomcat/Swagger đã khởi động thành công.
- MoMo Sandbox: CHỜ CREDENTIALS và public HTTPS IPN.

## Trạng thái

- READY FOR FRONTEND DEVELOPMENT: Có.
- MOMO SANDBOX VERIFIED: Chưa, vì chưa có credentials.
- PRODUCTION PAYMENT READY: Chưa.

