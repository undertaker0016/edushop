import RoleLayout from "../components/common/RoleLayout";

export default function InstructorLayout() {
  return (
    <RoleLayout
      title="Giảng viên"
      roles={["INSTRUCTOR", "ADMIN"]}
      items={[
        { to: "/instructor", label: "Tổng quan" },
        { to: "/instructor/profile", label: "Hồ sơ" },
        { to: "/instructor/courses", label: "Khóa học" },
        { to: "/instructor/offerings", label: "Lớp mở bán" },
        { to: "/instructor/sessions", label: "Lịch học" },
        { to: "/instructor/enrollments", label: "Học viên" },
        { to: "/instructor/notifications", label: "Thông báo lớp học" },
      ]}
    />
  );
}
