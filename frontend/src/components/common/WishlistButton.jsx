import { Heart } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../contexts/AuthContext";
import { useWishlist } from "../../contexts/WishlistContext";
export default function WishlistButton({ course, compact = false }) {
  const { user } = useAuth();
  const { rows, loading, pending, toggle, error } = useWishlist();
  const [message, setMessage] = useState("");
  const navigate = useNavigate();
  const saved = rows.some((row) => row.courseId === course.courseId);
  return (
    <div
      className={
        compact ? "inline-flex flex-col items-center" : "wishlist-control"
      }
    >
      <button
        type="button"
        className={
          compact
            ? `inline-flex size-10 shrink-0 items-center justify-center rounded-full border transition ${saved ? "border-rose-200 bg-rose-50 text-rose-600" : "border-slate-200 bg-white text-slate-500 hover:border-rose-200 hover:text-rose-600"}`
            : `btn ${saved ? "" : "secondary"}`
        }
        aria-label={
          compact
            ? saved
              ? "Bỏ yêu thích khóa học"
              : "Thêm khóa học vào yêu thích"
            : undefined
        }
        title={
          compact ? (saved ? "Bỏ yêu thích" : "Thêm vào yêu thích") : undefined
        }
        aria-pressed={saved}
        disabled={
          loading || pending.includes(course.courseId) || Boolean(error)
        }
        onClick={async () => {
          if (!user) {
            navigate("/auth/login");
            return;
          }
          try {
            await toggle(course);
            setMessage(saved ? "Đã bỏ yêu thích." : "Đã lưu vào yêu thích.");
          } catch (e) {
            setMessage(e.message);
          }
        }}
      >
        <Heart
          size={compact ? 18 : 16}
          fill={saved ? "currentColor" : "none"}
        />
        {!compact && (saved ? "Đã yêu thích" : "Yêu thích")}
      </button>
      {(message || error) && (
        <small
          role="status"
          className={compact && !error ? "sr-only" : "text-xs"}
        >
          {message || error}
        </small>
      )}
    </div>
  );
}
