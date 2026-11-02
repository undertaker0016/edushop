import { Link } from "react-router-dom";
import {
  BookOpen,
  ShoppingCart,
  Heart,
  UserRound,
  ArrowRight,
  CalendarDays,
  Compass,
  Trophy,
  Wallet,
  Clock,
  CalendarClock,
} from "lucide-react";
import useApi from "../../hooks/useApi";
import { useAuth } from "../../contexts/AuthContext";
import { useCart } from "../../contexts/CartContext";
import { useWishlist } from "../../contexts/WishlistContext";
import Shell from "../../components/common/Shell";
import Avatar from "../../components/common/Avatar";
import AccountBadge from "../../components/common/AccountBadge";
import CourseImage from "../../components/common/CourseImage";
import { ErrorState } from "../../components/common/State";
import { StatusBadge } from "../../components/ui";
import {
  AttentionList,
  DashboardHero,
  DashPanel,
  DateChip,
  ProgressBar,
  SkeletonBlock,
  StatCard,
  daysUntil,
  greeting,
  todayLabel,
} from "../../components/dashboard";
import { date, money } from "../../utils/format";

/** Timeline progress of a class from its start/end dates. */
function classProgress(row) {
  const start = daysUntil(row.startDate);
  const end = daysUntil(row.endDate);
  if (start === null) return { percent: 0, text: "Chưa có lịch khai giảng" };
  if (start > 0)
    return { percent: 0, text: start === 1 ? "Khai giảng ngày mai" : `Khai giảng sau ${start} ngày` };
  if (start === 0) return { percent: 2, text: "Khai giảng hôm nay" };
  if (end === null) return { percent: 50, text: "Đang diễn ra" };
  if (end < 0) return { percent: 100, text: "Đã kết thúc lịch học" };
  const total = end - start || 1;
  const percent = (-start / total) * 100;
  return { percent, text: `Đã qua ${Math.round(percent)}% thời gian · còn ${end} ngày` };
}

