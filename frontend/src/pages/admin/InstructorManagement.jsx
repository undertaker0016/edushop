import ImagePicker from "../../components/common/ImagePicker";
import { useState } from "react";
import { Plus, Pencil, UserMinus } from "lucide-react";
import useApi from "../../hooks/useApi";
import { api } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import Shell from "../../components/common/Shell";
import DataTable from "../../components/tables/DataTable";
import { Loading, ErrorState } from "../../components/common/State";
import { Button, Modal, Pagination, ConfirmDialog } from "../../components/ui";
export default function InstructorManagement() {
  const [page, setPage] = useState(0);
  const data = useApi(`/admin/instructors?page=${page}&size=20`, true),
    users = useApi("/admin/users?size=100", true);
  const { token } = useAuth();
  const [form, setForm] = useState(null),
    [editing, setEditing] = useState(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [revoke, setRevoke] = useState(null);
  async function edit(row) {
    try {
      setForm(await api.get(`/admin/instructors/${row.id}`, token));
      setEditing(row.id);
      setError("");
    } catch (e) {
      setError(e.message);
    }
  }
  async function save(event) {
    event.preventDefault();
    setBusy(true);
    try {
      if (editing) await api.put(`/admin/instructors/${editing}`, form, token);
      else await api.post("/admin/instructors", form, token);
      setForm(null);
      await data.reload();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function remove() {
    setBusy(true);
    try {
      await api.del(`/admin/instructors/${revoke.id}/role`, token);
      setRevoke(null);
      await data.reload();
    } catch (e) {
      setError(e.message);
      setRevoke(null);
    } finally {
      setBusy(false);
    }
  }
  if (data.loading) return <Loading />;
  if (data.error) return <ErrorState error={data.error} retry={data.reload} />;
  return (
    <Shell
      title="Quản lý giảng viên"
      subtitle="Cấp quyền cho tài khoản có sẵn, quản lý hồ sơ và quyền giảng dạy."
      actions={
        <Button
          onClick={() => {
            setEditing(null);
            setForm({ email: "", fullName: "" });
            setError("");
          }}
        >
          <Plus size={16} />
          Cấp quyền giảng viên
        </Button>
      }
    >
      {error && !form && <div className="alert">{error}</div>}
      <DataTable
        rows={data.data?.items || []}
        columns={[
          { key: "id", label: "Mã" },
          { key: "fullName", label: "Giảng viên" },
          { key: "email", label: "Email" },
          {
            key: "hasInstructorRole",
            label: "Quyền giảng dạy",
            render: (row) => (row.hasInstructorRole ? "Đã cấp" : "Đã thu hồi"),
          },
          {
            key: "actions",
            label: "Thao tác",
            render: (row) => (
              <div className="action-buttons">
                <Button variant="secondary" onClick={() => edit(row)}>
                  <Pencil size={15} />
                  Hồ sơ
                </Button>
                {Boolean(row.hasInstructorRole) && (
                  <Button variant="danger" onClick={() => setRevoke(row)}>
                    <UserMinus size={15} />
                    Thu hồi quyền
                  </Button>
                )}
              </div>
            ),
          },
        ]}
      />
      <Pagination
        page={page}
        totalPages={data.data?.totalPages || 1}
        onChange={setPage}
      />
      <Modal
        title={editing ? "Sửa hồ sơ giảng viên" : "Cấp quyền giảng viên"}
        open={Boolean(form)}
        onClose={() => {
          if (!busy) setForm(null);
        }}
      >
        {form && (
          <form className="form" onSubmit={save}>
            {error && <div className="alert">{error}</div>}
            {!editing && (
              <label>
                Tài khoản
                <select
                  required
                  value={form.email}
                  onChange={(e) => {
                    const user = users.data?.find(
                      (item) => item.email === e.target.value,
                    );
                    setForm({
                      email: e.target.value,
                      fullName: user?.fullName || "",
                    });
                  }}
                >
                  <option value="">Chọn người dùng</option>
                  {(users.data || []).map((user) => (
                    <option key={user.id} value={user.email}>
                      {user.fullName} — {user.email}
                    </option>
                  ))}
                </select>
              </label>
            )}
            {editing && (
              <ImagePicker
                value={form.avatarUrl}
                onChange={(value) => setForm({ ...form, avatarUrl: value })}
                onBusyChange={setBusy}
                label="Ảnh giảng viên"
              />
            )}
            {(editing
              ? [
                  "fullName",
                  "bio",
                  "contactEmail",
                  "phone",
                  "zalo",
                  "facebookUrl",
                ]
              : ["fullName"]
            ).map((key) => (
              <label key={key}>
                {
                  {
                    fullName: "Họ tên",
                    bio: "Tiểu sử",
                    avatarUrl: "Ảnh đại diện (URL)",
                    contactEmail: "Email liên hệ",
                    phone: "Điện thoại",
                    zalo: "Zalo",
                    facebookUrl: "Facebook",
                  }[key]
                }
                {key === "bio" ? (
                  <textarea
                    value={form[key] || ""}
                    onChange={(e) =>
                      setForm({ ...form, [key]: e.target.value })
                    }
                  />
                ) : (
                  <input
                    required={key === "fullName"}
                    value={form[key] || ""}
                    onChange={(e) =>
                      setForm({ ...form, [key]: e.target.value })
                    }
                  />
                )}
              </label>
            ))}
            <Button busy={busy} type="submit">
              Lưu thay đổi
            </Button>
          </form>
        )}
      </Modal>
      <ConfirmDialog
        open={Boolean(revoke)}
        onClose={() => setRevoke(null)}
        onConfirm={remove}
        busy={busy}
      >
        Thu hồi quyền giảng viên của {revoke?.fullName}? Hồ sơ và lịch sử vẫn
        được giữ.
      </ConfirmDialog>
    </Shell>
  );
}
