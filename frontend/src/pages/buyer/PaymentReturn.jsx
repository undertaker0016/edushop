import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { RefreshCw, CheckCircle2, Clock3 } from "lucide-react";
import { useAuth } from "../../contexts/AuthContext";
import { api } from "../../api/client";
import { paymentReference } from "../../utils/payment";
import Shell from "../../components/common/Shell";
import { Loading, ErrorState } from "../../components/common/State";

export default function PaymentReturn() {
  const [params] = useSearchParams();
  const reference = paymentReference(
    params.get("reference") ||
      params.get("apptransid") ||
      params.get("app_trans_id") ||
      params.get("orderId"),
  );
  const { token, user, loading } = useAuth();
  const [result, setResult] = useState(null),
    [error, setError] = useState(null),
    [checking, setChecking] = useState(false),
    [retry, setRetry] = useState(0);
  useEffect(() => {
    if (!reference || !user || !token) return;
    let active = true,
      timer,
      attempts = 0;
    setResult(null);
    setError(null);
    setChecking(true);
    async function check() {
      try {
        let value = await api.get(
          `/payments/references/${encodeURIComponent(reference)}`,
          token,
        );
        if (!active) return;
        if (
          value.provider === "ZALOPAY_SANDBOX" &&
          value.paymentStatus === "PENDING"
        ) {
          await api.post(
            `/payments/${value.paymentId}/verify`,
            undefined,
            token,
          );
          value = await api.get(
            `/payments/references/${encodeURIComponent(reference)}`,
            token,
          );
          if (!active) return;
        }
        setResult(value);
        attempts++;
        if (
          value.orderStatus === "PENDING" &&
          value.paymentStatus === "PENDING" &&
          attempts < 4
        )
          timer = setTimeout(
            check,
            value.provider === "ZALOPAY_SANDBOX" ? 15000 : 3000,
          );
        else setChecking(false);
      } catch (error) {
        if (active) {
          setError(error);
          setChecking(false);
        }
      }
    }
    check();
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [reference, user?.id, token, retry]);
  if (loading) return <Loading />;
  if (!reference)
    return (
      <Shell title="Không có mã giao dịch hợp lệ">
        <p>Không thể xác định đơn hàng từ đường dẫn này.</p>
        <Link className="btn secondary" to="/buyer/orders">
          Xem đơn hàng của tôi
        </Link>
      </Shell>
    );
  if (!user)
    return (
      <Shell title="Kiểm tra thanh toán">
        <p>Đăng nhập đúng tài khoản đã đặt đơn để xem kết quả thanh toán.</p>
        <Link
          className="btn"
          to="/auth/login"
          state={{ paymentReference: reference }}
        >
          Đăng nhập để kiểm tra
        </Link>
      </Shell>
    );
  const paid = result?.orderStatus === "PAID",
    reconciliation = !!result?.reconciliationRequired;
  return (
    <Shell
      title="Kết quả thanh toán"
      subtitle="Kết quả được kiểm tra từ máy chủ EduShop."
    >
      <section className="panel max-w-2xl mx-auto">
        {error ? (
          <ErrorState
            error={error}
            retry={() => setRetry((value) => value + 1)}
          />
        ) : !result ? (
          <Loading />
        ) : (
          <>
            {paid ? (
              <CheckCircle2 className="text-green-700 mb-3" size={32} />
            ) : (
              <Clock3 className="text-blue-600 mb-3" size={32} />
            )}
            <h2>
              {paid
                ? "Đơn hàng đã thanh toán"
                : reconciliation
                  ? "Giao dịch cần đối soát"
                  : result.paymentStatus === "FAILED"
                    ? "Thanh toán chưa thành công"
                    : "Đang chờ xác nhận thanh toán"}
            </h2>
            <p>
              {paid
                ? "Bạn có thể vào lớp học đã đăng ký."
                : reconciliation
                  ? "Thanh toán đến sau thời gian giữ chỗ. Vui lòng liên hệ quản trị; quyền học chưa được cấp tự động."
                  : result.paymentStatus === "FAILED"
                    ? "Bạn có thể quay lại đơn hàng và thử thanh toán khi đơn còn thời hạn."
                    : "EduShop chưa nhận được xác nhận hoàn tất. Hãy kiểm tra lại hoặc mở chi tiết đơn; không cần thanh toán lặp lại ngay."}
            </p>
            {checking && (
              <p role="status" className="text-sm text-slate-500">
                Đang tự kiểm tra trạng thái giao dịch…
              </p>
            )}
            <div className="flex flex-wrap gap-3 mt-5">
              <Link
                className="btn"
                to={
                  paid ? "/buyer/classroom" : `/buyer/orders/${result.orderId}`
                }
              >
                {paid ? "Vào lớp học" : "Xem chi tiết đơn"}
              </Link>
              <button
                type="button"
                className="btn secondary"
                disabled={checking}
                onClick={() => setRetry((value) => value + 1)}
              >
                <RefreshCw size={16} />
                Kiểm tra lại
              </button>
            </div>
          </>
        )}
      </section>
    </Shell>
  );
}
