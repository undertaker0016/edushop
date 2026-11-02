import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Check, CreditCard } from "lucide-react";
import { api } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import { useCart } from "../../contexts/CartContext";
import useApi from "../../hooks/useApi";
import Shell from "../../components/common/Shell";
import CourseImage from "../../components/common/CourseImage";
import { Button, Modal, StatusBadge } from "../../components/ui";
import { Loading, ErrorState } from "../../components/common/State";
import { money, date } from "../../utils/format";
export default function Checkout() {
  const [params] = useSearchParams();
  const raw = params.get("offeringIds") || params.get("offeringId") || "";
  const ids = useMemo(
    () => [
      ...new Set(
        raw
          .split(",")
          .map(Number)
          .filter((id) => Number.isInteger(id) && id > 0),
      ),
    ],
    [raw],
  );
  const joined = ids.join(",");
  const { token } = useAuth();
  const { remove } = useCart();
  const [details, setDetails] = useState([]),
    [loading, setLoading] = useState(true),
    [loadError, setLoadError] = useState(null),
    [coupon, setCoupon] = useState(""),
    [order, setOrder] = useState(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [applied, setApplied] = useState(null),
    [open, setOpen] = useState(false);
  const key = useMemo(() => crypto.randomUUID(), [joined]);
  const quote = useApi(
      joined ? `/coupons/quote?offeringIds=${joined}` : null,
      true,
    ),
    vouchers = useApi(
      joined ? `/coupons/available?offeringIds=${joined}` : null,
      true,
    );
  useEffect(() => {
    let active = true;
    setOrder(null);
    setApplied(null);
    setError("");
    setLoading(true);
    Promise.all(ids.map((id) => api.get(`/offerings/${id}`)))
      .then((value) => {
        if (active) {
          setDetails(value);
          setLoadError(null);
        }
      })
      .catch((e) => {
        if (active) setLoadError(e);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [joined]);
  async function apply(code = coupon) {
    setBusy(true);
    setError("");
    try {
      const result = await api.post(
        "/coupons/preview",
        { offeringIds: ids, code: code.trim() },
        token,
      );
      setApplied(result);
      setCoupon(result.coupon.code);
      setOpen(false);
    } catch (e) {
      setApplied(null);
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function submit(event) {
    event.preventDefault();
    if (coupon.trim() && !applied) {
      setError("Áp dụng mã để kiểm tra điều kiện trước khi tạo đơn.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const result = await api.post(
        "/orders/checkout",
        { offeringIds: ids, couponCode: coupon.trim() || null },
        token,
        { "Idempotency-Key": key },
      );
      setOrder(result);
      for (const id of ids) {
        try {
          remove(id);
        } catch {}
      }
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  if (!ids.length || ids.length > 20)
    return (
      <Shell title="Chọn lớp học">
        <p>
          Chọn từ 1 đến 20 lớp hợp lệ trong{" "}
          <Link to="/buyer/cart" className="text-blue-600">
            giỏ hàng
          </Link>
          .
        </p>
      </Shell>
    );
  if (loading) return <Loading />;
  if (loadError) return <ErrorState error={loadError} />;
  return (
    <Shell
      title="Xác nhận đăng ký"
      subtitle={`${ids.length} lớp đã chọn · Tổng tiền được xác nhận bởi máy chủ.`}
    >
      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_360px]">
        <div className="grid gap-4">
          {details.map(({ course, offering }) => (
            <article
              className="panel flex flex-wrap items-start gap-4"
              key={offering.id}
            >
              <CourseImage
                className="h-28 w-40 shrink-0 rounded-lg object-cover"
                src={course.thumbnailUrl}
                alt={course.title}
              />
              <div className="min-w-0 flex-1">
                <h2 className="text-base">{course.title}</h2>
                <p>{offering.cohortName || offering.code}</p>
                <p className="text-sm text-slate-500">
                  {date(offering.startDate)} — {date(offering.endDate)}
                </p>
                <p className="text-sm text-slate-500">
                  {offering.scheduleSummary}
                </p>
                <strong>{money(offering.price)}</strong>
              </div>
            </article>
          ))}
        </div>
        <section className="panel lg:sticky lg:top-24">
          {error && <div className="alert">{error}</div>}
          {!order ? (
            <form className="form m-0" onSubmit={submit}>
              <div className="flex items-center justify-between gap-2">
                <h2>Mã ưu đãi</h2>
                <button
                  type="button"
                  className="link"
                  onClick={() => setOpen(true)}
                >
                  Chọn mã
                </button>
              </div>
              <label>
                Nhập mã giảm giá
                <div className="flex gap-2">
                  <input
                    maxLength={50}
                    value={coupon}
                    onChange={(e) => {
                      setCoupon(e.target.value);
                      setApplied(null);
                    }}
                  />
                  <Button
                    type="button"
                    variant="secondary"
                    busy={busy}
                    disabled={!coupon.trim()}
                    onClick={() => apply()}
                  >
                    Áp dụng
                  </Button>
                </div>
              </label>
              {applied && (
                <div className="success">
                  <strong>{applied.coupon.title || applied.coupon.code}</strong>
                  <p>Giảm {money(applied.discountAmount)}</p>
                  <button
                    className="link"
                    type="button"
                    onClick={() => {
                      setCoupon("");
                      setApplied(null);
                    }}
                  >
                    Bỏ mã
                  </button>
                </div>
              )}
              {quote.error ? (
                <ErrorState error={quote.error} retry={quote.reload} />
              ) : (
                <dl className="record-fields">
                  <div>
                    <dt>Tạm tính</dt>
                    <dd>{money(quote.data?.subtotal)}</dd>
                  </div>
                  <div>
                    <dt>Giảm giá dự kiến</dt>
                    <dd>{money(applied?.discountAmount || 0)}</dd>
                  </div>
                  <div>
                    <dt>Tổng dự kiến</dt>
                    <dd className="text-xl font-bold">
                      {money(applied?.totalAmount ?? quote.data?.subtotal)}
                    </dd>
                  </div>
                </dl>
              )}
              <p className="text-xs text-slate-500">
                Giá, chỗ trống và lượt dùng mã được kiểm tra lại khi xác nhận
                đơn.
              </p>
              <Button
                type="submit"
                busy={busy}
                disabled={quote.loading || Boolean(quote.error)}
              >
                <Check size={16} />
                Xác nhận tạo đơn
              </Button>
            </form>
          ) : (
            <>
              <h2>Đơn {order.orderNumber}</h2>
              <StatusBadge value={order.status} />
              <p className="my-4 text-2xl font-bold">
                {money(order.totalAmount)}
              </p>
              <p>Giảm giá: {money(order.discountAmount)}</p>
              {order.status === "PAID" ? (
                <Link className="btn mt-4 w-full" to="/buyer/classroom">
                  Vào lớp học
                </Link>
              ) : (
                <Link
                  className="btn mt-4 w-full"
                  to={`/buyer/orders/${order.id}`}
                >
                  <CreditCard size={16} />
                  Xem đơn và thanh toán
                </Link>
              )}
            </>
          )}
        </section>
      </div>
      <Modal
        title="Chọn mã giảm giá"
        dismissible
        open={open}
        onClose={() => setOpen(false)}
      >
        {vouchers.loading ? (
          <Loading />
        ) : vouchers.error ? (
          <ErrorState error={vouchers.error} retry={vouchers.reload} />
        ) : (
          <div className="grid gap-3">
            {!vouchers.data?.length && (
              <p>Chưa có mã công khai; bạn có thể nhập mã được cung cấp.</p>
            )}
            {vouchers.data?.map((item) => (
              <article
                key={item.coupon.code}
                className="rounded-xl border border-dashed border-blue-300 bg-blue-50 p-4"
              >
                <div className="flex justify-between gap-3">
                  <div>
                    <h3>{item.coupon.title || item.coupon.code}</h3>
                    <strong>
                      {item.coupon.discountType === "PERCENT"
                        ? `${item.coupon.discountValue}%`
                        : money(item.coupon.discountValue)}
                    </strong>
                    <p className="text-xs">Mã {item.coupon.code}</p>
                  </div>
                  <Button
                    variant="secondary"
                    disabled={!item.eligible || busy}
                    onClick={() => apply(item.coupon.code)}
                  >
                    Chọn mã
                  </Button>
                </div>
                <p className="text-sm">{item.coupon.description}</p>
                <p className="text-xs">
                  Đơn từ {money(item.coupon.minimumOrderAmount)} · Hạn{" "}
                  {date(item.coupon.expiresAt)}
                </p>
                {!item.eligible && (
                  <p className="text-xs text-amber-700">{item.reason}</p>
                )}
              </article>
            ))}
          </div>
        )}
      </Modal>
    </Shell>
  );
}
