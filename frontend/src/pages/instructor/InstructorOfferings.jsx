import ActionNotice from "../../components/common/ActionNotice";
import { useSearchParams } from "react-router-dom";
import ScheduleBuilder from "../../components/common/ScheduleBuilder";
import { useEffect, useState } from "react";
import { Plus, Pencil, Check, X } from "lucide-react";
import useApi from "../../hooks/useApi";
import { api } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import Shell from "../../components/common/Shell";
import DataTable from "../../components/tables/DataTable";
import { Loading, ErrorState } from "../../components/common/State";
import { Modal, Button, ConfirmDialog } from "../../components/ui";
import { money, date } from "../../utils/format";
const initial = {
  courseId: "",
  instructorId: "",
  offeringCode: "LOP-" + crypto.randomUUID().slice(0, 8).toUpperCase(),
  cohortName: "",
  startDate: "",
  endDate: "",
  registrationClosesAt: "",
  scheduleSummary: "",
  capacity: 30,
  price: 0,
};
const today = () => new Date().toLocaleDateString("sv-SE");
const ACTIONS = {
  publish: {
    action: "open",
    label: "Mở bán",
    title: "Mở bán lớp học",
    text: "Lớp học sẽ hiển thị cho học viên và bắt đầu nhận đăng ký.",
    success: "Lớp học đã được mở bán thành công.",
  },
  reopen: {
    action: "open",
    label: "Mở lại",
    title: "Mở lại lớp học",
    text: "Lớp học sẽ tiếp tục nhận đăng ký. Học viên đã đăng ký trước đó vẫn giữ nguyên chỗ.",
    success: "Lớp học đã được mở lại thành công.",
  },
  close: {
    action: "close",
    label: "Đóng bán",
    title: "Đóng bán lớp học",
    text: "Lớp sẽ ngừng nhận đăng ký mới. Học viên đã đăng ký vẫn tham gia học bình thường.",
    success: "Lớp học đã đóng nhận đăng ký.",
  },
  complete: {
    action: "complete",
    label: "Hoàn thành",
    title: "Hoàn thành lớp học",
    text: "Xác nhận lớp đã kết thúc khóa học.",
    success: "Lớp học đã được đánh dấu hoàn thành.",
  },
  cancel: {
    action: "cancel",
    label: "Hủy",
    title: "Hủy lớp học",
    text: "Bạn có chắc chắn muốn hủy lớp học này? Chỉ có thể hủy lớp khi chưa có học viên đăng ký hoặc thanh toán.",
    success: "Lớp học đã được hủy thành công.",
  },
};
export default function InstructorOfferings() {
  const [params] = useSearchParams();
  const [notice, setNotice] = useState(
    params.get("setup") === "1"
      ? "Khóa học đã lưu. Tiếp theo chọn giảng viên, học phí và thời gian mở lớp."
      : "",
  );
  const [scheduleRows, setScheduleRows] = useState([]);
  const data = useApi("/instructor/offerings", true),
    courses = useApi("/instructor/courses", true);
  const { token, user } = useAuth();
  const admin = user.roles.includes("ADMIN");
  const teachers = useApi(admin ? "/admin/instructors?size=100" : null, true);
  const [form, setForm] = useState({
      ...initial,
      courseId: params.get("courseId") || "",
    }),
    [editing, setEditing] = useState(null),
    [open, setOpen] = useState(params.get("setup") === "1"),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [confirmation, setConfirmation] = useState(null);
  const canManage = (row) =>
    admin || (courses.data || []).some((course) => course.id === row.courseId);
  // Bản nháp form "Thêm lớp" được lưu tự động để lỡ đóng cửa sổ hoặc tải lại
  // trang thì không phải nhập lại từ đầu.
  const draftKey = `edushop:offering-draft:${user.id ?? user.email ?? "me"}`;
  const [draftRestored, setDraftRestored] = useState(false);
  useEffect(() => {
    if (!open || editing) return;
    const dirty =
      scheduleRows.length ||
      Object.keys(initial).some(
        (key) =>
          key !== "offeringCode" &&
          key !== "courseId" &&
          String(form[key] ?? "") !== String(initial[key] ?? ""),
      );
    if (!dirty) return;
    try {
      localStorage.setItem(draftKey, JSON.stringify({ form, scheduleRows }));
    } catch {
      /* bộ nhớ trình duyệt đầy hoặc bị chặn: bỏ qua */
    }
  }, [open, editing, form, scheduleRows, draftKey]);
  function readDraft() {
    try {
      return JSON.parse(localStorage.getItem(draftKey) || "null");
    } catch {
      return null;
    }
  }
  function clearDraft() {
    localStorage.removeItem(draftKey);
    setDraftRestored(false);
  }
  function blankForm() {
    return {
      ...initial,
      offeringCode: "LOP-" + crypto.randomUUID().slice(0, 8).toUpperCase(),
      courseId: params.get("courseId") || "",
    };
  }
  function openCreate() {
    const draft = readDraft();
    setEditing(null);
    setError("");
    if (draft?.form) {
      setForm({ ...blankForm(), ...draft.form });
      setScheduleRows(Array.isArray(draft.scheduleRows) ? draft.scheduleRows : []);
      setDraftRestored(true);
    } else {
      setForm(blankForm());
      setScheduleRows([]);
      setDraftRestored(false);
    }
    setOpen(true);
  }
  function startOver() {
    clearDraft();
    setForm(blankForm());
    setScheduleRows([]);
    setError("");
  }
  function field(key, value) {
    setForm((previous) => {
      const next = { ...previous, [key]: value };
      if (!editing) {
        try {
          localStorage.setItem(
            draftKey,
            JSON.stringify({ form: next, scheduleRows }),
          );
        } catch {}
      }
      return next;
    });
    if (key === "startDate" || key === "endDate") {
      const first = key === "startDate" ? value : form.startDate;
      const last = key === "endDate" ? value : form.endDate;
      setScheduleRows((rows) => {
        const nextRows = rows.filter(
          (row) => row.date >= first && row.date <= last,
        );
        if (!editing) {
          try {
            localStorage.setItem(
              draftKey,
              JSON.stringify({
                form: { ...form, [key]: value },
                scheduleRows: nextRows,
              }),
            );
          } catch {}
        }
        return nextRows;
      });
    }
  }
  async function editOffering(row) {
    setForm({ ...initial, ...row });
    setEditing(row.id);
    setScheduleRows([]);
    setError("");
    setOpen(true);
    try {
      const current = await api.get(
        `/instructor/offerings/${row.id}/sessions`,
        token,
      );
      setScheduleRows(
        current
          .filter((session) => session.status !== "CANCELLED")
          .map((session) => ({
            date: session.startsAt.slice(0, 10),
            start: session.startsAt.slice(11, 16),
            end: session.endsAt.slice(11, 16),
            title: session.title,
          })),
      );
    } catch (e) {
      setError(e.message);
    }
  }
  async function save(event) {
    event.preventDefault();
    setBusy(true);
    setError("");
    const payload = {
      ...form,
      courseId: Number(form.courseId),
      instructorId: form.instructorId ? Number(form.instructorId) : null,
      capacity: Number(form.capacity),
      price: Number(form.price),
      registrationClosesAt: form.registrationClosesAt || null,
    };
    try {
      if (!scheduleRows.length)
        throw new Error("Chọn ngày và giờ để tạo ít nhất một buổi học.");
      const sessions = [...scheduleRows]
        .sort((a, b) =>
          `${a.date}T${a.start}`.localeCompare(`${b.date}T${b.start}`),
        )
        .map((row, index) => ({
          sessionNumber: index + 1,
          title: row.title,
          startsAt: `${row.date}T${row.start}:00`,
          endsAt: `${row.date}T${row.end}:00`,
          meetingProvider: "GOOGLE_MEET",
          meetingUrl: null,
          notes: null,
        }));
      if (editing) {
        await api.put(`/instructor/offerings/${editing}`, payload, token);
        try {
          await api.put(
            `/instructor/offerings/${editing}/schedule`,
            { sessions },
            token,
          );
        } catch (scheduleErr) {
          if (scheduleErr.status === 409) {
            setNotice(
              "Đã cập nhật thông tin lớp. Do lớp đã có học viên đăng ký, bạn hãy chuyển sang mục 'Buổi học' để chỉnh sửa chi tiết từng buổi.",
            );
            setOpen(false);
            setScheduleRows([]);
            await data.reload();
            return;
          }
          throw scheduleErr;
        }
      } else {
        const saved = await api.post("/instructor/offerings", payload, token);
        await api.post(
          `/instructor/offerings/${saved.id}/schedule`,
          { sessions },
          token,
        );
        clearDraft();
      }
      setOpen(false);
      setNotice(
        editing
          ? "Đã cập nhật lớp và lịch học thành công."
          : "Đã tạo lớp và lưu lịch học thành công.",
      );
      setScheduleRows([]);
      await data.reload();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function action() {
    setBusy(true);
    try {
      await api.post(
        `/instructor/offerings/${confirmation.id}/${confirmation.action}`,
        undefined,
        token,
      );
      setNotice(confirmation.success || "");
      setError("");
      setConfirmation(null);
      await data.reload();
    } catch (e) {
      setError(e.message);
      setConfirmation(null);
    } finally {
      setBusy(false);
    }
  }
  const input = (key, title, type = "text") => (
    <label>
      {title}
      <input
        type={type}
        required
        value={form[key] ?? ""}
        onChange={(e) => field(key, e.target.value)}
      />
    </label>
  );
  if (data.loading) return <Loading />;
  if (data.error) return <ErrorState error={data.error} retry={data.reload} />;
  return (
    <Shell
      title="Quản lý lớp mở bán"
      subtitle="Chọn khóa học, giảng viên và lịch khai giảng."
      actions={
        <Button onClick={openCreate}>
          <Plus size={16} />
          Thêm lớp
        </Button>
      }
    >
      <ActionNotice onDismiss={() => setNotice("")}>{notice}</ActionNotice>
      {error && !open && <div className="alert">{error}</div>}
      <DataTable
        rows={(data.data || []).filter(
          (row) =>
            !params.get("courseId") ||
            row.courseId === Number(params.get("courseId")),
        )}
        columns={[
          { key: "id", label: "Mã" },
          { key: "code", label: "Mã lớp" },
          { key: "cohortName", label: "Tên lớp" },
          {
            key: "startDate",
            label: "Khai giảng",
            render: (row) => date(row.startDate),
          },
          { key: "price", label: "Học phí", render: (row) => money(row.price) },
          { key: "status", label: "Trạng thái" },
          {
            key: "actions",
            label: "Thao tác",
            render: (row) => {
              if (!canManage(row)) return null;
              if (row.status === "CANCELLED")
                return <span className="text-sm text-slate-400">Đã hủy</span>;
              if (row.status === "COMPLETED")
                return <span className="text-sm text-slate-400">Đã hoàn thành</span>;
              const ended = row.endDate && row.endDate < today();
              const statusAction = {
                DRAFT: ACTIONS.publish,
                CLOSED: ACTIONS.reopen,
                OPEN: ACTIONS.close,
              }[row.status];
              return (
                <div className="action-buttons">
                  <Button variant="secondary" onClick={() => editOffering(row)}>
                    <Pencil size={15} />
                    Lịch học / Sửa
                  </Button>
                  {statusAction && (
                    <Button
                      variant="secondary"
                      onClick={() => setConfirmation({ id: row.id, ...statusAction })}
                    >
                      {statusAction.label}
                    </Button>
                  )}
                  {ended && ["OPEN", "CLOSED"].includes(row.status) && (
                    <Button
                      variant="secondary"
                      onClick={() => setConfirmation({ id: row.id, ...ACTIONS.complete })}
                    >
                      <Check size={15} />
                      Hoàn thành
                    </Button>
                  )}
                  <Button
                    variant="danger"
                    onClick={() => setConfirmation({ id: row.id, ...ACTIONS.cancel })}
                  >
                    <X size={15} />
                    Hủy
                  </Button>
                </div>
              );
            },
          },
        ]}
      />
      <Modal
        title={editing ? "Sửa lớp học" : "Thêm lớp học"}
        open={open}
        onClose={() => {
          if (!busy) setOpen(false);
        }}
      >
        <form className="form" onSubmit={save}>
          {!editing && draftRestored && (
            <div className="flex items-center justify-between rounded-lg bg-blue-500/10 border border-blue-500/30 px-3 py-2 text-xs text-blue-300">
              <span>Đã tự động khôi phục thông tin lớp đang nhập dở trước đó.</span>
              <button
                type="button"
                className="font-medium text-blue-400 underline hover:text-blue-200 ml-2"
                onClick={startOver}
              >
                Xóa làm lại
              </button>
            </div>
          )}
          {error && <div className="alert">{error}</div>}
          {!editing && (
            <>
              <label>
                Khóa học
                <select
                  required
                  value={form.courseId}
                  onChange={(e) => field("courseId", e.target.value)}
                >
                  <option value="">Chọn khóa học</option>
                  {(courses.data || [])
                    .filter((row) => row.status !== "ARCHIVED")
                    .map((row) => (
                      <option key={row.id} value={row.id}>
                        {row.title}
                      </option>
                    ))}
                </select>
              </label>
              {admin && (
                <label>
                  Giảng viên
                  <select
                    required
                    value={form.instructorId}
                    onChange={(e) => field("instructorId", e.target.value)}
                  >
                    <option value="">Chọn giảng viên</option>
                    {(teachers.data?.items || [])
                      .filter((row) => row.hasInstructorRole)
                      .map((row) => (
                        <option key={row.id} value={row.id}>
                          {row.fullName} — {row.email}
                        </option>
                      ))}
                  </select>
                </label>
              )}
              {input("offeringCode", "Mã lớp")}
            </>
          )}
          {input("cohortName", "Tên lớp")}
          {input("startDate", "Ngày bắt đầu", "date")}
          {input("endDate", "Ngày kết thúc", "date")}
          <section className="rounded-xl border border-blue-200 bg-blue-50/50 p-4">
            <h2 className="mb-2">Lịch học của lớp</h2>
            <p className="text-sm text-slate-600">
              Chọn ngày trong tuần và khung giờ; xem trước từng buổi rồi lưu
              chung với thông tin lớp.
            </p>
            {editing && (
              <p className="text-sm text-slate-600">
                Bạn có thể đổi ngày giờ, thêm hoặc bớt buổi kể cả khi lớp đã có
                học viên. Khi lưu, học viên sẽ nhận thông báo về lịch mới.
              </p>
            )}
            {form.startDate && form.endDate ? (
              <ScheduleBuilder
                key={`${editing || "new"}-${form.startDate}-${form.endDate}`}
                offering={{
                  ...form,
                  id: editing || 0,
                  code: form.offeringCode,
                }}
                initialRows={scheduleRows}
                onRowsChange={(rows) => {
                  setScheduleRows(rows);
                  if (!editing) {
                    try {
                      localStorage.setItem(
                        draftKey,
                        JSON.stringify({ form, scheduleRows: rows }),
                      );
                    } catch {}
                  }
                  if (rows.length) {
                    const days = [
                      ...new Set(
                        rows.map((row) =>
                          new Date(`${row.date}T12:00:00`).getDay(),
                        ),
                      ),
                    ].sort((a, b) => a - b);
                    const names = [
                      "CN",
                      "Thứ 2",
                      "Thứ 3",
                      "Thứ 4",
                      "Thứ 5",
                      "Thứ 6",
                      "Thứ 7",
                    ];
                    field(
                      "scheduleSummary",
                      `${days.map((day) => names[day]).join(", ")} · ${rows[0].start}–${rows[0].end}`,
                    );
                  }
                }}
              />
            ) : (
              <p className="text-sm text-amber-700">
                Chọn ngày bắt đầu và ngày kết thúc trước để mở lịch xem trước.
              </p>
            )}
            <p className="text-sm font-medium">
              Đã chọn {scheduleRows.length} buổi
            </p>
          </section>
          <label>
            Hạn đăng ký
            <input
              type="datetime-local"
              value={form.registrationClosesAt || ""}
              onChange={(e) => field("registrationClosesAt", e.target.value)}
            />
          </label>
          {input("scheduleSummary", "Mô tả lịch học")}
          {input("capacity", "Sĩ số", "number")}
          {input("price", "Học phí (VND)", "number")}
          <Button
            type="submit"
            busy={busy}
            disabled={busy || !scheduleRows.length}
          >
            {busy
              ? "Đang lưu lớp và lịch…"
              : editing
                ? "Lưu lớp và lịch học"
                : "Tạo lớp và lịch học"}
          </Button>
        </form>
      </Modal>
      <ConfirmDialog
        open={Boolean(confirmation)}
        title={confirmation?.title}
        onClose={() => setConfirmation(null)}
        onConfirm={action}
        busy={busy}
      >
        {confirmation?.text}
      </ConfirmDialog>
    </Shell>
  );
}
