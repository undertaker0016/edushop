import { useEffect, useState } from "react";
import { api } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import Shell from "../../components/common/Shell";
import DataTable from "../../components/tables/DataTable";
import { ErrorState, Loading } from "../../components/common/State";
import { ConfirmDialog } from "../../components/ui";

export default function Categories() {
  const { token } = useAuth();
  const [rows, setRows] = useState([]);
  const [form, setForm] = useState({ name: "", slug: "", sortOrder: 0 });
  const [editing, setEditing] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true),
    [busy, setBusy] = useState(false),
    [message, setMessage] = useState(""),
    [pending, setPending] = useState(null),
    [query, setQuery] = useState("");
  const load = () => {
    setError(null);
    return api
      .get("/admin/catalog/categories", token)
      .then(setRows)
      .catch(setError)
      .finally(() => setLoading(false));
  };
  useEffect(() => {
    load();
  }, []);

  async function save(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setMessage("");
    try {
      if (editing)
        await api.put(
          `/admin/catalog/categories/${encodeURIComponent(editing)}`,
          form,
          token,
        );
      else await api.post("/admin/catalog/categories", form, token);
      setEditing(null);
      setForm({ name: "", slug: "", sortOrder: 0 });
      setMessage(editing ? "Đã cập nhật danh mục." : "Đã thêm danh mục.");
      await load();
    } catch (caught) {
      setError(caught);
      return;
    } finally {
      setBusy(false);
    }
  }

  async function toggle(row) {
    setBusy(true);
    setError(null);
    setMessage("");
    try {
      await api.patch(
        `/admin/catalog/categories/${encodeURIComponent(row.slug)}/active?active=${!row.active}`,
        undefined,
        token,
      );
      setPending(null);
      setMessage(
        row.active
          ? "Đã ẩn danh mục khỏi danh sách công khai. Các khóa học được giữ nguyên."
          : "Đã kích hoạt danh mục.",
      );
      await load();
    } catch (error) {
      setError(error);
      setPending(null);
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <Loading />;
  return (
    <Shell title="Danh mục">
      {error && <ErrorState error={error} retry={load} />}
      {message && (
        <p className="success" role="status">
          {message}
        </p>
      )}
      <p className="text-sm text-slate-500">
        Ẩn danh mục để ngừng hiển thị công khai; dữ liệu khóa học và lịch sử vẫn
        được giữ.
      </p>
      <form className="panel form inline" onSubmit={save}>
        <label>
          Tên danh mục
          <input
            required
            maxLength={100}
            aria-label="Tên danh mục"
            placeholder="Tên danh mục"
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
          />
        </label>
        <label>
          Đường dẫn danh mục
          <input
            required
            pattern="[a-z0-9]+(?:-[a-z0-9]+)*"
            maxLength={100}
            aria-label="Đường dẫn danh mục"
            placeholder="vi-du: lap-trinh"
            value={form.slug}
            onChange={(e) => setForm({ ...form, slug: e.target.value })}
          />
        </label>
        <label>
          Thứ tự hiển thị
          <input
            type="number"
            aria-label="Thứ tự hiển thị"
            min="0"
            value={form.sortOrder}
            onChange={(e) =>
              setForm({ ...form, sortOrder: Number(e.target.value) })
            }
          />
        </label>
        <button className="btn" disabled={busy}>
          {busy ? "Đang lưu…" : editing ? "Lưu thay đổi" : "Thêm danh mục"}
        </button>
        {editing && (
          <button
            className="btn secondary"
            type="button"
            disabled={busy}
            onClick={() => {
              setEditing(null);
              setForm({ name: "", slug: "", sortOrder: 0 });
              setError(null);
            }}
          >
            Hủy sửa
          </button>
        )}
      </form>
      <label className="form mb-4">
        Tìm danh mục
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Nhập tên hoặc đường dẫn"
        />
      </label>
      <DataTable
        rows={rows.filter((row) =>
          `${row.name} ${row.slug}`
            .toLocaleLowerCase("vi")
            .includes(query.toLocaleLowerCase("vi")),
        )}
        columns={[
          { key: "id", label: "ID" },
          { key: "name", label: "Tên" },
          { key: "slug", label: "Đường dẫn" },
          {
            key: "active",
            label: "Trạng thái",
            render: (row) => (row.active ? "Hoạt động" : "Ẩn"),
          },
          {
            key: "action",
            label: "Sửa",
            render: (row) => (
              <button
                className="btn secondary"
                disabled={busy}
                onClick={() => {
                  setEditing(row.slug);
                  setForm({
                    name: row.name,
                    slug: row.slug,
                    sortOrder: row.sortOrder,
                    icon: row.icon,
                  });
                }}
              >
                Sửa
              </button>
            ),
          },
          {
            key: "statusAction",
            label: "Trạng thái",
            render: (row) => (
              <button
                className="btn secondary"
                disabled={busy}
                onClick={() => setPending(row)}
              >
                {row.active ? "Vô hiệu hóa" : "Kích hoạt"}
              </button>
            ),
          },
        ]}
      />
      <ConfirmDialog
        open={!!pending}
        busy={busy}
        onClose={() => setPending(null)}
        onConfirm={() => toggle(pending)}
        title={pending?.active ? "Ẩn danh mục" : "Kích hoạt danh mục"}
        children={`Xác nhận thay đổi danh mục “${pending?.name || ""}”? Thao tác không xóa khóa học.`}
      />
    </Shell>
  );
}
