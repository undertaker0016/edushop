import AddToCartButton from "./AddToCartButton";
import CourseImage from "./CourseImage";
import RatingStars from "./RatingStars";
import { money } from "../../utils/format";
import { mediaUrl } from "../../api/client";
import { Link } from "react-router-dom";
import { BookOpen } from "lucide-react";
import WishlistButton from "./WishlistButton";
export default function CourseCard({ course, compactWishlist = false }) {
  return (
    <article className="course-card">
      <Link className="course-card-link" to={`/courses/${course.slug}`}>
        <div className="course-art">
          {course.thumbnailUrl ? (
            <img
              src={mediaUrl(course.thumbnailUrl)}
              alt={course.title}
              loading="lazy"
            />
          ) : (
            <BookOpen size={40} />
          )}
        </div>
        <h3>{course.title}</h3>
        <RatingStars value={course.rating} count={course.reviewCount} />
        <p>{course.description || "Khám phá nội dung khóa học."}</p>
      </Link>
      <div className="mt-3 flex items-center justify-between gap-2">
        <strong className="text-blue-700">
          {course.minPrice == null
            ? "Chưa mở lớp"
            : Number(course.minPrice) === 0
              ? "Miễn phí"
              : `Từ ${money(course.minPrice)}`}
        </strong>
        <span className="text-xs text-slate-500">
          {Number(course.studentCount || 0)} học viên
        </span>
      </div>
      <div className="mt-3 flex items-start gap-2">
        <AddToCartButton course={course} />
        <WishlistButton compact course={course} />
      </div>
    </article>
  );
}
