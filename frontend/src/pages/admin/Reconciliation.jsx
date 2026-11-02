import { useState } from "react";
import { RefreshCw } from "lucide-react";
import useApi from "../../hooks/useApi";
import { api } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import Shell from "../../components/common/Shell";
import { Button } from "../../components/ui";
import { ErrorState, Loading } from "../../components/common/State";
import { money } from "../../utils/format";
export default function Reconciliation() {
  const { token } = useAuth();
  const data = useApi("/admin/payments?size=100", true);
  const [id, setId] = useState(""),
    [message, setMessage] = useState(""),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const result = await api.post(
        `/admin/payments/${id}/reconcile`,
        undefined,
        token,
      );
      setMessage(
        Number(result.resultCode) === 0 || Number(result.return_code) === 1
          ? "Nhà cung cấp xác nhận giao dịch thành công."
          : "Đã truy vấn nhà cung cấp. Chưa xác nhận giao dịch thành công.",
      );
      await data.reload();
    } catch (e) {
      setError(
        e.status === 404
          ? "Đối soát chưa khả dụng. Vui lòng kiểm tra cấu hình cổng thanh toán."
          : e.message,
      );
    } finally {
      setBusy(false);
    }
  }
  if (data.loading) return <Loading />;
  if (data.error) return <ErrorState error={data.error} retry={data.reload} />;
  const rows = (data.data?.items || []).filter(
    (row) =>
      row.provider?.startsWith("MOMO") || row.provider === "ZALOPAY_SANDBOX",
  );
  return (
    <Shell
      title="Đối soát thanh toán"
      subtitle="Truy vấn trạng thái giao dịch từ nhà cung cấp, không tự đánh dấu đã thanh toán."
    >
      <form className="panel form" onSubmit={submit}>
        {error && <div className="alert">{error}</div>}
        {message && (
          <div className="success" role="status">
            {message}
          </div>
        )}
        <label>
          Giao dịch thanh toán online
          <select required value={id} onChange={(e) => setId(e.target.value)}>
            <option value="">Chọn giao dịch cần kiểm tra</option>
            {rows.map((row) => (
              <option key={row.id} value={row.id}>
                #{row.id} · Đơn #{row.orderId} · {money(row.amount)} ·{" "}
                {row.status}
              </option>
            ))}
          </select>
        </label>
        {!rows.length && (
          <p>
            Chưa có giao dịch online để đối soát. Tính năng này hoạt động khi
            hệ thống đã kết nối cổng thanh toán.
          </p>
        )}
        <Button type="submit" busy={busy} disabled={!id}>
          <RefreshCw size={16} />
          Truy vấn nhà cung cấp
        </Button>
      </form>
    </Shell>
  );
}
