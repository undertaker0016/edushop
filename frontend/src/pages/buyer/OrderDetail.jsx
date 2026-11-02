import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { CreditCard, RefreshCw } from "lucide-react";
import useApi from "../../hooks/useApi";
import { api } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import Shell from "../../components/common/Shell";
import DataTable from "../../components/tables/DataTable";
import { Button, StatusBadge } from "../../components/ui";
import { Loading, ErrorState } from "../../components/common/State";
import { money, date } from "../../utils/format";
import { checkoutUrl, providerLabel } from "../../utils/payment";
export default function OrderDetail() {
  const { id } = useParams();
  const { token } = useAuth();
  const order = useApi(`/orders/${id}`, true),
    payments = useApi(`/payments/orders/${id}`, true);
  const classes = useApi("/enrollments", true);
  const configuration = useApi("/payments/config", true);
  const key = useMemo(() => crypto.randomUUID(), [id, payments.data?.length]);
  const [busy, setBusy] = useState(false),
    [checking, setChecking] = useState(false),
    [error, setError] = useState("");
  async function refreshStatus() {
    setChecking(true);
    setError("");
    try {
      const pending = (payments.data || []).find(
        (row) => row.status === "PENDING" && row.provider === "ZALOPAY_SANDBOX",
      );
      if (pending)
        await api.post(`/payments/${pending.id}/verify`, undefined, token);
      await Promise.all([order.reload(), payments.reload(), classes.reload()]);
    } catch (error) {
      setError(error.message);
    } finally {
      setChecking(false);
    }
  }
  async function pay() {
    setBusy(true);
    setError("");
    try {
      const pending = (payments.data || []).find(
        (row) => row.status === "PENDING",
      );
      const result = pending
        ? await api.post(`/payments/${pending.id}/resume`, undefined, token)
        : await api.post(`/payments/orders/${id}/initiate`, undefined, token, {
            "Idempotency-Key": key,
          });
      await payments.reload();
      if (result.checkoutUrl) {
        window.location.assign(
          checkoutUrl(result.checkoutUrl, result.provider),
        );
      }
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  if (order.loading) return <Loading />;
  if (order.error)
    return <ErrorState error={order.error} retry={order.reload} />;
  const row = order.data;
  return (
    <Shell
      title={`Đơn hàng ${row.orderNumber}`}
      subtitle={`Tạo lúc ${date(row.createdAt)}`}
      actions={
        <Button variant="secondary" busy={checking} onClick={refreshStatus}>
          <RefreshCw size={16} />
          Kiểm tra trạng thái
        </Button>
      }
    >
      <Link className="back" to="/buyer/orders">
        ← Đơn hàng của tôi
      </Link>
      {error && <div className="alert">{error}</div>}
      {configuration.data?.sandbox && (
        <p className="success">
          {providerLabel(configuration.data?.provider)} sandbox: môi trường thử
          nghiệm, không dùng ví hoặc tiền thật.
        </p>
      )}
      {configuration.data?.provider === "DEV_SIMULATOR" && (
        <p className="alert">
          Hệ thống đang dùng thanh toán mô phỏng cho đồ án; chưa kết nối cổng
          sandbox.
        </p>
      )}
      {configuration.data?.publicDemo && (
        <p className="text-sm text-slate-500">
          Đang dùng tài khoản sandbox mẫu công khai. Sau khi thanh toán thử,
          nhấn “Kiểm tra trạng thái” để truy vấn ZaloPay; không nhận callback
          bằng khóa mẫu.
        </p>
      )}
      {configuration.error && (
        <ErrorState error={configuration.error} retry={configuration.reload} />
      )}
      <section className="panel mb-5">
        <StatusBadge value={row.status} />
        <dl className="record-fields">
          <div>
            <dt>Tạm tính</dt>
            <dd>{money(row.subtotal)}</dd>
          </div>
          <div>
            <dt>Giảm giá</dt>
            <dd>{money(row.discountAmount)}</dd>
          </div>
          <div>
            <dt>Tổng tiền</dt>
            <dd className="text-xl font-bold">{money(row.totalAmount)}</dd>
          </div>
        </dl>
        <div className="mt-4">
          {row.status === "PENDING" && Number(row.totalAmount) > 0 && (
            <Button
              busy={busy}
              disabled={
                payments.loading ||
                !!payments.error ||
                configuration.loading ||
                (!configuration.data?.online &&
                  configuration.data?.provider !== "DEV_SIMULATOR")
              }
              onClick={pay}
            >
              <CreditCard size={16} />
              {configuration.data?.online
                ? `Thanh toán qua ${providerLabel(configuration.data?.provider)}`
                : "Tạo giao dịch mô phỏng"}
            </Button>
          )}
          {row.status === "PAID" && (
            <Link className="btn" to="/buyer/classroom">
              Vào lớp học
            </Link>
          )}
        </div>
      </section>
      <h2 className="mb-3">Nội dung đơn hàng</h2>
      <DataTable
        rows={row.items}
        columns={[
          { key: "title", label: "Lớp học" },
          {
            key: "unitPrice",
            label: "Học phí",
            render: (item) => money(item.unitPrice),
          },
        ]}
      />
      {row.status === "PAID" && (
        <section className="panel my-5">
          <h2>Lớp học đã đăng ký</h2>
          {(classes.data || [])
            .filter((item) =>
              row.items.some(
                (orderItem) => orderItem.offeringId === item.offeringId,
              ),
            )
            .map((item) => (
              <article
                className="my-3 flex flex-wrap justify-between gap-3"
                key={item.id}
              >
                <div>
                  <strong>
                    {item.courseTitle} · {item.offeringCode}
                  </strong>
                  <p className="text-xs text-slate-500">
                    Đăng ký: {date(item.enrolledAt)} · Khai giảng:{" "}
                    {date(item.startDate)}
                  </p>
                </div>
                <Link
                  className="btn secondary"
                  to={`/buyer/classroom?enrollmentId=${item.id}`}
                >
                  Xem ngày và giờ học
                </Link>
              </article>
            ))}
        </section>
      )}
      <h2 className="mt-6 mb-3">Lịch sử thanh toán</h2>
      {payments.error ? (
        <ErrorState error={payments.error} retry={payments.reload} />
      ) : (
        <DataTable
          rows={payments.data || []}
          columns={[
            { key: "id", label: "Mã" },
            { key: "provider", label: "Cổng thanh toán" },
            { key: "status", label: "Trạng thái" },
            {
              key: "amount",
              label: "Số tiền",
              render: (item) => money(item.amount),
            },
          ]}
        />
      )}
      <p className="mt-3 text-xs text-slate-500">
        Trạng thái được xác nhận từ máy chủ. Không tự đánh dấu đã thanh toán.
      </p>
    </Shell>
  );
}
