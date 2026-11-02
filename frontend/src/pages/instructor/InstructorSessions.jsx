import { useState } from "react";
import { Plus, Pencil, X, Video } from "lucide-react";
import useApi from "../../hooks/useApi";
import { api } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import Shell from "../../components/common/Shell";
import DataTable from "../../components/tables/DataTable";
import { Modal, Button, ConfirmDialog } from "../../components/ui";
import { ErrorState, Loading } from "../../components/common/State";
import { date } from "../../utils/format";
const initial = {
  sessionNumber: 1,
  title: "",
  startsAt: "",
  endsAt: "",
  meetingProvider: "GOOGLE_MEET",
  meetingUrl: "",
  meetingPassword: "",
  notes: "",
};
export default function InstructorSessions() {
  const offerings = useApi("/instructor/offerings", true);
  const { token, user } = useAuth();
  const ownCourses = useApi("/instructor/courses", true);
  const [id, setId] = useState("");
  const data = useApi(id ? `/instructor/offerings/${id}/sessions` : null, true);
  const [room, setRoom] = useState(null);
  const [form, setForm] = useState(initial),
    [editing, setEditing] = useState(null),
    [open, setOpen] = useState(false),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [cancel, setCancel] = useState(null);
  const selectedOffering = (offerings.data || []).find(
    (row) => row.id === Number(id),
  );
  const canManage =
    user.roles.includes("ADMIN") ||
    (ownCourses.data || []).some(
      (course) => course.id === selectedOffering?.courseId,
    );
  async function save(event) {
    event.preventDefault();
    setBusy(true);
    setError("");
    const payload = { ...form, sessionNumber: Number(form.sessionNumber) };
    try {
      if (editing)
        await api.put(
          `/instructor/offerings/${id}/sessions/${editing}`,
          payload,
          token,
        );
      else
        await api.post(`/instructor/offerings/${id}/sessions`, payload, token);
      setOpen(false);
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
      await api.del(`/instructor/offerings/${id}/sessions/${cancel}`, token);
      setCancel(null);
      await data.reload();
    } catch (e) {
      setError(e.message);
      setCancel(null);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Shell
      title="Lịch từng buổi học"
      subtitle="Liên kết phòng học chỉ hiển thị trong khu vực có quyền truy cập."
      actions={
        <Button
          disabled={!id || !canManage}
          onClick={() => {
            setEditing(null);
            setForm({
              ...initial,
              sessionNumber: (data.data?.length || 0) + 1,
            });
            setError("");
            setOpen(true);
          }}
        >
          <Plus size={16} />
          Thêm buổi học
        </Button>
      }
    >
      <div className="filters">
        <label>
          Lớp học{" "}
          <select value={id} onChange={(e) => setId(e.target.value)}>
            <option value="">Chọn lớp học</option>
            {(offerings.data || []).map((row) => (
              <option key={row.id} value={row.id}>
                {row.code} — {row.cohortName}
              </option>
            ))}
          </select>
        </label>
      </div>
      {offerings.error && (
        <ErrorState error={offerings.error} retry={offerings.reload} />
      )}{" "}
      {error && !open && <div className="alert">{error}</div>}
      {id &&
        (data.loading ? (
          <Loading />
        ) : data.error ? (
          <ErrorState error={data.error} retry={data.reload} />
        ) : (
          <DataTable
            rows={data.data || []}
            columns={[
              { key: "sessionNumber", label: "Buổi" },
              { key: "title", label: "Tiêu đề" },
              {
                key: "startsAt",
                label: "Bắt đầu",
                render: (row) => date(row.startsAt),
              },
              {
                key: "endsAt",
                label: "Kết thúc",
                render: (row) => date(row.endsAt),
              },
              { key: "status", label: "Trạng thái" },
              {
                key: "actions",
                label: "Thao tác",
                render: (row) =>
                  row.status !== "CANCELLED" && (
                    <div className="action-buttons">
                      <Button
                        variant="secondary"
                        onClick={() => {
                          setRoom({ ...row });
                          setError("");
                        }}
                      >
                        <Video size={15} />
                        Phòng học
                      </Button>
                      {canManage && (
                        <>
                          <Button
                            variant="secondary"
                            onClick={() => {
                              setEditing(row.sessionNumber);
                              setForm({ ...initial, ...row });
                              setError("");
                              setOpen(true);
                            }}
                          >
                            <Pencil size={15} />
                            Sửa lịch
                          </Button>
                          <Button
                            variant="danger"
                            onClick={() => setCancel(row.sessionNumber)}
                          >
                            <X size={15} />
                            Hủy buổi
                          </Button>
                        </>
                      )}
                    </div>
                  ),
              },
            ]}
          />
        ))}
      <Modal
        title={editing ? "Sửa buổi học" : "Thêm buổi học"}
        open={open}
        onClose={() => {
          if (!busy) setOpen(false);
        }}
      >
        <form className="form" onSubmit={save}>
          {error && <div className="alert">{error}</div>}
          {[
            ["sessionNumber", "Buổi số", "number"],
            ["title", "Tiêu đề", "text"],
            ["startsAt", "Bắt đầu", "datetime-local"],
            ["endsAt", "Kết thúc", "datetime-local"],
            ["meetingUrl", "Đường dẫn phòng học", "url"],
            ["meetingPassword", "Mật khẩu phòng học", "text"],
          ].map(([key, label, type]) => (
            <label key={key}>
              {label}
              <input
                required={[
                  "sessionNumber",
                  "title",
                  "startsAt",
                  "endsAt",
                ].includes(key)}
                disabled={editing && key === "sessionNumber"}
                type={type}
                value={form[key] || ""}
                onChange={(e) => setForm({ ...form, [key]: e.target.value })}
              />
            </label>
          ))}
          <label>
            Nền tảng
            <select
              value={form.meetingProvider || "OTHER"}
              onChange={(e) =>
                setForm({ ...form, meetingProvider: e.target.value })
              }
            >
              <option value="GOOGLE_MEET">Google Meet</option>
              <option value="ZOOM">Zoom</option>
              <option value="OTHER">Khác</option>
            </select>
          </label>
          <label>
            Ghi chú
            <textarea
              value={form.notes || ""}
              onChange={(e) => setForm({ ...form, notes: e.target.value })}
            />
          </label>
          <Button type="submit" busy={busy}>
            Lưu buổi học
          </Button>
        </form>
      </Modal>
      <Modal
        title="Liên kết phòng học"
        open={Boolean(room)}
        onClose={() => {
          if (!busy) setRoom(null);
        }}
      >
        {room && (
          <form
            className="form"
            onSubmit={async (event) => {
              event.preventDefault();
              setBusy(true);
              setError("");
              try {
                await api.put(
                  `/instructor/offerings/${id}/sessions/${room.sessionNumber}/room`,
                  {
                    meetingProvider: room.meetingProvider || "GOOGLE_MEET",
                    meetingUrl: room.meetingUrl,
                    meetingPassword: room.meetingPassword || null,
                    notes: room.notes || null,
                  },
                  token,
                );
                setRoom(null);
                await data.reload();
              } catch (e) {
                setError(e.message);
              } finally {
                setBusy(false);
              }
            }}
          >
            <p className="text-sm text-slate-500">
              Cập nhật phòng mà không đổi ngày giờ đã đăng ký. Học viên đang học
              sẽ nhận thông báo.
            </p>
            {error && <div className="alert">{error}</div>}
            <label>
              Nền tảng
              <select
                value={room.meetingProvider || "GOOGLE_MEET"}
                onChange={(e) =>
                  setRoom({ ...room, meetingProvider: e.target.value })
                }
              >
                <option value="GOOGLE_MEET">Google Meet</option>
                <option value="ZOOM">Zoom</option>
                <option value="OTHER">Khác</option>
              </select>
            </label>
            <label>
              Link phòng học (HTTPS)
              <input
                required
                type="url"
                pattern="https://.*"
                value={room.meetingUrl || ""}
                onChange={(e) =>
                  setRoom({ ...room, meetingUrl: e.target.value })
                }
              />
            </label>
            <label>
              Mật khẩu phòng (nếu có)
              <input
                value={room.meetingPassword || ""}
                onChange={(e) =>
                  setRoom({ ...room, meetingPassword: e.target.value })
                }
              />
            </label>
            <label>
              Hướng dẫn vào lớp
              <textarea
                value={room.notes || ""}
                onChange={(e) => setRoom({ ...room, notes: e.target.value })}
              />
            </label>
            <Button busy={busy} type="submit">
              Lưu phòng học
            </Button>
          </form>
        )}
      </Modal>
      <ConfirmDialog
        open={Boolean(cancel)}
        onClose={() => setCancel(null)}
        onConfirm={remove}
        busy={busy}
      >
        Hủy buổi học này và giữ lại lịch sử?
      </ConfirmDialog>
    </Shell>
  );
}
