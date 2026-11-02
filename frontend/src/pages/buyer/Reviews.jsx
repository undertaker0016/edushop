import { Link } from "react-router-dom";
import useApi from "../../hooks/useApi";
import Shell from "../../components/common/Shell";
import { Loading, ErrorState, Empty } from "../../components/common/State";
export default function Reviews() {
  const data = useApi("/courses?size=100");
  if (data.loading) return <Loading />;
  if (data.error) return <ErrorState error={data.error} retry={data.reload} />;
  return (
    <Shell
      title="Đánh giá khóa học"
      subtitle="Chọn khóa học bạn đã ghi danh để viết hoặc cập nhật đánh giá."
    >
      {data.data?.items?.length ? (
        <div className="card-grid">
          {data.data.items.map((course) => (
            <article key={course.courseId} className="card">
              <Link
                className="font-semibold text-slate-900 hover:text-blue-700 hover:underline"
                to={`/courses/${course.slug}`}
              >
                <h2>{course.title}</h2>
              </Link>
              <Link
                className="btn secondary mt-4"
                to={`/buyer/reviews/${course.courseId}`}
              >
                Viết hoặc sửa đánh giá →
              </Link>
            </article>
          ))}
        </div>
      ) : (
        <Empty />
      )}
    </Shell>
  );
}
