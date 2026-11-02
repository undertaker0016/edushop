import RoleLayout from "../components/common/RoleLayout";

const items = [
  { to: "/admin", label: "Tổng quan" },
  { to: "/admin/users", label: "Người dùng" },
  { to: "/admin/instructors", label: "Giảng viên" },
  { to: "/admin/instructor-applications", label: "Liên hệ giảng viên" },
  { to: "/admin/categories", label: "Danh mục" },
  { to: "/admin/tags", label: "Thẻ" },
  { to: "/admin/courses", label: "Khóa học" },
  { to: "/admin/offerings", label: "Lớp mở bán" },
  { to: "/admin/sessions", label: "Lịch học" },
  { to: "/admin/coupons", label: "Mã giảm giá" },
  { to: "/admin/orders", label: "Đơn hàng" },
  { to: "/admin/payments", label: "Thanh toán" },
  { to: "/admin/reconciliation", label: "Đối soát" },
  { to: "/admin/enrollments", label: "Ghi danh" },
  { to: "/admin/reviews", label: "Đánh giá" },
  { to: "/admin/notifications", label: "Thông báo" },
];

export default function AdminLayout() {
  return <RoleLayout title="Quản trị" roles={["ADMIN"]} items={items} />;
}
