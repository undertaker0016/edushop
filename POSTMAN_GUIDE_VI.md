# Hướng dẫn chạy EduShop và test bằng Postman

## 1. Cấu hình MySQL local

Ứng dụng local đang dùng:

- Database: `edushop`
- Username: `root`
- Password: rỗng
- Port: `3306`

Nếu MySQL Workbench/phpMyAdmin đã tạo database `edushop`, chỉ cần chạy backend. Flyway tự kiểm tra và áp dụng migration.

## 2. Tạo admin đầu tiên

Mở PowerShell tại `D:\Projects\edushop`:

```powershell
$env:EDUSHOP_BOOTSTRAP_ADMIN_EMAIL = 'admin@edushop.local'
$env:EDUSHOP_BOOTSTRAP_ADMIN_PASSWORD = 'Admin@123456789'
.\mvnw.cmd spring-boot:run
```

Hoặc chạy jar:

```powershell
.\mvnw.cmd package -DskipTests
java -jar .\target\edushop-0.0.1-SNAPSHOT.jar
```

Sau khi khởi động, truy cập:

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

Swagger UI và catalog công khai không cần đăng nhập. Chỉ các endpoint protected mới cần token.

## 3. Đăng nhập trong Swagger

1. Gọi `POST /api/v1/auth/login` với:

```json
{
  "email": "admin@edushop.local",
  "password": "Admin@123456789"
}
```

2. Copy trường `accessToken`.
3. Bấm **Authorize** ở góc phải Swagger.
4. Nhập `Bearer <accessToken>` rồi bấm **Authorize**.

Token có thời hạn 15 phút.

Ở ô chọn group trên đầu Swagger, chọn **All APIs** để xem toàn bộ endpoint trong một danh sách. Các group `Admin`, `Authentication`, `Courses`, `Instructor and Offerings`, `Checkout and Orders`, `Payments`, `Enrollments and Classroom`, `Reviews`, `Wishlists` và `Notifications` chỉ là bộ lọc tài liệu, không phải các bảng database bị thiếu.

## 4. Collection Postman cơ bản

Đặt biến:

```text
baseUrl = http://localhost:8080
adminToken = token nhận từ login admin
buyerToken = token nhận từ register/login buyer
instructorToken = token sau khi admin cấp instructor
```

Header cho request protected:

```text
Authorization: Bearer {{adminToken}}
Content-Type: application/json
```

### Authentication và user

`POST {{baseUrl}}/api/v1/auth/register`

```json
{
  "fullName": "Nguyen Van Buyer",
  "email": "buyer@example.com",
  "password": "BuyerPassword@123",
  "phone": "0900000000"
}
```

`POST {{baseUrl}}/api/v1/auth/login` trả về JWT. Buyer đăng ký không có role ứng dụng; buyer vẫn checkout được vì ownership dựa trên `user_id`.

`GET {{baseUrl}}/api/v1/auth/me` kiểm tra tài khoản hiện tại.

`GET {{baseUrl}}/api/v1/admin/users` xem danh sách tài khoản bằng `adminToken`.

Admin có thể tự tạo tài khoản quản trị hoặc giảng viên bằng `POST {{baseUrl}}/api/v1/admin/users`:

```json
{
  "fullName": "Nguyen Van Instructor",
  "email": "instructor@example.com",
  "password": "InstructorPassword@123",
  "phone": "0900000000",
  "role": "INSTRUCTOR"
}
```

Để tạo admin thứ hai, đổi `role` thành `ADMIN`. Endpoint này chỉ cho ADMIN gọi. Tài khoản admin đầu tiên vẫn phải bootstrap một lần vì database chưa có ai có quyền ADMIN.

`PATCH {{baseUrl}}/api/v1/admin/users/{id}/status?status=BANNED` khóa tài khoản; dùng `ACTIVE` để mở lại.

### Category/tag admin

`POST {{baseUrl}}/api/v1/admin/catalog/categories`

```json
{
  "name": "Java",
  "slug": "java",
  "icon": "code",
  "sortOrder": 1
}
```

`POST {{baseUrl}}/api/v1/admin/catalog/tags`

```json
{ "name": "Spring Boot" }
```

`GET {{baseUrl}}/api/v1/categories` và `GET {{baseUrl}}/api/v1/tags` là endpoint public.

### Cấp instructor và tạo course

