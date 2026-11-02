import { Link } from "react-router-dom";
import {
  BookOpen,
  Calendar,
  CalendarClock,
  CalendarPlus,
  FilePlus2,
  FileEdit,
  Users,
  Armchair,
  Hourglass,
  Rocket,
} from "lucide-react";
import useApi from "../../hooks/useApi";
import { useAuth } from "../../contexts/AuthContext";
import Shell from "../../components/common/Shell";
import CourseImage from "../../components/common/CourseImage";
import { ErrorState } from "../../components/common/State";
import { StatusBadge } from "../../components/ui";
import {
  AttentionList,
  DashboardHero,
  DashboardSkeleton,
  DashPanel,
  DateChip,
  SkeletonBlock,
  StatCard,
  StatusBreakdown,
  countOf,
  daysUntil,
  greeting,
  sum,
  todayLabel,
} from "../../components/dashboard";
import { date, money } from "../../utils/format";

function relativeDay(days) {
  if (days === null) return "";
  if (days === 0) return "Hôm nay";
  if (days === 1) return "Ngày mai";
  if (days > 1) return `Còn ${days} ngày`;
  return `${-days} ngày trước`;
}

export default function InstructorHome() {
  const { user } = useAuth();
  const data = useApi("/instructor/dashboard", true);
  const offerings = useApi("/instructor/offerings", true);
  const courses = useApi("/instructor/courses", true);

  if (data.loading) return <DashboardSkeleton />;
  if (data.error)
    return (
      <main className="page">
        <ErrorState error={data.error} retry={data.reload} />
      </main>
    );

  const d = data.data || {};
  const courseRows = courses.data || [];
  const offeringRows = offerings.data || [];
  const titleOf = Object.fromEntries(courseRows.map((course) => [course.id, course.title]));
  const offeringCount = Object.create(null);
  offeringRows.forEach((row) => {
    offeringCount[row.courseId] = (offeringCount[row.courseId] || 0) + 1;
  });

  const upcoming = offeringRows
    .filter((row) => !["CANCELLED", "COMPLETED"].includes(row.status))
    .map((row) => ({ ...row, days: daysUntil(row.startDate) }))
    .filter((row) => row.days !== null && row.days >= 0)
    .sort((a, b) => a.days - b.days);
  const next = upcoming[0];
  const openSeats = offeringRows
    .filter((row) => row.status === "OPEN")
    .reduce((total, row) => total + Number(row.capacity || 0), 0);
  const closingSoon = offeringRows.filter((row) => {
    if (row.status !== "OPEN" || !row.registrationClosesAt) return false;
    const days = daysUntil(row.registrationClosesAt);
    return days !== null && days >= 0 && days <= 3;
  });
  const firstName = user?.fullName?.trim().split(/\s+/).at(-1) || "";

  return (
    <Shell title="Tổng quan giảng viên" subtitle="Nội dung giảng dạy và lớp học do bạn phụ trách.">
      <DashboardHero
        eyebrow={todayLabel()}
        title={`${greeting()}, ${firstName}!`}
        description={
          d.activeEnrollments
            ? `Bạn đang đồng hành cùng ${d.activeEnrollments} học viên. Chúc một ngày giảng dạy hiệu quả!`
            : "Tạo khóa học, mở lớp và lên lịch để bắt đầu đón học viên."
        }
        actions={
          <>
            <Link className="btn" to="/instructor/courses">
              <FilePlus2 size={16} /> Quản lý khóa học
            </Link>
            <Link className="btn secondary" to="/instructor/offerings">
              <CalendarPlus size={16} /> Mở lớp mới
            </Link>
            <Link className="btn secondary" to="/instructor/sessions">
              <CalendarClock size={16} /> Lịch buổi học
            </Link>
          </>
        }
        aside={
          <div className="dash-highlight">
            <span>Lớp sắp khai giảng</span>
            {offerings.loading ? (
              <strong>…</strong>
            ) : next ? (
              <>
                <strong>{relativeDay(next.days)}</strong>
                <small>
                  {next.cohortName || next.code} · {titleOf[next.courseId] || `Khóa #${next.courseId}`} · {date(next.startDate)}
                </small>
              </>
            ) : (
              <>
                <strong>Chưa có lớp</strong>
                <small>
                  <Link to="/instructor/offerings">Mở lớp đầu tiên</Link>
                </small>
              </>
            )}
          </div>
        }
      />

      <div className="dash-stats">
        <StatCard
          icon={BookOpen}
          tone="navy"
          label="Khóa học"
          value={sum(d.coursesByStatus)}
          hint={`${countOf(d.coursesByStatus, "PUBLISHED")} đã xuất bản · ${countOf(d.coursesByStatus, "DRAFT")} nháp`}
          to="/instructor/courses"
        />
        <StatCard
          icon={Calendar}
          tone="info"
          label="Lớp mở bán"
          value={sum(d.offeringsByStatus)}
          hint={`${countOf(d.offeringsByStatus, "OPEN")} đang mở · ${countOf(d.offeringsByStatus, "COMPLETED")} hoàn thành`}
          to="/instructor/offerings"
        />
        <StatCard icon={Users} tone="success" label="Học viên đang học" value={d.activeEnrollments ?? 0} to="/instructor/enrollments" />
        <StatCard
          icon={Armchair}
          tone="violet"
          label="Chỗ đang mở bán"
          value={offerings.loading ? "…" : openSeats}
          hint="Tổng sĩ số các lớp đang mở"
          to="/instructor/offerings"
        />
      </div>

      <div className="dash-grid">
        <DashPanel
          className="span-8"
          title="Lớp sắp khai giảng"
          description="Các lớp chưa bắt đầu, sắp xếp theo ngày khai giảng."
          action={{ to: "/instructor/offerings", label: "Tất cả lớp" }}
        >
          {offerings.loading || courses.loading ? (
            <SkeletonBlock lines={5} />
          ) : offerings.error ? (
            <ErrorState error={offerings.error} retry={offerings.reload} />
          ) : upcoming.length ? (
            <ul className="dash-list">
              {upcoming.slice(0, 5).map((row) => (
                <li className="dash-row" key={row.id}>
                  <DateChip value={row.startDate} />
                  <div className="dash-row-main">
                    <strong title={titleOf[row.courseId]}>{titleOf[row.courseId] || `Khóa #${row.courseId}`}</strong>
                    <div className="dash-row-meta">
                      <span>{row.cohortName || row.code}</span>
                      {row.scheduleSummary && <span>{row.scheduleSummary}</span>}
                      <span>
                        <Users size={12} /> {row.capacity ?? "—"} chỗ
                      </span>
                    </div>
                  </div>
                  <div className="dash-row-end">
                    <StatusBadge value={row.status} />
                    <small>
                      {relativeDay(row.days)} · {money(row.price)}
                    </small>
                  </div>
                </li>
              ))}
            </ul>
          ) : (
            <p className="dash-empty">
              Chưa có lớp nào sắp khai giảng. <Link to="/instructor/offerings">Mở lớp mới</Link>
            </p>
          )}
        </DashPanel>

        <DashPanel className="span-4" title="Việc cần làm" description="Gợi ý để lớp học sẵn sàng đón học viên.">
          {offerings.loading ? (
            <SkeletonBlock lines={3} />
          ) : (
            <AttentionList
              doneText="Mọi thứ đã sẵn sàng!"
              items={[
                {
                  key: "draft-courses",
                  icon: FileEdit,
                  tone: "info",
                  title: "Khóa học bản nháp",
                  description: "Hoàn thiện và gửi xuất bản",
                  count: countOf(d.coursesByStatus, "DRAFT"),
                  to: "/instructor/courses",
                },
                {
                  key: "draft-offerings",
                  icon: Rocket,
                  tone: "violet",
                  title: "Lớp chưa mở bán",
                  description: "Lớp ở trạng thái nháp",
                  count: countOf(d.offeringsByStatus, "DRAFT"),
                  to: "/instructor/offerings",
                },
                {
                  key: "closing",
                  icon: Hourglass,
                  tone: "warning",
                  title: "Sắp hết hạn đăng ký",
                  description: "Hạn đăng ký trong 3 ngày tới",
                  count: closingSoon.length,
                  to: "/instructor/offerings",
                },
              ]}
            />
          )}
        </DashPanel>
      </div>

      <div className="dash-grid">
        <DashPanel className="span-6" title="Khóa học theo trạng thái" action={{ to: "/instructor/courses", label: "Chi tiết" }}>
          <StatusBreakdown rows={d.coursesByStatus} emptyText="Bạn chưa tạo khóa học nào." />
        </DashPanel>
        <DashPanel className="span-6" title="Lớp học theo trạng thái" action={{ to: "/instructor/offerings", label: "Chi tiết" }}>
          <StatusBreakdown rows={d.offeringsByStatus} emptyText="Bạn chưa mở lớp nào." />
        </DashPanel>
      </div>

      <DashPanel title="Khóa học của bạn" description="Cập nhật gần đây nhất." action={{ to: "/instructor/courses", label: "Quản lý" }}>
        {courses.loading ? (
          <SkeletonBlock lines={4} />
        ) : courses.error ? (
          <ErrorState error={courses.error} retry={courses.reload} />
        ) : courseRows.length ? (
          <div className="dash-course-grid">
            {[...courseRows]
              .sort((a, b) => new Date(b.updatedAt || b.createdAt) - new Date(a.updatedAt || a.createdAt))
              .slice(0, 4)
              .map((course) => (
                <Link className="dash-course" key={course.id} to={`/instructor/offerings?courseId=${course.id}`}>
                  <div className="dash-course-art">
                    <CourseImage src={course.thumbnailUrl} alt={course.title} loading="lazy" />
                  </div>
                  <div className="dash-course-body">
                    <StatusBadge value={course.status} />
                    <strong>{course.title}</strong>
                    <small>{offeringCount[course.id] || 0} lớp · cập nhật {date(course.updatedAt || course.createdAt)}</small>
                  </div>
                </Link>
              ))}
          </div>
        ) : (
          <p className="dash-empty">
            Bạn chưa có khóa học. <Link to="/instructor/courses">Tạo khóa học đầu tiên</Link>
          </p>
        )}
      </DashPanel>
    </Shell>
  );
}
