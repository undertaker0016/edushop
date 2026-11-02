import { useMemo } from "react";
import { Link } from "react-router-dom";
import {
  Users,
  GraduationCap,
  BookOpen,
  ShoppingCart,
  CreditCard,
  CalendarCheck,
  UserCheck,
  Ticket,
  Bell,
  ListChecks,
  UserPlus,
  Clock,
  FileEdit,
  AlertTriangle,
} from "lucide-react";
import useApi from "../../hooks/useApi";
import { useAuth } from "../../contexts/AuthContext";
import Shell from "../../components/common/Shell";
import { ErrorState } from "../../components/common/State";
import { StatusBadge } from "../../components/ui";
import {
  AttentionList,
  BarList,
  DashboardHero,
  DashboardSkeleton,
  DashPanel,
  SkeletonBlock,
  StatCard,
  StatusBreakdown,
  TrendChart,
  compact,
  countOf,
  greeting,
  sum,
  todayLabel,
} from "../../components/dashboard";
import { date, money } from "../../utils/format";
import { label } from "../../utils/labels";

const TREND_DAYS = 14;

/** Groups PAID orders by local paid date for the last TREND_DAYS days. */
function paidTrend(orders = []) {
  const today = new Date();
  const days = Array.from({ length: TREND_DAYS }, (_, index) => {
    const day = new Date(today.getFullYear(), today.getMonth(), today.getDate() - (TREND_DAYS - 1 - index));
    return {
      key: day.toDateString(),
      label: day.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" }),
      short: String(day.getDate()),
      value: 0,
      count: 0,
    };
  });
  const byKey = Object.fromEntries(days.map((day) => [day.key, day]));
  orders
    .filter((order) => order.status === "PAID" && order.paidAt)
    .forEach((order) => {
      const bucket = byKey[new Date(order.paidAt).toDateString()];
      if (bucket) {
        bucket.value += Number(order.totalAmount || 0);
        bucket.count += 1;
      }
    });
  return days;
}

