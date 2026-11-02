import { useState } from "react";
import { Plus, Pencil, Lock, Unlock } from "lucide-react";
import useApi from "../../hooks/useApi";
import { api } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import Shell from "../../components/common/Shell";
import DataTable from "../../components/tables/DataTable";
import { Loading, ErrorState } from "../../components/common/State";
import {
  Button,
  Modal,
  PasswordInput,
  ConfirmDialog,
} from "../../components/ui";
const initial = {
  fullName: "",
  email: "",
  phone: "",
  password: "",
  role: "INSTRUCTOR",
};
export default function Users() {
  const [page, setPage] = useState(0);
  const data = useApi(`/admin/users?page=${page}&size=20`, true);
  const { token } = useAuth();
  const [query, setQuery] = useState(""),
    [form, setForm] = useState(null),
    [editing, setEditing] = useState(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [change, setChange] = useState(null);
  async function save(event) {
    event.preventDefault();
    setBusy(true);
    try {
      if (editing)
        await api.put(
          `/admin/users/${editing}`,
          { fullName: form.fullName, phone: form.phone || null },
          token,
        );
      else await api.post("/admin/users", form, token);
      setForm(null);
      await data.reload();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function edit(row) {
    try {
      setForm(await api.get(`/admin/users/${row.id}`, token));
      setEditing(row.id);
      setError("");
    } catch (e) {
      setError(e.message);
    }
  }
  async function apply() {
    setBusy(true);
    try {
      await api.patch(change.path, undefined, token);
      setChange(null);
      await data.reload();
    } catch (e) {
      setError(e.message);
      setChange(null);
    } finally {
      setBusy(false);
    }
  }
  if (data.loading) return <Loading />;
  if (data.error) return <ErrorState error={data.error} retry={data.reload} />;
  const rows = (data.data || []).filter((row) =>
    `${row.fullName} ${row.email}`.toLowerCase().includes(query.toLowerCase()),
  );
  return (
    <Shell
      title="Quản lý tài khoản"
      subtitle="Học viên không cần role ứng dụng. Chỉ cấp thêm quyền cho người cần dùng chức năng tương ứng."
      actions={
        <Button
          onClick={() => {
            setForm(initial);
            setEditing(null);
            setError("");
          }}
        >
          <Plus size={16} />
          Tạo tài khoản quản lý
        </Button>
      }
    >
      {error && !form && <div className="alert">{error}</div>}
      <div className="filters">
        <input
          aria-label="Tìm tài khoản trong trang"
          placeholder="Tìm tên hoặc email trong trang"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
      </div>
      <DataTable
        rows={rows}
        columns={[
          { key: "id", label: "Mã" },
          { key: "fullName", label: "Họ tên" },
          { key: "email", label: "Email" },
          { key: "status", label: "Trạng thái" },
          {
            key: "roles",
            label: "Phân quyền",
            render: (row) => (
              <div className="grid gap-2">
                {["ADMIN", "INSTRUCTOR"].map((role) => (
                  <label className="flex items-center gap-2" key={role}>
                    <input
                      type="checkbox"
                      checked={row.roles.includes(role)}
                      onChange={(e) =>
                        setChange({
                          path: `/admin/users/${row.id}/roles/${role}?enabled=${e.target.checked}`,
                          text: `Thay đổi quyền ${role === "ADMIN" ? "quản trị viên" : "giảng viên"} của ${row.email}?`,
                        })
                      }
                    />
                    {role === "ADMIN" ? "Quản trị viên" : "Giảng viên"}
                  </label>
                ))}
              </div>
            ),
          },
          {
            key: "actions",
            label: "Thao tác",
            render: (row) => (
              <div className="action-buttons">
                <Button variant="secondary" onClick={() => edit(row)}>
                  <Pencil size={15} />
                  Sửa
                </Button>
                <Button
                  variant={row.status === "ACTIVE" ? "danger" : "secondary"}
                  onClick={() =>
                    setChange({
                      path: `/admin/users/${row.id}/status?status=${row.status === "ACTIVE" ? "BANNED" : "ACTIVE"}`,
                      text: `${row.status === "ACTIVE" ? "Khóa" : "Mở khóa"} tài khoản ${row.email}?`,
                    })
                  }
                >
                  {row.status === "ACTIVE" ? (
                    <Lock size={15} />
                  ) : (
                    <Unlock size={15} />
                  )}{" "}
                  {row.status === "ACTIVE" ? "Khóa" : "Mở khóa"}
                </Button>
              </div>
            ),
          },
        ]}
      />
      <div className="pagination">
        <Button
          variant="secondary"
          disabled={page === 0}
          onClick={() => setPage(page - 1)}
        >
          Trước
        </Button>
        <span>Trang {page + 1}</span>
        <Button
          variant="secondary"
          disabled={(data.data || []).length < 20}
          onClick={() => setPage(page + 1)}
        >
          Sau
        </Button>
      </div>
      <Modal
        title={editing ? "Sửa thông tin tài khoản" : "Tạo tài khoản quản lý"}
        open={Boolean(form)}
        onClose={() => {
          if (!busy) setForm(null);
        }}
      >
        {form && (
          <form className="form" onSubmit={save}>
            {error && <div className="alert">{error}</div>}
            <label>
              Họ tên
              <input
                required
                maxLength={100}
                value={form.fullName}
                onChange={(e) => setForm({ ...form, fullName: e.target.value })}
              />
            </label>
            <label>
              Email
              <input
                type="email"
                required
                disabled={Boolean(editing)}
                maxLength={255}
                value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })}
              />
            </label>
            <label>
              Điện thoại
              <input
                maxLength={20}
                value={form.phone || ""}
                onChange={(e) => setForm({ ...form, phone: e.target.value })}
              />
            </label>
            {!editing && (
              <>
                <label>
                  Mật khẩu ban đầu
                  <PasswordInput
                    required
                    minLength={12}
                    maxLength={72}
                    autoComplete="new-password"
                    value={form.password}
                    onChange={(e) =>
                      setForm({ ...form, password: e.target.value })
                    }
                  />
                </label>
                <label>
                  Vai trò
                  <select
                    value={form.role}
                    onChange={(e) => setForm({ ...form, role: e.target.value })}
                  >
                    <option value="INSTRUCTOR">Giảng viên</option>
                    <option value="ADMIN">Quản trị viên</option>
                  </select>
                </label>
              </>
            )}
            <Button busy={busy} type="submit">
              Lưu tài khoản
            </Button>
          </form>
        )}
      </Modal>
      <ConfirmDialog
        open={Boolean(change)}
        onClose={() => setChange(null)}
        onConfirm={apply}
        busy={busy}
      >
        {change?.text}
      </ConfirmDialog>
    </Shell>
  );
}
