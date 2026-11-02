import { api } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import { Button } from "../../components/ui";
import { date } from "../../utils/format";
import { useState, useMemo } from "react";
import useApi from "../../hooks/useApi";
import Shell from "../../components/common/Shell";
import DataTable from "../../components/tables/DataTable";
import { Loading, ErrorState } from "../../components/common/State";

export default function Enrollments() {
  const { token, user } = useAuth();
  const teacherProfile = useApi(
    user.roles.includes("ADMIN") ? null : "/instructor/profile",
    true,
  );
  const [announcement, setAnnouncement] = useState({ title: "", body: "" }),
    [notice, setNotice] = useState(""),
    [busy, setBusy] = useState(false);
  const offerings = useApi("/instructor/offerings", true);
  const [selected, setSelected] = useState("");
  const sendKey = useMemo(
    () => crypto.randomUUID(),
    [selected, announcement.title, announcement.body],
  );
  const learners = useApi(
    selected ? `/instructor/offerings/${selected}/enrollments` : null,
    true,
  );
  if (offerings.loading) return <Loading />;
  if (offerings.error)
    return <ErrorState error={offerings.error} retry={offerings.reload} />;
  return (
    <Shell title="Học viên">
      <label>
        Lớp học
        <select
          value={selected}
          onChange={(event) => setSelected(event.target.value)}
        >
          <option value="">Chọn lớp học</option>
          {(offerings.data || []).map((row) => (
            <option key={row.id} value={row.id}>
              {row.code || row.id}
            </option>
          ))}
        </select>
      </label>
      {selected &&
        (learners.loading ? (
          <Loading />
        ) : learners.error ? (
          <ErrorState error={learners.error} retry={learners.reload} />
        ) : (
          <DataTable
            rows={learners.data || []}
            columns={[
              { key: "id", label: "Mã ghi danh" },
              { key: "fullName", label: "Họ tên" },
              { key: "email", label: "Email" },
              { key: "status", label: "Trạng thái" },
              {
                key: "enrolledAt",
                label: "Ngày đăng ký",
                render: (row) => date(row.enrolledAt),
              },
            ]}
          />
        ))}
      {selected &&
        (user.roles.includes("ADMIN") ||
          (offerings.data || []).find((row) => row.id === Number(selected))
            ?.instructorId === teacherProfile.data?.id) && (
          <form
            className="panel form"
            onSubmit={async (event) => {
              event.preventDefault();
              setBusy(true);
              try {
                const result = await api.post(
                  "/instructor/notification-campaigns",
                  {
                    ...announcement,
                    type: "ANNOUNCEMENT",
                    audience: "OFFERING_ACTIVE",
                    offeringId: Number(selected),
                    courseId: null,
                  },
                  token,
                  { "Idempotency-Key": sendKey },
                );
                setNotice(
                  `Đã gửi thông báo đến ${result.recipient_count ?? result.recipientCount ?? 0} học viên đang học.`,
                );
                setAnnouncement({ title: "", body: "" });
              } catch (e) {
                setNotice(e.message);
              } finally {
                setBusy(false);
              }
            }}
          >
            <h2>Phát thông báo cho lớp</h2>
            {notice && <p role="status">{notice}</p>}
            <label>
              Tiêu đề
              <input
                required
                maxLength={200}
                value={announcement.title}
                onChange={(e) =>
                  setAnnouncement({ ...announcement, title: e.target.value })
                }
              />
            </label>
            <label>
              Nội dung
              <textarea
                required
                maxLength={10000}
                value={announcement.body}
                onChange={(e) =>
                  setAnnouncement({ ...announcement, body: e.target.value })
                }
              />
            </label>
            <Button type="submit" busy={busy}>
              Gửi đến học viên của lớp
            </Button>
          </form>
        )}
    </Shell>
  );
}
