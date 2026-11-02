import { useCart } from "../../contexts/CartContext";
import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  ArrowLeft,
  CalendarDays,
  Clock3,
  Users,
  ShoppingCart,
  GraduationCap,
  ArrowRight,
  BookOpen,
} from "lucide-react";
import CourseImage from "../../components/common/CourseImage";
import CourseCard from "../../components/common/CourseCard";
import RatingStars from "../../components/common/RatingStars";
import WishlistButton from "../../components/common/WishlistButton";
import { Empty, ErrorState, Loading } from "../../components/common/State";
import { mediaUrl } from "../../api/client";
import useApi from "../../hooks/useApi";
import { useAuth } from "../../contexts/AuthContext";
import { date, money } from "../../utils/format";

export default function CourseDetail() {
  const { slug } = useParams();
  const navigate = useNavigate();
  const { add } = useCart();
  const { token, user } = useAuth();
  const course = useApi(`/courses/${slug}`);
  const offerings = useApi(`/courses/${slug}/offerings`);
  const instructors = useApi(`/courses/${slug}/instructors`);
  const reviews = useApi(`/reviews/courses/${slug}`);
  const metrics = useApi(`/courses/${slug}/metrics`);
  const related = useApi(
    course.data?.categories?.[0]?.slug
      ? `/courses?size=8&sort=rating_desc&category=${encodeURIComponent(course.data.categories[0].slug)}`
      : null,
  );
  const popular = useApi("/courses?size=12&sort=students_desc");
  const [selectedId, setSelectedId] = useState("");
  const [cartMessage, setCartMessage] = useState("");
  useEffect(() => {
    setSelectedId("");
    setCartMessage("");
  }, [slug]);
  if (course.loading || offerings.loading)
    return (
      <main className="page">
        <Loading />
      </main>
    );
  if (course.error || offerings.error)
    return (
      <main className="page">
        <ErrorState
          error={course.error || offerings.error}
          retry={() => {
            course.reload();
            offerings.reload();
          }}
        />
      </main>
    );
  if (!course.data) return null;
  const item = course.data;
  const classes = offerings.data || [];
  const selected =
    classes.find((row) => row.id === Number(selectedId)) || classes[0];
  const suggestions = [
    ...new Map(
      [...(related.data?.items || []), ...(popular.data?.items || [])]
        .filter((row) => row.courseId !== item.courseId)
        .map((row) => [row.courseId, row]),
    ).values(),
  ].slice(0, 4);

  function addToCart() {
    if (!user) {
      navigate("/auth/login");
      return;
    }
    if (!selected) return;
    try {
      setCartMessage(
        add(item, selected)
          ? "Đã thêm lớp vào giỏ hàng."
          : "Lớp này đã có trong giỏ hàng.",
      );
    } catch {
      setCartMessage("Chưa lưu được giỏ hàng. Vui lòng thử lại.");
    }
  }

  return (
    <main className="page">
      <Link className="back" to="/courses">
        <ArrowLeft size={16} />
        Tất cả khóa học
      </Link>
      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_380px]">
        <div className="overflow-hidden rounded-xl border border-slate-200 bg-slate-100 lg:col-start-1 lg:row-start-1">
          <CourseImage
            className="aspect-video w-full object-cover"
            src={item.thumbnailUrl}
            alt={item.title}
          />
        </div>
        <aside
          className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm lg:sticky lg:top-24 lg:col-start-2 lg:row-start-1 lg:row-span-2"
          aria-label="Thông tin và đăng ký khóa học"
        >
          <div className="mb-3 flex items-center justify-between gap-3">
            <span className="eyebrow">Khóa học trực tuyến</span>
            <WishlistButton compact course={item} />
          </div>
          <h1 className="mb-3 text-xl leading-snug">{item.title}</h1>
          <RatingStars
            value={metrics.data?.rating}
            count={metrics.data?.reviewCount}
          />
          <p className="mt-2 flex items-center gap-2 text-xs text-slate-500">
            <Users size={14} />
            {metrics.data?.studentCount || 0} học viên đang học
          </p>
          <div className="my-4 border-t border-slate-100" />
          {selected ? (
            <>
              <strong className="mb-4 block text-2xl text-blue-700">
                {Number(selected.price) === 0
                  ? "Miễn phí"
                  : money(selected.price)}
              </strong>
              <label className="grid gap-2 text-sm font-medium text-slate-700">
                Chọn lớp học
                <select
                  aria-label="Chọn lớp học"
                  className="w-full min-w-0 rounded-lg border border-slate-300 bg-white p-2.5 text-sm"
                  value={selected.id}
                  onChange={(event) => {
                    setSelectedId(event.target.value);
                    setCartMessage("");
                  }}
                >
                  {classes.map((row) => (
                    <option key={row.id} value={row.id}>
                      {row.cohortName || row.code} · {date(row.startDate)}
                    </option>
                  ))}
                </select>
              </label>
              <div className="my-4 grid gap-3 text-sm text-slate-600">
                <p className="!m-0 flex items-start gap-2">
                  <CalendarDays size={16} className="mt-0.5 shrink-0" />
                  <span>
                    {date(selected.startDate)} — {date(selected.endDate)}
                  </span>
                </p>
                <p className="!m-0 flex items-start gap-2">
                  <Clock3 size={16} className="mt-0.5 shrink-0" />
                  <span>{selected.scheduleSummary}</span>
                </p>
                <p className="!m-0 flex items-center gap-2">
                  <BookOpen size={16} />
                  {selected.sessions?.length || 0} buổi · Tối đa{" "}
                  {selected.capacity} học viên
                </p>
              </div>
              <div className="grid gap-2">
                <Link
                  className="btn w-full"
                  to={
                    token
                      ? `/buyer/checkout?offeringId=${selected.id}`
                      : "/auth/login"
                  }
                >
                  {token ? "Đăng ký lớp này" : "Đăng nhập để đăng ký"}
                  <ArrowRight size={17} />
                </Link>
                <button
                  className="btn secondary w-full"
                  type="button"
                  onClick={addToCart}
                >
                  <ShoppingCart size={17} />
                  Thêm vào giỏ hàng
                </button>
              </div>
              {cartMessage && (
                <p className="mt-3 text-xs text-blue-700" role="status">
                  {cartMessage}{" "}
                  <Link className="underline" to="/buyer/cart">
                    Xem giỏ
                  </Link>
                </p>
              )}
              {selected.sessions?.length > 0 && (
                <details className="mt-4 border-t border-slate-100 pt-3">
                  <summary className="cursor-pointer py-1 text-xs font-medium text-slate-600">
                    Xem ngày và giờ từng buổi
                  </summary>
                  <ul className="mt-2 max-h-48 space-y-2 overflow-auto text-xs text-slate-500">
                    {[...selected.sessions]
                      .sort((a, b) => a.startsAt.localeCompare(b.startsAt))
                      .map((session) => (
                        <li
                          className="rounded bg-slate-50 p-2"
                          key={session.number}
                        >
                          <strong className="text-slate-700">
                            Buổi {session.number}: {session.title}
                          </strong>
                          <p>
                            {date(session.startsAt)} → {date(session.endsAt)}
                          </p>
                        </li>
                      ))}
                  </ul>
                </details>
              )}
            </>
          ) : (
            <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-500">
              Hiện chưa có lớp mở bán. Bạn có thể lưu yêu thích để xem lại sau.
            </div>
          )}
        </aside>
        <div className="min-w-0 space-y-6 lg:col-start-1 lg:row-start-2">
          <section className="panel">
            <h2 className="mb-4">Nội dung khóa học</h2>
            <p className="whitespace-pre-line leading-7 text-slate-600">
              {item.description || "Nội dung đang được cập nhật."}
            </p>
            <div className="course-tags">
              {item.categories?.map((category) => (
                <Link
                  key={category.slug}
                  to={`/courses?category=${encodeURIComponent(category.slug)}`}
                >
                  {category.name}
                </Link>
              ))}
              {item.tags?.map((tag) => (
                <Link key={tag} to={`/courses?tag=${encodeURIComponent(tag)}`}>
                  #{tag}
                </Link>
              ))}
            </div>
          </section>
          <section className="panel">
            <h2 className="mb-4">Giảng viên</h2>
            {instructors.loading ? (
              <Loading />
            ) : instructors.error ? (
              <ErrorState
                error={instructors.error}
                retry={instructors.reload}
              />
            ) : instructors.data?.length ? (
              <div className="space-y-5">
                {instructors.data.map((teacher) => (
                  <article className="flex items-start gap-4" key={teacher.id}>
                    {teacher.avatarUrl ? (
                      <img
                        className="size-14 shrink-0 rounded-full object-cover"
                        src={mediaUrl(teacher.avatarUrl)}
                        alt={teacher.fullName}
                        loading="lazy"
                      />
                    ) : (
                      <GraduationCap
                        size={40}
                        className="shrink-0 text-slate-400"
                      />
                    )}
                    <div className="min-w-0">
                      <h3>{teacher.fullName}</h3>
                      <p className="whitespace-pre-line text-sm text-slate-500">
                        {teacher.bio || "Giảng viên phụ trách lớp học."}
                      </p>
                      <div className="mt-3 flex flex-wrap gap-x-4 gap-y-2 text-sm">
                        {teacher.contactEmail && (
                          <a
                            href={`mailto:${teacher.contactEmail}`}
                            className="text-blue-700 underline"
                          >
                            Email: {teacher.contactEmail}
                          </a>
                        )}
                        {teacher.phone && (
                          <a
                            href={`tel:${teacher.phone}`}
                            className="text-blue-700 underline"
                          >
                            Gọi {teacher.phone}
                          </a>
                        )}
                        {teacher.zalo && (
                          <a
                            href={
                              /^https:\/\//i.test(teacher.zalo)
                                ? teacher.zalo
                                : `https://zalo.me/${teacher.zalo.replace(/\D/g, "")}`
                            }
                            target="_blank"
                            rel="noreferrer"
                            className="text-blue-700 underline"
                          >
                            Liên hệ Zalo
                          </a>
                        )}
                        {teacher.facebookUrl &&
                          /^https:\/\//i.test(teacher.facebookUrl) && (
                            <a
                              href={teacher.facebookUrl}
                              target="_blank"
                              rel="noreferrer"
                              className="text-blue-700 underline"
                            >
                              Facebook giảng viên
                            </a>
                          )}
                        {!teacher.contactEmail &&
                          !teacher.phone &&
                          !teacher.zalo &&
                          !teacher.facebookUrl && (
                            <span className="text-xs text-slate-500">
                              Giảng viên chưa công khai thông tin liên hệ.
                            </span>
                          )}
                      </div>
                    </div>
                  </article>
                ))}
              </div>
            ) : (
              <p className="text-slate-500">
                Thông tin giảng viên sẽ cập nhật khi lớp mở bán.
              </p>
            )}
          </section>
          <section className="panel">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
              <h2>Đánh giá từ học viên</h2>
              {token && (
                <Link
                  className="text-sm font-medium text-blue-600 hover:underline"
                  to={`/buyer/reviews/${item.courseId}`}
                >
                  Viết hoặc sửa đánh giá
                </Link>
              )}
            </div>
            {reviews.loading ? (
              <Loading />
            ) : reviews.error ? (
              <ErrorState error={reviews.error} retry={reviews.reload} />
            ) : reviews.data?.length ? (
              <div className="divide-y divide-slate-100">
                {reviews.data.map((review) => (
                  <article key={review.id} className="py-4 first:pt-0">
                    <strong className="mb-1 block text-sm">
                      {review.reviewerName}
                    </strong>
                    <RatingStars value={review.rating} count={1} />
                    <p className="text-sm text-slate-600">{review.comment}</p>
                  </article>
                ))}
              </div>
            ) : (
              <p className="text-slate-500">
                Chưa có đánh giá cho khóa học này.
              </p>
            )}
            <p className="mt-4 text-xs text-slate-400">
              Chỉ học viên đáp ứng điều kiện ghi danh mới được gửi đánh giá.
            </p>
          </section>
        </div>
      </div>
      <section
        className="mt-10 border-t border-slate-200 pt-7"
        aria-label="Khóa học gợi ý"
      >
        <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2>Các khóa học có thể bạn thích</h2>
            <p className="text-sm text-slate-500">
              Khám phá thêm chủ đề liên quan và những khóa học được quan tâm.
            </p>
          </div>
          <Link className="text-sm font-medium text-blue-600" to="/courses">
            Xem tất cả <ArrowRight size={15} className="inline" />
          </Link>
        </div>
        {related.loading && popular.loading ? (
          <Loading />
        ) : suggestions.length ? (
          <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-4">
            {suggestions.map((row) => (
              <CourseCard key={row.courseId} course={row} compactWishlist />
            ))}
          </div>
        ) : related.error && popular.error ? (
          <ErrorState
            error={popular.error}
            retry={() => {
              related.reload();
              popular.reload();
            }}
          />
        ) : (
          <Empty>Chưa có khóa học gợi ý.</Empty>
        )}
      </section>
    </main>
  );
}
