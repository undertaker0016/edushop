import { useSearchParams } from "react-router-dom";
import { useEffect, useState } from "react";
import { api } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import Shell from "../../components/common/Shell";
import DataTable from "../../components/tables/DataTable";
import { ErrorState, Loading, Empty } from "../../components/common/State";
import { downloadClassCalendar } from "../../utils/calendar";
import { date } from "../../utils/format";
import { Link } from "react-router-dom";

export default function Classroom() {
  const { token } = useAuth();
  const [params] = useSearchParams();
  const [rows, setRows] = useState([]);
  const [sessions, setSessions] = useState([]);
  const [selected, setSelected] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [sessionLoading, setSessionLoading] = useState(false);
  const [sessionError, setSessionError] = useState(null);
  const [retry, setRetry] = useState(0);
  const [message, setMessage] = useState("");
  const load = async () => {
    setError(null);
    setLoading(true);
    return api
      .get("/enrollments", token)
      .then(setRows)
      .catch(setError)
      .finally(() => setLoading(false));
  };
  useEffect(() => {
    let refreshTimer;
    const refreshIfVisible = () => {
      if (document.visibilityState === "visible") load();
    };
    const handleVisibility = () => {
      if (document.visibilityState === "visible") {
        refreshIfVisible();
        clearInterval(refreshTimer);
        refreshTimer = setInterval(refreshIfVisible, 15000);
      } else {
        clearInterval(refreshTimer);
      }
    };
    window.addEventListener("focus", refreshIfVisible);
    document.addEventListener("visibilitychange", handleVisibility);
    handleVisibility();
    return () => {
      clearInterval(refreshTimer);
      window.removeEventListener("focus", refreshIfVisible);
      document.removeEventListener("visibilitychange", handleVisibility);
    };
  }, [token]);
  useEffect(() => {
    if (!selected) return;
    let active = true;
    setSessions([]);
    setSessionLoading(true);
    setSessionError(null);
    setMessage("");
    api
      .get(`/enrollments/${selected}/sessions`, token)
      .then((value) => {
        if (active) setSessions(value);
      })
      .catch((error) => {
        if (active) setSessionError(error);
      })
      .finally(() => {
        if (active) setSessionLoading(false);
      });
    return () => {
      active = false;
    };
  }, [selected, token, retry]);
  useEffect(() => {
    const id = Number(params.get("enrollmentId"));
    if (id && rows.some((row) => row.id === id)) {
      setSelected(id);
    }
  }, [params, rows, token]);
  if (error) return <ErrorState error={error} retry={load} />;
  if (loading) return <Loading />;
  return (
    <Shell title="Lớp học của tôi">
      <DataTable
        rows={rows}
        columns={[
          { key: "id", label: "Mã ghi danh" },
          {
            key: "courseTitle",
            label: "Khóa học",
            render: (row) => (
              <div className="grid justify-items-start gap-1">
                <Link
                  className="font-semibold text-blue-700 hover:underline"
                  to={`/courses/${row.courseSlug}`}
                >
                  {row.courseTitle}
                </Link>
                <Link
                  className="text-xs text-blue-600 hover:underline"
                  to={`/buyer/reviews/${row.courseId}`}
                >
                  Đánh giá khóa học
                </Link>
              </div>
            ),
          },
          { key: "offeringCode", label: "Lớp" },
          {
            key: "enrolledAt",
            label: "Ngày đăng ký",
            render: (row) => date(row.enrolledAt),
          },
          {
            key: "startDate",
            label: "Khai giảng",
            render: (row) => date(row.startDate),
          },
          { key: "status", label: "Trạng thái" },
          {
            key: "action",
            label: "Lịch học",
            render: (row) => (
              <button
                className="btn secondary"
                onClick={() => setSelected(row.id)}
                disabled={row.status !== "ACTIVE"}
              >
                Xem lịch
              </button>
            ),
          },
        ]}
      />
      {selected && (
        <>
          <section className="panel my-5">
            <h2>{rows.find((row) => row.id === selected)?.courseTitle}</h2>
            <p>Lớp {rows.find((row) => row.id === selected)?.offeringCode}</p>
            <button
              type="button"
              className="btn secondary"
              disabled={sessionLoading || !!sessionError || !sessions.length}
              onClick={() => {
                try {
                  downloadClassCalendar(
                    rows.find((row) => row.id === selected),
                    sessions,
                  );
                  setMessage(
                    "Đã tạo file lịch. Nhập file .ics vào Google Calendar hoặc Outlook để lưu lịch và đặt nhắc học.",
                  );
                } catch (error) {
                  setSessionError(error);
                }
              }}
            >
              Tải lịch học (.ics)
            </button>
            <p className="text-xs text-slate-500">
              Tương thích Google Calendar và Outlook, không cần API key. Lịch
              tải về không tự cập nhật; tải lại nếu giảng viên thay đổi giờ học.
              File không chứa mật khẩu hay link phòng riêng.
            </p>
            <a
              className="text-sm text-blue-600"
              href="https://support.google.com/calendar/answer/37118?hl=vi"
              target="_blank"
              rel="noreferrer"
            >
              Hướng dẫn nhập vào Google Calendar
            </a>
            {message && (
              <p role="status" className="success">
                {message}
              </p>
            )}
            {sessionLoading && <Loading />}
            {sessionError && (
              <ErrorState
                error={sessionError}
                retry={() => setRetry((value) => value + 1)}
              />
            )}
            {!sessionLoading && !sessionError && !sessions.length && (
              <Empty>Giảng viên chưa công bố buổi học.</Empty>
            )}
            <div className="grid gap-3 sm:grid-cols-2">
              {sessions.map((session) => (
                <article
                  className="rounded-lg border border-blue-100 bg-blue-50 p-3"
                  key={session.sessionNumber}
                >
                  <strong>
                    Buổi {session.sessionNumber}: {session.title}
                  </strong>
                  <p>
                    {date(session.startsAt)} → {date(session.endsAt)}
                  </p>
                </article>
              ))}
            </div>
          </section>
          <h2 className="mb-3">Lịch chi tiết</h2>
          <DataTable
            rows={sessions}
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
              {
                key: "meetingUrl",
                label: "Link lớp",
                render: (row) =>
                  row.meetingUrl ? (
                    <a href={row.meetingUrl} target="_blank" rel="noreferrer">
                      Tham gia
                    </a>
                  ) : (
                    "Không có"
                  ),
              },
            ]}
          />
        </>
      )}
    </Shell>
  );
}