export default function AdminDashboard() {
  const { user } = useAuth();
  const data = useApi("/admin/dashboard", true);
  const orders = useApi("/admin/orders?size=100", true);
  const payments = useApi("/admin/payments?size=100", true);
  const applications = useApi("/admin/instructor-applications", true);

  const orderRows = orders.data?.items || [];
  const trend = useMemo(() => paidTrend(orderRows), [orderRows]);

  if (data.loading) return <DashboardSkeleton />;
  if (data.error)
    return (
      <main className="page">
        <ErrorState error={data.error} retry={data.reload} />
      </main>
    );

  const d = data.data || {};
  const totalCourses = sum(d.coursesByStatus);
  const totalOrders = sum(d.ordersByStatus);
  const succeededPayments = sum(d.paymentsByProvider, "succeeded");
  const succeededAmount = sum(d.paymentsByProvider, "amount");
  const pendingApplications = (applications.data || []).filter((row) =>
    ["PENDING", "REVIEWED"].includes(row.status),
  );
  const reconciliation = (payments.data?.items || []).filter(
    (row) => row.reconciliationRequired === true || row.reconciliationRequired === 1,
  );
  const trendTotal = trend.reduce((total, day) => total + day.value, 0);
  const trendCount = trend.reduce((total, day) => total + day.count, 0);
  const firstName = user?.fullName?.trim().split(/\s+/).at(-1) || "";

  return (
    <Shell title="Tổng quan quản trị" subtitle="Theo dõi dữ liệu thực tế và các việc cần xử lý của EduShop.">
      <DashboardHero
        eyebrow={todayLabel()}
        title={`${greeting()}, ${firstName}!`}
        description={`Hệ thống đang có ${d.openOfferings ?? 0} lớp mở bán và ${d.activeEnrollments ?? 0} lượt ghi danh đang học.`}
        actions={
          <>
            <Link className="btn" to="/admin/coupons">
              <Ticket size={16} /> Mã giảm giá
            </Link>
            <Link className="btn secondary" to="/admin/notifications">
              <Bell size={16} /> Gửi thông báo
            </Link>
            <Link className="btn secondary" to="/admin/reconciliation">
              <ListChecks size={16} /> Đối soát
            </Link>
          </>
        }
        aside={
          <div className="dash-highlight">
            <span>Doanh thu thực tế</span>
            <strong>{money(d.productionRevenue || 0)}</strong>
            <small>Chỉ tính thanh toán production đã xác nhận, không gồm giao dịch thử nghiệm.</small>
          </div>
        }
      />

      <div className="dash-stats">
        <StatCard icon={Users} tone="info" label="Người dùng" value={compact(d.users)} to="/admin/users" />
        <StatCard icon={GraduationCap} tone="violet" label="Giảng viên" value={compact(d.instructors)} to="/admin/instructors" />
        <StatCard
          icon={BookOpen}
          tone="navy"
          label="Khóa học"
          value={compact(totalCourses)}
          hint={`${countOf(d.coursesByStatus, "PUBLISHED")} đã xuất bản`}
          to="/admin/courses"
        />
        <StatCard
          icon={ShoppingCart}
          tone="warning"
          label="Đơn hàng"
          value={compact(totalOrders)}
          hint={`${countOf(d.ordersByStatus, "PAID")} đã thanh toán · ${countOf(d.ordersByStatus, "PENDING")} chờ`}
          to="/admin/orders"
        />
        <StatCard
          icon={CreditCard}
          tone="success"
          label="Thanh toán thành công"
          value={compact(succeededPayments)}
          hint={`Tổng ${money(succeededAmount)} (gồm thử nghiệm)`}
          to="/admin/payments"
        />
        <StatCard icon={CalendarCheck} tone="info" label="Lớp đang mở" value={compact(d.openOfferings)} to="/admin/offerings" />
        <StatCard icon={UserCheck} tone="success" label="Ghi danh đang học" value={compact(d.activeEnrollments)} to="/admin/enrollments" />
      </div>

      <div className="dash-grid">
        <DashPanel
          className="span-8"
          title="Đơn đã thanh toán"
          description={`${TREND_DAYS} ngày gần nhất, tính trên 100 đơn mới nhất (gồm thanh toán thử).`}
          action={{ to: "/admin/orders", label: "Xem đơn hàng" }}
        >
          {orders.loading ? (
            <SkeletonBlock lines={6} />
          ) : orders.error ? (
            <ErrorState error={orders.error} retry={orders.reload} />
          ) : (
            <>
              <div className="dash-trend-summary">
                <div>
                  <span>Giá trị</span>
                  <strong>{money(trendTotal)}</strong>
                </div>
                <div>
                  <span>Số đơn</span>
                  <strong>{trendCount}</strong>
                </div>
                <div>
                  <span>Trung bình / đơn</span>
                  <strong>{money(trendCount ? trendTotal / trendCount : 0)}</strong>
                </div>
              </div>
              <TrendChart points={trend} format={money} emptyText={`Chưa có đơn thanh toán trong ${TREND_DAYS} ngày qua.`} />
            </>
          )}
        </DashPanel>

        <DashPanel className="span-4" title="Cần xử lý" description="Các mục đang chờ quản trị viên.">
          {applications.loading || payments.loading ? (
            <SkeletonBlock lines={4} />
          ) : (
            <AttentionList
              doneText="Tuyệt vời! Không có việc nào đang chờ."
              items={[
                {
                  key: "applications",
                  icon: UserPlus,
                  tone: "violet",
                  title: "Hồ sơ giảng viên",
                  description: "Chờ duyệt hoặc chờ quyết định",
                  count: pendingApplications.length,
                  to: "/admin/instructor-applications",
                },
                {
                  key: "reconcile",
                  icon: AlertTriangle,
                  tone: "danger",
                  title: "Giao dịch cần đối soát",
                  description: "Thanh toán đến muộn hoặc chưa rõ kết quả",
                  count: reconciliation.length,
                  to: "/admin/reconciliation",
                },
                {
                  key: "pending-orders",
                  icon: Clock,
                  tone: "warning",
                  title: "Đơn chờ thanh toán",
                  description: "Đơn hàng đang giữ chỗ",
                  count: countOf(d.ordersByStatus, "PENDING"),
                  to: "/admin/orders",
                },
                {
                  key: "draft-courses",
                  icon: FileEdit,
                  tone: "info",
                  title: "Khóa học bản nháp",
                  description: "Chưa xuất bản lên danh mục",
                  count: countOf(d.coursesByStatus, "DRAFT"),
                  to: "/admin/courses",
                },
              ]}
            />
          )}
        </DashPanel>
      </div>

      <div className="dash-grid">
        <DashPanel className="span-4" title="Đơn hàng theo trạng thái" action={{ to: "/admin/orders", label: "Chi tiết" }}>
          <StatusBreakdown rows={d.ordersByStatus} emptyText="Chưa có đơn hàng." />
        </DashPanel>
        <DashPanel className="span-4" title="Khóa học theo trạng thái" action={{ to: "/admin/courses", label: "Chi tiết" }}>
          <StatusBreakdown rows={d.coursesByStatus} emptyText="Chưa có khóa học." />
        </DashPanel>
        <DashPanel className="span-4" title="Thanh toán theo cổng" action={{ to: "/admin/payments", label: "Chi tiết" }}>
          <BarList
            emptyText="Chưa có giao dịch thành công."
            rows={(d.paymentsByProvider || []).map((row) => ({
              key: row.provider,
              label: label(row.provider),
              value: Number(row.succeeded),
              display: `${row.succeeded} · ${money(row.amount)}`,
            }))}
          />
        </DashPanel>
      </div>

      <div className="dash-grid">
        <DashPanel className="span-7" title="Đơn hàng mới nhất" action={{ to: "/admin/orders", label: "Tất cả đơn" }}>
          {orders.loading ? (
            <SkeletonBlock lines={5} />
          ) : orderRows.length ? (
            <ul className="dash-list">
              {orderRows.slice(0, 6).map((row) => (
                <li className="dash-row" key={row.id}>
                  <span className="dash-icon" data-tone="navy" aria-hidden="true">
                    <ShoppingCart size={16} />
                  </span>
                  <div className="dash-row-main">
                    <strong title={row.orderNumber}>{row.orderNumber}</strong>
                    <small>
                      {row.buyerName || row.buyerEmail} · {date(row.createdAt)}
                    </small>
                  </div>
                  <div className="dash-row-end">
                    <strong>{money(row.totalAmount)}</strong>
                    <StatusBadge value={row.status} />
                  </div>
                </li>
              ))}
            </ul>
          ) : (
            <p className="dash-empty">Chưa có đơn hàng.</p>
          )}
        </DashPanel>

        <DashPanel className="span-5" title="Lớp được đăng ký nhiều" description="Theo số ghi danh đang học." action={{ to: "/admin/offerings", label: "Lớp học" }}>
          <BarList
            emptyText="Chưa có ghi danh."
            rows={(d.popularOfferings || []).slice(0, 6).map((row) => ({
              key: row.id,
              label: row.code,
              value: Number(row.enrollments),
              display: `${row.enrollments} học viên`,
            }))}
          />
        </DashPanel>
      </div>

      {pendingApplications.length > 0 && (
        <DashPanel title="Hồ sơ giảng viên mới" action={{ to: "/admin/instructor-applications", label: "Xem tất cả" }}>
          <ul className="dash-list">
            {pendingApplications.slice(0, 4).map((row) => (
              <li className="dash-row" key={row.id}>
                <span className="dash-icon" data-tone="violet" aria-hidden="true">
                  <UserPlus size={16} />
                </span>
                <div className="dash-row-main">
                  <Link to={`/admin/instructor-applications?applicationId=${row.id}`}>{row.fullName}</Link>
                  <small>
                    {row.expertise} · {row.email}
                  </small>
                </div>
                <div className="dash-row-end">
                  <small>{date(row.createdAt)}</small>
                  <span className="dash-count" data-tone={row.status === "PENDING" ? "warning" : "info"}>
                    {row.status === "PENDING" ? "Chờ duyệt" : "Đã xem"}
                  </span>
                </div>
              </li>
            ))}
          </ul>
        </DashPanel>
      )}
    </Shell>
  );
}
