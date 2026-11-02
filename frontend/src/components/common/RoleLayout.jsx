import ThemeToggle from "./ThemeToggle";
import { Loading } from "./State";
import Avatar from "./Avatar";
import { useState } from "react";
import { Link, NavLink, Navigate, Outlet, useLocation } from "react-router-dom";
import {
  GraduationCap,
  LayoutDashboard,
  BookOpen,
  Users,
  Calendar,
  Ticket,
  ShoppingCart,
  CreditCard,
  Bell,
  Star,
  Tags,
  User,
  LogOut,
  Menu,
  Settings,
  ListChecks,
} from "lucide-react";
import { useAuth } from "../../contexts/AuthContext";
import AccountBadge from "./AccountBadge";
import NotificationBell from "./NotificationBell";
import { Modal, IconButton, Breadcrumb } from "../ui";
const icons = {
  users: Users,
  instructors: GraduationCap,
  courses: BookOpen,
  offerings: Calendar,
  sessions: Calendar,
  coupons: Ticket,
  orders: ShoppingCart,
  payments: CreditCard,
  reconciliation: ListChecks,
  enrollments: Users,
  reviews: Star,
  notifications: Bell,
  categories: Tags,
  tags: Tags,
  profile: User,
};
export default function RoleLayout({ title, roles, items }) {
  const { user, loading, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const location = useLocation();
  if (loading) return <Loading />;
  if (!user) return <Navigate to="/auth/login" replace />;
  if (roles.length && !roles.some((role) => user.roles?.includes(role)))
    return <Navigate to="/403" replace />;
  const current = items
    .filter(
      (item) =>
        location.pathname === item.to ||
        location.pathname.startsWith(item.to + "/"),
    )
    .at(-1);
  const nav = (
    <nav className="side-nav">
      {items.map(({ to, label }) => {
        const Icon = icons[to.split("/").at(-1)] || LayoutDashboard;
        return (
          <NavLink
            end={to.split("/").length === 2}
            key={to}
            to={to}
            onClick={() => setOpen(false)}
          >
            <Icon size={18} />
            {label}
          </NavLink>
        );
      })}
    </nav>
  );
  return (
    <div className="dashboard">
      <a href="#main-content" className="skip-link">
        Đến nội dung chính
      </a>
      <aside>
        <Link className="brand" to="/">
          <GraduationCap size={26} />
          EduShop
        </Link>
        <strong>{title}</strong>
        {nav}
        <div className="sidebar-account">
          <span>{user.fullName}</span>
          <AccountBadge />
        </div>
        <button className="btn secondary" onClick={logout}>
          <LogOut size={16} />
          Đăng xuất
        </button>
      </aside>
      <section className="dashboard-main">
        <header className="workspace-topbar">
          <div className="flex items-center gap-3">
            <NotificationBell />
            <span className="lg:hidden">
              <IconButton label="Mở menu quản lý" onClick={() => setOpen(true)}>
                <Menu size={18} />
              </IconButton>
            </span>
            <Breadcrumb>
              <Link to="/">EduShop</Link> / {title} /{" "}
              {current?.label || "Quản lý"}
            </Breadcrumb>
          </div>
          <div className="workspace-account">
            <ThemeToggle />
            <div className="workspace-identity">
              <Avatar user={user} />
              <div className="grid gap-1">
                <span className="hidden max-w-48 truncate text-sm font-semibold sm:inline">
                  {user.fullName}
                </span>
                <AccountBadge />
              </div>
            </div>
          </div>
        </header>
        <div id="main-content" tabIndex={-1}>
          <Outlet />
        </div>
      </section>
      <Modal dismissible title={title} open={open} onClose={() => setOpen(false)}>
        <div className="rounded-lg bg-brand-900 p-3">{nav}</div>
      </Modal>
    </div>
  );
}