1. Buyer đã đăng ký phải tồn tại trước.
2. Có thể dùng `POST /api/v1/admin/users` với `role=INSTRUCTOR` để tạo luôn user và instructor profile. Hoặc dùng flow cũ: admin gọi `POST {{baseUrl}}/api/v1/admin/instructors` cho một buyer đã tồn tại:

```json
{
  "email": "instructor@example.com",
  "fullName": "Nguyen Van Instructor"
}
```

3. Instructor đăng nhập lấy `instructorToken`.
4. Gọi `PUT {{baseUrl}}/api/v1/instructor/profile` để cập nhật hồ sơ.
5. Tạo course bằng `POST {{baseUrl}}/api/v1/instructor/courses`:

```json
{
  "title": "Spring Boot API",
  "slug": "spring-boot-api",
  "description": "Khóa học Spring Boot REST API",
  "thumbnailUrl": null,
  "categorySlugs": ["java"],
  "tags": ["Spring Boot"]
}
```

Sau đó dùng:

- `POST /api/v1/instructor/courses/{id}/publish`
- `POST /api/v1/instructor/courses/{id}/archive`
- `GET /api/v1/instructor/courses`

Course muốn publish phải có description và ít nhất một category active.

### Offering và session

`POST {{baseUrl}}/api/v1/instructor/offerings`

```json
{
  "courseId": 1,
  "offeringCode": "JAVA-2027-01",
  "cohortName": "Khai giảng tháng 1",
  "startDate": "2027-01-10",
  "endDate": "2027-02-10",
  "scheduleSummary": "Thứ bảy 09:00",
  "capacity": 20,
  "price": 1500000
}
```

Giá luôn là VND. Dùng `/open` sau khi course đã publish, sau đó tạo session:

```json
{
  "sessionNumber": 1,
  "title": "Spring MVC",
  "startsAt": "2027-01-10T09:00:00",
  "endsAt": "2027-01-10T11:00:00",
  "meetingProvider": "ZOOM",
  "meetingUrl": "https://zoom.us/j/example",
  "meetingPassword": "private-password"
}
```

Link họp không xuất hiện ở catalog public. Chỉ enrollment đã thanh toán mới xem được.

### Catalog public

- `GET /api/v1/categories`
- `GET /api/v1/tags`
- `GET /api/v1/courses?page=0&size=20&q=Spring&category=java&tag=Spring%20Boot`
- `GET /api/v1/courses/{slug}`
- `GET /api/v1/courses/{slug}/offerings`

### Checkout, coupon, order

Admin tạo coupon bằng `POST /api/v1/admin/coupons`:

```json
{
  "code": "WELCOME50",
  "discountType": "PERCENT",
  "discountValue": 50,
  "maxUses": 100,
  "maxUsesPerUser": 1
}
```

Buyer checkout bằng `POST {{baseUrl}}/api/v1/orders/checkout` và bắt buộc có header:

```text
Idempotency-Key: checkout-001
```

Body:

```json
{
  "offeringIds": [1],
  "couponCode": "WELCOME50"
}
```

Không gửi `price`, `subtotal`, `discountAmount` hoặc `totalAmount`; backend tự tính. Đơn tổng bằng 0 tự chuyển `PAID` và tạo enrollment. Đơn có phí giữ chỗ 15 phút.

- `GET /api/v1/orders`
- `GET /api/v1/orders/{id}`
- `DELETE /api/v1/orders/{id}` hủy đơn pending

### Payment test

Trong profile `test`, gateway `TEST_ONLY` chỉ phục vụ automated test. Môi trường chạy thật chưa có credentials nhà cung cấp nên payment trả `503`, không giả lập thanh toán thành công.

### Enrollment, classroom, review, wishlist, notification

- `GET /api/v1/enrollments`
- `GET /api/v1/enrollments/{id}/sessions` để xem link lớp riêng sau khi thanh toán
- `GET /api/v1/reviews/courses/{slug}` public
- `PUT /api/v1/reviews/courses/{courseId}` cần enrollment paid
- `GET/POST/DELETE /api/v1/wishlists...`
- `GET /api/v1/notifications`
- `PATCH /api/v1/notifications/{id}/read`
- Admin: `GET /api/v1/admin/enrollments` và `POST /api/v1/admin/enrollments/{id}/cancel`

Các bảng liên kết như `user_roles`, `course_categories`, `course_tags`, `coupon_redemptions` không có CRUD raw trực tiếp; chúng được cập nhật qua API nghiệp vụ để giữ FK, ownership, giá tiền và trạng thái nhất quán.
