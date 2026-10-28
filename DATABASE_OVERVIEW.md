# Database Overview

V1-V5 tạo 19 bảng ban đầu và V3 có thêm coupon_redemptions, tổng 20 bảng ứng dụng. V6 bổ sung cột/index/constraint, không tạo bảng mới.

JPA dùng cho user, role, instructor, category, tag, course, offering và session. JdbcTemplate dùng cho checkout/payment/enrollment và truy vấn tổng hợp để kiểm soát lock, snapshot giá, idempotency và transaction.

V6:

- active_offering_id generated column + unique để ngăn active enrollment trùng.
- provider + provider_transaction_id unique.
- checkout_url, reconciliation_required, result_note cho payment.
- is_hidden cho review.
- index order expiry.

