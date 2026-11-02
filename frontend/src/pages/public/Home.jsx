import CourseCard from "../../components/common/CourseCard";
import WishlistButton from "../../components/common/WishlistButton";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../../api/client";
import { Empty, ErrorState, Loading } from "../../components/common/State";

function usePublic(path) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const reload = async () => {
    setLoading(true);
    try {
      setData(await api.get(path));
      setError(null);
    } catch (caught) {
      setError(caught);
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    reload();
  }, [path]);
  return { data, error, loading, reload };
}

function CourseGrid({ rows }) {
  return rows.length ? (
    <div className="course-grid">
      {rows.map((course) => (
        <CourseCard key={course.courseId} course={course} />
      ))}
    </div>
  ) : (
    <Empty>Chưa có khóa học phù hợp.</Empty>
  );
}

export default function Home() {
  const result = usePublic("/courses?size=8");
  if (result.loading) return <Loading />;
  if (result.error)
    return <ErrorState error={result.error} retry={result.reload} />;
  return (
    <main className="page">
      <section className="hero">
        <div>
          <span className="eyebrow">Học tập linh hoạt</span>
          <h1>Học đúng điều bạn cần, theo cách của bạn.</h1>
          <p>
            Khám phá khóa học chất lượng, lịch học rõ ràng và lớp trực tuyến
            riêng tư trên EduShop.
          </p>
          <Link className="btn" to="/courses">
            Khám phá khóa học
          </Link>
        </div>
      </section>
      <section className="mt-8">
        <div className="page-heading">
          <div>
            <h2>Khóa học nổi bật</h2>
            <p>Những lựa chọn mới nhất từ giảng viên EduShop.</p>
          </div>
          <Link className="btn secondary" to="/courses">
            Xem tất cả
          </Link>
        </div>
        <CourseGrid rows={result.data?.items || []} />
      </section>
      <section className="mt-10 grid gap-4 md:grid-cols-3">
        {[
          ["01", "Khám phá khóa học", "Tìm chủ đề và chọn khóa học phù hợp."],
          [
            "02",
            "Chọn lịch lớp",
            "Xem thời gian khai giảng và lịch từng buổi.",
          ],
          [
            "03",
            "Bắt đầu học",
            "Đăng ký, thanh toán và mở lớp học trong tài khoản.",
          ],
        ].map(([number, title, text]) => (
          <article className="panel" key={number}>
            <span className="eyebrow">{number}</span>
            <h3 className="mt-2">{title}</h3>
            <p className="text-slate-500">{text}</p>
          </article>
        ))}
      </section>
    </main>
  );
}
