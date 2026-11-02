import RatingStars from "../../components/common/RatingStars";
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import Shell from "../../components/common/Shell";
import useApi from "../../hooks/useApi";
import { Loading, ErrorState } from "../../components/common/State";

export default function Review() {
  const { courseId } = useParams();
  const { token } = useAuth();
  const existing = useApi(`/reviews/mine/courses/${courseId}`, true);
  const [form, setForm] = useState(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!existing.loading && existing.data)
      setForm(
        existing.data.exists
          ? {
              rating: existing.data.rating,
              comment: existing.data.comment || "",
            }
          : { rating: 5, comment: "" },
      );
  }, [existing.loading, existing.data]);
  const navigate = useNavigate();
  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      await api.put(`/reviews/courses/${courseId}`, form, token);
      navigate(`/courses/${existing.data?.courseSlug}`);
    } catch (error) {
      setError(error.message);
    } finally {
      setBusy(false);
    }
  };
  if (existing.loading) return <Loading />;
  if (existing.error)
    return <ErrorState error={existing.error} retry={existing.reload} />;
  const editing = !!existing.data?.exists;
  if (!form) return <Loading />;
  return (
    <Shell
      title={editing ? "Sửa đánh giá" : "Viết đánh giá"}
      subtitle={
        editing
          ? "Bạn đang cập nhật đánh giá đã gửi cho khóa học này."
          : "Bạn đang tạo đánh giá mới. Chỉ học viên đã thanh toán và ghi danh mới được đánh giá."
      }
    >
      <form className="panel form" onSubmit={submit}>
        {error && <div className="alert">{error}</div>}
        <div>
          <span className="text-sm font-medium">Đánh giá của bạn</span>
          <RatingStars
            value={form.rating}
            onChange={(rating) => setForm({ ...form, rating })}
          />
        </div>
        <label>
          Nhận xét
          <textarea
            required
            value={form.comment}
            onChange={(event) =>
              setForm({ ...form, comment: event.target.value })
            }
          />
        </label>
        {editing && (
          <button
            type="button"
            className="btn danger"
            disabled={busy}
            onClick={async () => {
              setBusy(true);
              try {
                await api.del(`/reviews/courses/${courseId}`, token);
                await existing.reload();
                setForm({ rating: 5, comment: "" });
              } catch (e) {
                setError(e.message);
              } finally {
                setBusy(false);
              }
            }}
          >
            Xóa đánh giá
          </button>
        )}
        <button className="btn" disabled={busy}>
          {busy
            ? editing
              ? "Đang cập nhật…"
              : "Đang gửi…"
            : editing
              ? "Cập nhật đánh giá"
              : "Gửi đánh giá"}
        </button>
      </form>
    </Shell>
  );
}
