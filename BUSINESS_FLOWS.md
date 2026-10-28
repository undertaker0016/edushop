# Business Flows

## Checkout

Buyer gửi danh sách offering và Idempotency-Key. Backend khóa user/offering, đọc giá từ database, kiểm tra trạng thái, capacity, enrollment và coupon. Order trả phí giữ chỗ 15 phút. Order 0 đồng chuyển PAID và tạo enrollment ngay.

## Payment

Payment attempt được lưu trước khi gọi gateway. Provider callback phải xác minh signature, partner/reference, amount, currency và transaction ID. Callback thành công lặp lại là idempotent. Callback đến sau hạn giữ chỗ được ghi nhận reconciliation và không tự cấp classroom.

## Enrollment/classroom

Enrollment chỉ được tạo từ order PAID. Classroom yêu cầu đúng owner, enrollment ACTIVE, order PAID, offering chưa CANCELLED và chưa hết hạn. Enrollment CANCELLED/EXPIRED không còn xem link.

## Course/offering/session

Course phải published trước khi mở offering. Offering phải có session hợp lệ, session nằm trong khoảng offering. Offering có thể OPEN, CLOSED, COMPLETED hoặc CANCELLED theo điều kiện nghiệp vụ. Không xóa lịch sử giao dịch.

## Notifications

Thanh toán, enrollment, thay đổi lịch và hủy enrollment tạo notification. Buyer có list, unread count, mark read và mark all read.

