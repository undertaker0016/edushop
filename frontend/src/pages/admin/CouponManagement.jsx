import { useState } from "react";
import { Plus, Pencil, History, Power } from "lucide-react";
import useApi from "../../hooks/useApi";
import { api } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import Shell from "../../components/common/Shell";
import DataTable from "../../components/tables/DataTable";
import { Loading, ErrorState } from "../../components/common/State";
import { Modal, Button, ConfirmDialog } from "../../components/ui";
import { money, date } from "../../utils/format";
const initial = {
  title: "",
  description: "",
  minimumOrderAmount: 0,
  startsAt: null,
  code: "",
  discountType: "PERCENT",
  discountValue: 10,
  maxDiscountAmount: null,
  maxUses: 100,
  maxUsesPerUser: 1,
  expiresAt: null,
};
export default function CouponManagement() {
  const data = useApi("/admin/coupons", true);
  const { token } = useAuth();
  const [form, setForm] = useState(null),
    [editing, setEditing] = useState(null),
    [history, setHistory] = useState(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [toggle, setToggle] = useState(null),
    [query, setQuery] = useState("");
  async function save(event) {
    event.preventDefault();
    setBusy(true);
    setError("");
    const payload = {
      ...form,
      discountValue: Number(form.discountValue),
      maxDiscountAmount: form.maxDiscountAmount
        ? Number(form.maxDiscountAmount)
        : null,
      maxUses: form.maxUses ? Number(form.maxUses) : null,
      maxUsesPerUser: form.maxUsesPerUser ? Number(form.maxUsesPerUser) : null,
      expiresAt: form.expiresAt || null,
    };
    try {
      if (editing)
        await api.put(
          `/admin/coupons/${encodeURIComponent(editing)}`,
          payload,
          token,
        );
      else await api.post("/admin/coupons", payload, token);
      setForm(null);
      await data.reload();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function showHistory(row) {
    try {
      setHistory({
        code: row.code,
        rows: await api.get(
          `/admin/coupons/${encodeURIComponent(row.code)}/redemptions`,
          token,
        ),
      });
    } catch (e) {
      setError(e.message);
    }
  }
  async function change() {
    setBusy(true);
    try {
      await api.patch(
        `/admin/coupons/${encodeURIComponent(toggle.code)}/active?active=${!toggle.active}`,
        undefined,
        token,
      );
      setToggle(null);
      await data.reload();
    } catch (e) {
      setError(e.message);
      setToggle(null);
    } finally {
      setBusy(false);
    }
  }
  if (data.loading) return <Loading />;
  if (data.error) return <ErrorState error={data.error} retry={data.reload} />;
  return (
    <Shell
      title="Mã giảm giá"
      subtitle="Tạo ưu đãi, giới hạn sử dụng và theo dõi lượt áp dụng."
      actions={
        <Button
          onClick={() => {
            setEditing(null);
            setForm(initial);
            setError("");
          }}
        >
          <Plus size={16} />
          Thêm mã
        </Button>
      }
    >
      {error && !form && <div className="alert">{error}</div>}
      <div className="filters">
        <input
          aria-label="Tìm mã giảm giá"
          placeholder="Tìm mã giảm giá"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
      </div>
      <DataTable
        rows={(data.data || []).filter((row) =>
          row.code.toLowerCase().includes(query.toLowerCase()),
        )}
        columns={[
          { key: "code", label: "Mã" },
          {
            key: "discountValue",
            label: "Giảm giá",
            render: (row) =>
              row.discountType === "PERCENT"
                ? `${row.discountValue}%`
                : money(row.discountValue),
          },
          { key: "usedCount", label: "Đã dùng" },
          { key: "maxUses", label: "Giới hạn lượt" },
          {
            key: "expiresAt",
            label: "Hết hạn",
            render: (row) => date(row.expiresAt),
          },
          {
            key: "active",
            label: "Trạng thái",
            render: (row) => (row.active ? "Đang áp dụng" : "Đã tắt"),
          },
          {
            key: "actions",
            label: "Thao tác",
            render: (row) => (
              <div className="action-buttons">
                <Button
                  variant="secondary"
                  onClick={() => {
                    setEditing(row.code);
                    setForm({ ...row });
                    setError("");
                  }}
                >
                  <Pencil size={15} />
                  Sửa
                </Button>
                <Button variant="secondary" onClick={() => showHistory(row)}>
                  <History size={15} />
                  Lịch sử
                </Button>
                <Button variant="secondary" onClick={() => setToggle(row)}>
                  <Power size={15} />
                  {row.active ? "Tắt" : "Bật"}
                </Button>
              </div>
            ),
          },
        ]}
      />
      <Modal
        title={editing ? "Sửa mã giảm giá" : "Thêm mã giảm giá"}
        open={Boolean(form)}
        onClose={() => {
          if (!busy) setForm(null);
        }}
      >
        {form && (
          <form className="form" onSubmit={save}>
            {error && <div className="alert">{error}</div>}
            <div className="rounded-lg bg-blue-50 p-3 text-sm text-blue-900">
              Mã giảm giá dùng tại checkout. Người mua có thể chọn từ danh sách
              hoặc nhập mã. Mã đã gắn với đơn hàng chỉ có thể bật/tắt để giữ
              lịch sử.
            </div>
            <label>
              Tên chương trình
              <input
                maxLength={150}
                value={form.title || ""}
                onChange={(e) => setForm({ ...form, title: e.target.value })}
                placeholder="Chào mừng học viên mới"
              />
            </label>
            <label>
              Giới thiệu và điều kiện
              <textarea
                maxLength={1000}
                value={form.description || ""}
                onChange={(e) =>
                  setForm({ ...form, description: e.target.value })
                }
              />
            </label>
            <label>
              Giá trị đơn tối thiểu (VND)
              <input
                type="number"
                min="0"
                value={form.minimumOrderAmount || 0}
                onChange={(e) =>
                  setForm({ ...form, minimumOrderAmount: e.target.value })
                }
              />
            </label>
            <label>
              Bắt đầu áp dụng
              <input
                type="datetime-local"
                value={form.startsAt || ""}
                onChange={(e) => setForm({ ...form, startsAt: e.target.value })}
              />
            </label>
            <label>
              Mã ưu đãi
              <input
                required
                pattern="[A-Za-z0-9_-]+"
                maxLength={50}
                disabled={Boolean(editing)}
                value={form.code}
                onChange={(e) => setForm({ ...form, code: e.target.value })}
              />
            </label>
            <label>
              Loại giảm giá
              <select
                value={form.discountType}
                onChange={(e) =>
                  setForm({ ...form, discountType: e.target.value })
                }
              >
                <option value="PERCENT">Phần trăm</option>
                <option value="FIXED">Số tiền cố định</option>
              </select>
            </label>
            {[
              ["discountValue", "Giá trị giảm"],
              ["maxDiscountAmount", "Giảm tối đa (VND, không bắt buộc)"],
              ["maxUses", "Tổng lượt sử dụng tối đa"],
              ["maxUsesPerUser", "Lượt tối đa mỗi tài khoản"],
            ].map(([key, text]) => (
              <label key={key}>
                {text}
                <input
                  type="number"
                  min={
                    key === "discountValue" || key === "maxDiscountAmount"
                      ? 0.01
                      : 1
                  }
                  step={
                    key === "discountValue" || key === "maxDiscountAmount"
                      ? 0.01
                      : 1
                  }
                  required={key === "discountValue"}
                  value={form[key] ?? ""}
                  onChange={(e) => setForm({ ...form, [key]: e.target.value })}
                />
              </label>
            ))}
            <label>
              Ngày hết hạn
              <input
                type="datetime-local"
                value={form.expiresAt || ""}
                onChange={(e) =>
                  setForm({ ...form, expiresAt: e.target.value })
                }
              />
            </label>
            <Button type="submit" busy={busy}>
              Lưu mã giảm giá
            </Button>
          </form>
        )}
      </Modal>
      <Modal
        title={`Lịch sử sử dụng ${history?.code || ""}`}
        open={Boolean(history)}
        onClose={() => setHistory(null)}
      >
        <p className="mb-3 text-xs text-slate-500">
          Hiển thị tối đa 100 lượt gần nhất.
        </p>
        <DataTable
          rows={history?.rows || []}
          columns={[
            { key: "id", label: "Mã lượt" },
            { key: "userId", label: "Người dùng" },
            { key: "orderId", label: "Đơn hàng" },
            { key: "status", label: "Trạng thái" },
            {
              key: "createdAt",
              label: "Thời gian",
              render: (row) => date(row.createdAt),
            },
          ]}
        />
      </Modal>
      <ConfirmDialog
        open={Boolean(toggle)}
        onClose={() => setToggle(null)}
        onConfirm={change}
        busy={busy}
      >
        Thay đổi trạng thái mã {toggle?.code}?
      </ConfirmDialog>
    </Shell>
  );
}
