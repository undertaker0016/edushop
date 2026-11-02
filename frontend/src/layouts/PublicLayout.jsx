import { useCart } from "../contexts/CartContext";
import ThemeToggle from "../components/common/ThemeToggle";
import Avatar from "../components/common/Avatar";
import CourseSearch from "../components/common/CourseSearch";
import { useEffect, useState } from "react";
import { Link, NavLink, Outlet, useNavigate } from "react-router-dom";
import {
  GraduationCap,
  Heart,
  ShoppingCart,
  Menu,
  ChevronDown,
  User,
  LogOut,
  LayoutDashboard,
} from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import { useWishlist } from "../contexts/WishlistContext";
import { api } from "../api/client";
import Account from "../pages/auth/Account";
import AccountBadge from "../components/common/AccountBadge";
import NotificationBell from "../components/common/NotificationBell";
import { Modal, IconButton } from "../components/ui";
export default function PublicLayout({ children }) {
  const { user, logout } = useAuth();
  const { rows } = useWishlist();
  const { count: cartCount } = useCart();
  const navigate = useNavigate();
  const [categories, setCategories] = useState([]),
    [profileOpen, setProfileOpen] = useState(false),
    [menuOpen, setMenuOpen] = useState(false);
  const workspace = user?.roles?.includes("ADMIN")
    ? "/admin"
    : user?.roles?.includes("INSTRUCTOR")
      ? "/instructor"
      : "/buyer";
  useEffect(() => {
    let active = true;
    api
      .get("/categories")
      .then((value) => {
        if (active) setCategories(value);
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, []);
  useEffect(() => {
    const open = () => setProfileOpen(true);
    window.addEventListener("edushop:profile", open);
    return () => window.removeEventListener("edushop:profile", open);
  }, []);
  return (
    <div className="app">
      <a href="#main-content" className="skip-link">
        Đến nội dung chính
      </a>
      <header className="topbar">
        <Link className="brand" to="/" aria-label="EduShop">
          <GraduationCap className="text-blue-600" size={28} />
          <span className="hidden sm:inline">EduShop</span>
        </Link>
        <CourseSearch />

        <div className="hidden items-center gap-2 2xl:flex">
          <Link to="/contact" className="hidden px-3 text-slate-600 2xl:block">
            Giảng dạy tại EduShop
          </Link>
          <NavLink to="/courses" className="px-3 text-slate-600">
            Khám phá khóa học
          </NavLink>
          {user && (
            <Link className="px-3 text-slate-600" to="/buyer/classroom">
              Lớp học của tôi
            </Link>
          )}
        </div>
        <nav aria-label="Tài khoản" className="navbar-actions">
          <ThemeToggle />
          {user ? (
            <>
              <Link
                to="/buyer/wishlist"
                aria-label={`Yêu thích, ${rows.length} khóa học`}
                title="Yêu thích"
                className="navbar-icon relative"
              >
                <Heart size={19} />
                {rows.length > 0 && (
                  <span className="absolute -right-1 -top-1 rounded-full bg-blue-600 px-1 text-[10px] text-white">
                    {rows.length}
                  </span>
                )}
              </Link>
              <Link
                to="/buyer/cart"
                aria-label={`Giỏ hàng, ${cartCount} lớp học`}
                title="Giỏ hàng"
                className="navbar-icon relative"
              >
                <ShoppingCart size={19} />
                {cartCount > 0 && (
                  <span className="absolute -right-1 -top-1 rounded-full bg-blue-600 px-1 text-[10px] text-white">
                    {cartCount}
                  </span>
                )}
              </Link>
              <NotificationBell />
              <details className="account-menu">
                <summary>
                  <Avatar user={user} />
                  <span className="hidden text-xs sm:grid">
                    <strong className="max-w-24 truncate">
                      {user.fullName}
                    </strong>
                    <AccountBadge />
                  </span>
                  <ChevronDown size={14} className="hidden sm:block" />
                </summary>
                <div
                  onClick={(event) => {
                    event.currentTarget.parentElement.removeAttribute("open");
                  }}
                >
                  <Link to={workspace}>
                    <LayoutDashboard size={16} />
                    Không gian của tôi
                  </Link>
                  <button className="link" onClick={() => setProfileOpen(true)}>
                    <User size={16} />
                    Thông tin cá nhân
                  </button>
                  <Link to="/buyer/orders">
                    <ShoppingCart size={16} />
                    Đơn hàng
                  </Link>
                  <button
                    className="link"
                    onClick={() => {
                      logout();
                      navigate("/");
                    }}
                  >
                    <LogOut size={16} />
                    Đăng xuất
                  </button>
                </div>
              </details>
            </>
          ) : (
            <>
              <Link to="/auth/login">Đăng nhập</Link>
              <Link to="/auth/register">Đăng ký</Link>
            </>
          )}
          <span className="2xl:hidden">
            <IconButton label="Mở menu" onClick={() => setMenuOpen(true)}>
              <Menu size={19} />
            </IconButton>
          </span>
        </nav>
      </header>
      <Modal
        title="Điều hướng"
        dismissible
        open={menuOpen}
        onClose={() => setMenuOpen(false)}
      >
        <nav className="grid gap-3" onClick={() => setMenuOpen(false)}>
          <Link to="/">Trang chủ</Link>
          <Link to="/courses">Khám phá khóa học</Link>
          {user && (
            <>
              <Link to={workspace}>Không gian của tôi</Link>
              <Link to="/buyer/classroom">Lớp học của tôi</Link>
              <Link to="/buyer/orders">Đơn hàng</Link>
            </>
          )}
        </nav>
      </Modal>
      {categories.length > 0 && (
        <nav className="category-nav" aria-label="Danh mục">
          {categories.map((item) => (
            <Link
              key={item.slug}
              to={`/courses?category=${encodeURIComponent(item.slug)}`}
            >
              {item.name}
            </Link>
          ))}
        </nav>
      )}
      <div id="main-content" tabIndex={-1}>
        {children || <Outlet />}
      </div>
      <Modal
        title="Thông tin cá nhân"
        open={profileOpen}
        onClose={() => setProfileOpen(false)}
      >
        <Account />
      </Modal>
      <footer className="site-footer">
        <div className="footer-grid">
          <section>
            <Link className="brand" to="/">
              <GraduationCap size={24} />
              EduShop
            </Link>
            <p>
              Khám phá kiến thức mới qua các lớp trực tuyến có lịch học rõ ràng.
            </p>
          </section>
          <section>
            <h2>Khám phá</h2>
            <Link to="/courses">Tất cả khóa học</Link>
            {categories.map((item) => (
              <Link key={item.slug} to={`/courses?category=${item.slug}`}>
                {item.name}
              </Link>
            ))}
          </section>
          <section>
            <h2>Việc học của bạn</h2>
            <Link to="/buyer/classroom">Lớp học</Link>
            <Link to="/buyer/orders">Đơn hàng</Link>
            <Link to="/buyer/wishlist">Yêu thích</Link>
          </section>
          <section>
            <h2>Tài khoản</h2>
            <Link to="/contact">Trở thành giảng viên</Link>
            <Link to={user ? workspace : "/auth/login"}>
              Không gian cá nhân
            </Link>
            <Link to="/auth/forgot-password">Khôi phục mật khẩu</Link>
            <p>Chọn khóa học → Chọn lịch lớp → Đăng ký và bắt đầu học.</p>
          </section>
        </div>
        <div className="footer-bottom">
          © {new Date().getFullYear()} EduShop · Học tập theo cách của bạn.
        </div>
      </footer>
    </div>
  );
}