export default function BuyerHome() {
  const { user } = useAuth();
  const { count } = useCart();
  const { rows: wishes } = useWishlist();
  const classes = useApi("/enrollments", true),
    orders = useApi("/orders?size=100", true);
  const classRows = classes.data || [];
  const orderRows = orders.data || [];
  const active = classRows
    .filter((row) => row.status === "ACTIVE")
    .sort((a, b) => String(a.startDate).localeCompare(String(b.startDate)));
  const completed = classRows.filter((row) => row.status === "COMPLETED");
  const pending = orderRows.filter((row) => row.status === "PENDING");
  const spent = orderRows
    .filter((row) => row.status === "PAID")
    .reduce((total, row) => total + Number(row.totalAmount || 0), 0);
  const nextClass = active
    .map((row) => ({ ...row, days: daysUntil(row.startDate) }))
    .filter((row) => row.days !== null && row.days >= 0)
    .sort((a, b) => a.days - b.days)[0];
  const firstName = user?.fullName?.trim().split(/\s+/).at(-1) || "";

  return (
    <Shell title="Không gian của tôi" subtitle="Theo dõi việc học, các lớp đã lưu và những đơn cần hoàn tất.">
      <DashboardHero
        eyebrow={todayLabel()}
        title={`${greeting()}, ${firstName}! Hôm nay bạn muốn học gì?`}
        description={
          nextClass
            ? `Lớp "${nextClass.courseTitle}" ${nextClass.days === 0 ? "khai giảng hôm nay" : `khai giảng sau ${nextClass.days} ngày`}. Đừng quên kiểm tra lịch học nhé.`
            : "Theo dõi lớp học và tiếp tục hành trình của bạn tại đây."
        }
        actions={
          <>
            <Link className="btn" to="/courses">
              <Compass size={16} /> Khám phá khóa học
            </Link>
            <Link className="btn secondary" to="/buyer/classroom">
              <CalendarDays size={16} /> Lịch học của tôi
            </Link>
          </>
        }
        aside={
          <button
            type="button"
            className="buyer-profile w-full"
            onClick={() => window.dispatchEvent(new Event("edushop:profile"))}
          >
            <Avatar user={user} size="size-12" />
            <span className="min-w-0 flex-1">
              <strong className="truncate">{user.fullName}</strong>
              <small className="truncate">{user.email}</small>
              <AccountBadge />
            </span>
            <UserRound size={18} aria-hidden="true" />
          </button>
        }
      />

      <div className="dash-stats">
        <StatCard icon={BookOpen} tone="info" label="Lớp đang học" value={classes.loading ? "…" : active.length} to="/buyer/classroom" />
        <StatCard icon={Trophy} tone="success" label="Lớp đã hoàn thành" value={classes.loading ? "…" : completed.length} to="/buyer/classroom" />
        <StatCard icon={ShoppingCart} tone="warning" label="Giỏ hàng" value={count} hint={count ? "Hoàn tất đăng ký ngay" : undefined} to="/buyer/cart" />
        <StatCard icon={Heart} tone="danger" label="Khóa yêu thích" value={wishes.length} to="/buyer/wishlist" />
        <StatCard icon={Wallet} tone="navy" label="Đã đầu tư cho học tập" value={orders.loading ? "…" : money(spent)} to="/buyer/orders" />
      </div>

      {pending.length > 0 && (
        <div className="mb-5">
          <AttentionList
            items={[
              {
                key: "pending",
                icon: Clock,
                tone: "warning",
                title: `Bạn có ${pending.length} đơn đang chờ thanh toán`,
                description: "Hoàn tất thanh toán để giữ chỗ trong lớp học.",
                count: pending.length,
                to: "/buyer/orders",
              },
            ]}
          />
        </div>
      )}

      <div className="dash-grid">
        <DashPanel className="span-7" title="Lớp học của bạn" action={{ to: "/buyer/classroom", label: "Xem tất cả" }}>
          {classes.loading ? (
            <SkeletonBlock lines={5} />
          ) : classes.error ? (
            <ErrorState error={classes.error} retry={classes.reload} />
          ) : active.length ? (
            <ul className="dash-list">
              {active.slice(0, 4).map((row) => {
                const progress = classProgress(row);
                return (
                  <li className="dash-row" key={row.id}>
                    <DateChip value={row.startDate} />
                    <div className="dash-row-main">
                      <Link to={`/buyer/classroom?enrollmentId=${row.id}`} title={row.courseTitle}>
                        {row.courseTitle}
                      </Link>
                      <div className="dash-row-meta">
                        <span>{row.offeringCode}</span>
                        <span>
                          <CalendarClock size={12} /> {date(row.startDate)} – {date(row.endDate)}
                        </span>
                      </div>
                      <div className="mt-2 grid gap-1">
                        <ProgressBar value={progress.percent} label={`Tiến độ lớp ${row.courseTitle}`} />
                        <small className="text-xs text-slate-500">{progress.text}</small>
                      </div>
                    </div>
                    <Link
                      className="dash-panel-link"
                      to={`/buyer/classroom?enrollmentId=${row.id}`}
                      aria-label={`Lịch và phòng học ${row.courseTitle}`}
                    >
                      Vào lớp <ArrowRight size={14} />
                    </Link>
                  </li>
                );
              })}
            </ul>
          ) : (
            <p className="dash-empty">
              Bạn chưa đăng ký lớp. <Link to="/courses">Khám phá khóa học</Link>
            </p>
          )}
        </DashPanel>

        <DashPanel className="span-5" title="Đơn gần đây" action={{ to: "/buyer/orders", label: "Lịch sử" }}>
          {orders.loading ? (
            <SkeletonBlock lines={5} />
          ) : orders.error ? (
            <ErrorState error={orders.error} retry={orders.reload} />
          ) : orderRows.length ? (
            <ul className="dash-list">
              {orderRows.slice(0, 5).map((row) => (
                <li className="dash-row" key={row.id}>
                  <span className="dash-icon" data-tone="navy" aria-hidden="true">
                    <ShoppingCart size={16} />
                  </span>
                  <div className="dash-row-main">
                    <Link to={`/buyer/orders/${row.id}`} title={row.orderNumber}>
                      {row.orderNumber}
                    </Link>
                    <small>{date(row.createdAt)}</small>
                  </div>
                  <div className="dash-row-end">
                    <strong>{money(row.totalAmount)}</strong>
                    <StatusBadge value={row.status} />
                  </div>
                </li>
              ))}
            </ul>
          ) : (
            <p className="dash-empty">Chưa có đơn hàng. Các lớp bạn thêm sẽ xuất hiện trong giỏ hàng trước khi đăng ký.</p>
          )}
        </DashPanel>
      </div>

      <DashPanel title="Khóa học bạn yêu thích" action={{ to: "/buyer/wishlist", label: "Xem tất cả" }}>
        {wishes.length ? (
          <div className="dash-course-grid">
            {wishes.slice(0, 4).map((course) => (
              <Link className="dash-course" key={course.courseId} to={`/courses/${course.slug}`}>
                <div className="dash-course-art">
                  <CourseImage src={course.thumbnailUrl} alt={course.title} loading="lazy" />
                </div>
                <div className="dash-course-body">
                  <strong>{course.title}</strong>
                  <small>Đã lưu {date(course.addedAt)}</small>
                </div>
              </Link>
            ))}
          </div>
        ) : (
          <p className="dash-empty">
            Lưu khóa học bạn quan tâm để xem lại nhanh tại đây. <Link to="/courses">Tìm khóa học</Link>
          </p>
        )}
      </DashPanel>
    </Shell>
  );
}
