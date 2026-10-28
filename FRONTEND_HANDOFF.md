# Frontend Handoff

Base URL: `http://localhost:8080`.

Import [postman/EduShop.postman_collection.json](postman/EduShop.postman_collection.json). Chạy collection theo thứ tự. Đặt `adminToken` bằng token ADMIN tạm thời hoặc dùng admin login nếu có mật khẩu local.

JWT trả về từ register/login nằm ở `accessToken`. Buyer/instructor token được collection tự lưu.

Giá, subtotal, discount, total và payment status do backend quyết định. Frontend chỉ hiển thị kết quả.

Các màn hình có API:

- Catalog/course detail/offering schedule.
- Register/login/profile/password.
- Orders/checkout/payment/enrollments/classroom.
- Wishlist/reviews/notifications.
- Instructor dashboard/course/offering/session/enrollments.
- Admin dashboard/users/instructors/categories/tags/coupons/orders/payments/enrollments/reviews.

DEV_SIMULATOR trả payment attempt không có payment URL thật. Gọi dev result chỉ trong profile dev. MoMo cần credentials và HTTPS IPN.

Date/time request dùng ISO local datetime theo timezone Asia/Ho_Chi_Minh. Money dùng decimal JSON, không dùng floating point trong logic frontend.

