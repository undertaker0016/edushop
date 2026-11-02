import { ShieldCheck, GraduationCap, User } from "lucide-react";
import { useAuth } from "../../contexts/AuthContext";
export default function AccountBadge() {
  const { user } = useAuth();
  if (!user) return null;
  const roles = user.roles || [];
  const kind = roles.includes("ADMIN")
    ? "admin"
    : roles.includes("INSTRUCTOR")
      ? "instructor"
      : "buyer";
  const name = {
    admin: "Quản trị viên",
    instructor: "Giảng viên",
    buyer: "Học viên",
  }[kind];
  return (
    <span
      className={`account-badge ${kind}`}
      title={`Tài khoản: ${user.email}`}
    >
      {kind === "admin" ? (
        <ShieldCheck size={12} />
      ) : kind === "instructor" ? (
        <GraduationCap size={12} />
      ) : (
        <User size={12} />
      )}{" "}
      {name}
    </span>
  );
}
