import { useEffect, useRef, useState } from "react";
import { Eye, Send } from "lucide-react";
import { api } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import Shell from "../common/Shell";
import DataTable from "../tables/DataTable";
import { Button, Modal, Pagination } from "../ui";

const audiences = {
  ALL_ACTIVE: "Tất cả tài khoản đang hoạt động",
  BUYERS: "Tài khoản người mua (không có vai trò quản trị/giảng viên)",
  INSTRUCTORS: "Giảng viên đang hoạt động",
  COURSE_ACTIVE: "Học viên đang ghi danh trong khóa học",
  OFFERING_ACTIVE: "Học viên đang ghi danh trong lớp",
  COURSE_PAID: "Người có đơn đã thanh toán trong khóa học",
  OFFERING_PAID: "Người có đơn đã thanh toán trong lớp",
};

export default function NotificationCampaigns({ instructor = false }) {
  const { token } = useAuth();
  const base = `/${instructor ? "instructor" : "admin"}/notification-campaigns`;
  const initial = {
    title: "",
    body: "",
    type: "ANNOUNCEMENT",
    audience: instructor ? "OFFERING_ACTIVE" : "ALL_ACTIVE",
    courseId: null,
    offeringId: null,
  };
  const [form, setForm] = useState(initial);
  const [query, setQuery] = useState("");
  const [options, setOptions] = useState([]);
  const [preview, setPreview] = useState(null);
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [history, setHistory] = useState(null);
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState("");
  const retryKey = useRef(null);
  const inFlight = useRef(false);
  const kind = form.audience.startsWith("COURSE_")
    ? "courses"
    : form.audience.startsWith("OFFERING_")
      ? "offerings"
      : null;

  async function loadHistory() {
    try {
      setHistory(
        await api.get(`${base}?page=${page}&audience=${filter}`, token),
      );
    } catch (caught) {
      setError(caught.message);
    }
  }
  useEffect(() => {
    loadHistory();
  }, [base, page, filter, token]);
  useEffect(() => {
    if (!kind) {
      setOptions([]);
      return;
    }
    let active = true;
    const timer = setTimeout(async () => {
      try {
        const result = await api.get(
          `${base}/options/${kind}?q=${encodeURIComponent(query)}`,
          token,
        );
        if (active) setOptions(result);
      } catch (caught) {
        if (active) setError(caught.message);
      }
    }, 250);
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [base, kind, query, token]);

  function change(values) {
    setForm((previous) => ({ ...previous, ...values }));
    setPreview(null);
    retryKey.current = null;
    setMessage("");
  }
  async function inspect(event) {
    event.preventDefault();
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setError("");
    try {
      setPreview(await api.post(`${base}/preview`, form, token));
    } catch (caught) {
      setError(caught.message);
    } finally {
      setBusy(false);
      inFlight.current = false;
    }
  }
  async function send() {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setError("");
    retryKey.current ||= crypto.randomUUID();
    try {
      const result = await api.post(base, form, token, {
        "Idempotency-Key": retryKey.current,
      });
      setMessage(
        `Đã gửi tới ${result.recipient_count} người nhận. Mã đợt gửi: ${result.id}.`,
      );
      setPreview(null);
      setConfirm(false);
      setForm(initial);
      retryKey.current = null;
      await loadHistory();
    } catch (caught) {
      setError(caught.message);
      setConfirm(false);
    } finally {
      setBusy(false);
      inFlight.current = false;
    }
  }

  return (
    <Shell
      title="Đợt gửi thông báo"
      subtitle={
        instructor
          ? "Gửi tới học viên của lớp bạn được phân công."
          : "Chọn nhóm người nhận, kiểm tra nội dung và xác nhận trước khi gửi."
      }
    >
      {error && (
        <div className="alert" role="alert">
          {error}
          <Button
            variant="secondary"
            onClick={() => {
              setError("");
              loadHistory();
            }}
          >
            Thử tải lại
          </Button>
        </div>
      )}
      {message && (
        <p className="success" role="status">
          {message}
        </p>
      )}
      <div className="grid gap-6 xl:grid-cols-2">
        <form className="panel form" onSubmit={inspect}>
          <h2>Soạn thông báo</h2>
          <label>
            Đối tượng nhận *
            <select
              disabled={busy}
              value={form.audience}
              onChange={(e) => {
                change({
                  audience: e.target.value,
                  courseId: null,
                  offeringId: null,
                });
                setQuery("");
                setOptions([]);
              }}
            >
              {Object.entries(audiences)
                .filter(([key]) => !instructor || key === "OFFERING_ACTIVE")
                .map(([key, text]) => (
                  <option key={key} value={key}>
                    {text}
                  </option>
                ))}
            </select>
          </label>
          {kind && (
            <>
              <label>
                Tìm {kind === "courses" ? "khóa học" : "lớp học"}
                <input
                  disabled={busy}
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="Nhập tên khóa học hoặc mã lớp…"
                />
              </label>
              <label>
                Chọn {kind === "courses" ? "khóa học" : "lớp học"} *
                <select
                  disabled={busy}
                  required
                  value={form.courseId || form.offeringId || ""}
                  onChange={(e) =>
                    change({
                      [kind === "courses" ? "courseId" : "offeringId"]:
                        Number(e.target.value) || null,
                    })
                  }
                >
                  <option value="">Chọn từ kết quả tìm kiếm</option>
                  {options.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name}
                    </option>
                  ))}
                </select>
              </label>
            </>
          )}
          <label>
            Loại thông báo *
            <select
              disabled={busy}
              value={form.type}
              onChange={(e) => change({ type: e.target.value })}
            >
              <option value="ANNOUNCEMENT">Thông báo chung</option>
              <option value="CLASS_UPDATE">Cập nhật lớp học</option>
              <option value="SYSTEM">Thông tin hệ thống</option>
            </select>
          </label>
          <label>
            Tiêu đề *
            <input
              disabled={busy}
              required
              maxLength={200}
              value={form.title}
              onChange={(e) => change({ title: e.target.value })}
            />
          </label>
          <label>
            Nội dung *
            <textarea
              disabled={busy}
              required
              maxLength={10000}
              rows={6}
              value={form.body}
              onChange={(e) => change({ body: e.target.value })}
            />
          </label>
          <Button type="submit" busy={busy}>
            <Eye size={18} aria-hidden="true" />
            Xem trước người nhận
          </Button>
        </form>
        <section className="panel form" aria-live="polite">
          <h2>Xem trước</h2>
          <h3>{form.title || "Tiêu đề thông báo"}</h3>
          <p className="whitespace-pre-wrap break-words">
            {form.body ||
              "Nội dung sẽ hiển thị tại trung tâm thông báo của người nhận."}
          </p>
          {preview ? (
            <>
              <p>
                <strong>{preview.count}</strong> người nhận hợp lệ tại thời điểm
                kiểm tra.
              </p>
              <ul>
                {preview.sample.map((item, index) => (
                  <li key={index}>{item.name}</li>
                ))}
              </ul>
              {!preview.count && (
                <p className="alert">
                  Nhóm này chưa có người nhận hợp lệ. Chọn lại nhóm hoặc lớp
                  học.
                </p>
              )}
              <p className="text-sm text-slate-500">
                Danh sách được chốt lại khi gửi. Mỗi tài khoản chỉ nhận một
                thông báo trong đợt gửi.
              </p>
              <Button
                disabled={!preview.count || busy}
                onClick={() => setConfirm(true)}
              >
                <Send size={18} aria-hidden="true" />
                Gửi thông báo
              </Button>
            </>
          ) : (
            <p>Hoàn thành biểu mẫu và chọn “Xem trước người nhận”.</p>
          )}
        </section>
      </div>
      <section className="mt-8">
        <h2>Lịch sử đợt gửi</h2>
        <div className="filters">
          <label>
            Đối tượng nhận
            <select
              value={filter}
              onChange={(e) => {
                setFilter(e.target.value);
                setPage(0);
              }}
            >
              <option value="">Tất cả nhóm</option>
              {Object.entries(audiences)
                .filter(([key]) => !instructor || key === "OFFERING_ACTIVE")
                .map(([key, text]) => (
                  <option key={key} value={key}>
                    {text}
                  </option>
                ))}
            </select>
          </label>
        </div>
        <DataTable
          rows={history?.items || []}
          columns={[
            { key: "id", label: "ID" },
            { key: "title", label: "Tiêu đề" },
            {
              key: "audience",
              label: "Nhóm nhận",
              render: (row) => audiences[row.audience],
            },
            {
              key: "status",
              label: "Trạng thái",
              render: (row) => (row.status === "SENT" ? "Đã gửi" : "Đang gửi"),
            },
            { key: "recipient_count", label: "Thành công" },
            { key: "sent_at", label: "Ngày gửi" },
          ]}
        />
        {history && (
          <Pagination
            page={page}
            totalPages={history.totalPages}
            onChange={setPage}
          />
        )}
      </section>
      <Modal
        open={confirm}
        onClose={() => {
          if (!busy) setConfirm(false);
        }}
        title="Xác nhận gửi theo nhóm"
      >
        <p>
          Gửi “{form.title}” tới nhóm “{audiences[form.audience]}”? Dự kiến{" "}
          {preview?.count} người nhận.
        </p>
        <p className="text-sm text-slate-500">
          Thao tác này tạo thông báo ngay trong tài khoản người nhận.
        </p>
        <div className="mt-5 flex gap-3">
          <Button
            variant="secondary"
            disabled={busy}
            onClick={() => setConfirm(false)}
          >
            Quay lại
          </Button>
          <Button busy={busy} onClick={send}>
            Xác nhận gửi
          </Button>
        </div>
      </Modal>
    </Shell>
  );
}
