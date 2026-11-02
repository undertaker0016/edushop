import { useState } from "react";
import { Link } from "react-router-dom";
import { Trash2, CalendarDays, Clock3 } from "lucide-react";
import { useCart } from "../../contexts/CartContext";
import CourseImage from "../../components/common/CourseImage";
import { Loading, Empty } from "../../components/common/State";
import { money, date } from "../../utils/format";
export default function Cart() {
  const { rows, count, loading, remove } = useCart();
  const [selected, setSelected] = useState(null),
    [error, setError] = useState("");
  const ids = (
    selected ?? rows.filter((row) => row.available).map((row) => row.id)
  ).filter((id) => rows.some((row) => row.id === id && row.available));
  const selectedRows = rows.filter((row) => ids.includes(row.id));
  const sum = selectedRows.reduce((total, row) => total + Number(row.price), 0);
  if ((loading || count > 0) && !rows.length) return <Loading />;
  if (!rows.length)
    return (
      <Empty>
        Giỏ hàng đang trống.{" "}
        <Link className="text-blue-600 underline" to="/courses">
          Khám phá khóa học
        </Link>
      </Empty>
    );
  return (
    <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_300px]">
      <div className="grid gap-4">
        {error && <div className="alert">{error}</div>}
        {rows.map((row) => (
          <article className="panel" key={row.id}>
            <div className="flex items-start gap-3">
              <input
                aria-label={`Chọn lớp ${row.course?.title || row.title}`}
                type="checkbox"
                disabled={!row.available}
                checked={ids.includes(row.id)}
                onChange={(e) =>
                  setSelected(
                    e.target.checked
                      ? [...ids, row.id]
                      : ids.filter((id) => id !== row.id),
                  )
                }
              />
              <CourseImage
                className="h-24 w-32 shrink-0 rounded-lg object-cover max-sm:hidden"
                src={row.course?.thumbnailUrl || row.thumbnailUrl}
                alt={row.course?.title || row.title}
              />
              <div className="min-w-0 flex-1">
                <Link
                  className="font-semibold text-slate-900 hover:text-blue-600"
                  to={`/courses/${row.course?.slug || row.slug}`}
                >
                  {row.course?.title || row.title}
                </Link>
                <p className="text-sm text-slate-500">
                  {row.cohortName || row.code}
                </p>
                {row.available ? (
                  <>
                    <p className="flex items-center gap-2 text-xs text-slate-500">
                      <CalendarDays size={14} />
                      {date(row.startDate)} — {date(row.endDate)}
                    </p>
                    <p className="flex items-center gap-2 text-xs text-slate-500">
                      <Clock3 size={14} />
                      {row.scheduleSummary}
                    </p>
                    <strong className="mt-2 block text-blue-700">
                      {money(row.price)}
                    </strong>
                  </>
                ) : (
                  <p className="text-sm text-amber-700">
                    Lớp đã đóng hoặc không còn khả dụng. Hãy chọn lớp khác.
                  </p>
                )}
              </div>
              <button
                aria-label={`Bỏ lớp ${row.title}`}
                title="Bỏ khỏi giỏ"
                className="rounded-lg p-2 text-slate-400 hover:bg-red-50 hover:text-red-600"
                onClick={() => {
                  try {
                    remove(row.id);
                    setSelected(ids.filter((id) => id !== row.id));
                  } catch {
                    setError("Chưa lưu được thay đổi giỏ hàng.");
                  }
                }}
              >
                <Trash2 size={17} />
              </button>
            </div>
            {row.available && (
              <details className="mt-3 border-t border-slate-100 pt-2 text-xs text-slate-500">
                <summary className="cursor-pointer py-1">
                  Xem {row.sessions?.length || 0} buổi và nội dung khóa học
                </summary>
                <p className="line-clamp-3">{row.course?.description}</p>
                <ul className="mt-2 grid gap-2">
                  {row.sessions?.map((session) => (
                    <li key={session.number}>
                      {date(session.startsAt)} → {date(session.endsAt)} ·{" "}
                      {session.title}
                    </li>
                  ))}
                </ul>
              </details>
            )}
          </article>
        ))}
      </div>
      <aside className="panel lg:sticky lg:top-24">
        <h2>Tóm tắt giỏ hàng</h2>
        <p className="text-sm text-slate-500">
          Đã chọn {ids.length}/{rows.length} lớp
        </p>
        <strong className="my-4 block text-2xl">{money(sum)}</strong>
        <p className="mb-4 text-xs text-slate-500">
          Tạm tính từ giá lớp hiện tại. Voucher và tổng tiền cuối cùng sẽ được
          xác nhận ở bước thanh toán.
        </p>
        {ids.length > 0 && ids.length <= 20 ? (
          <Link
            className="btn w-full"
            to={`/buyer/checkout?offeringIds=${ids.join(",")}`}
          >
            Đăng ký {ids.length} lớp đã chọn
          </Link>
        ) : (
          <p className="text-sm text-amber-700">
            Chọn từ 1 đến 20 lớp để tiếp tục.
          </p>
        )}
        <Link
          className="mt-3 block text-center text-sm text-blue-600"
          to="/buyer/orders"
        >
          Xem các đơn đã tạo
        </Link>
      </aside>
    </div>
  );
}
