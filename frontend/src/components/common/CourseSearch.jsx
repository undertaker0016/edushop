import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Search, LoaderCircle } from "lucide-react";
import { api, mediaUrl } from "../../api/client";
export default function CourseSearch() {
  const [q, setQ] = useState(""),
    [items, setItems] = useState([]),
    [show, setShow] = useState(false),
    [busy, setBusy] = useState(false),
    [active, setActive] = useState(-1);
  const navigate = useNavigate();
  const root = useRef(null);
  useEffect(() => {
    let alive = true;
    if (q.trim().length < 2) {
      setItems([]);
      setBusy(false);
      return;
    }
    const timer = setTimeout(async () => {
      setBusy(true);
      try {
        const rows = await api.get(
          `/courses/suggestions?q=${encodeURIComponent(q.trim())}`,
        );
        if (alive) setItems(rows);
      } catch {
        if (alive) setItems([]);
      } finally {
        if (alive) setBusy(false);
      }
    }, 250);
    return () => {
      alive = false;
      clearTimeout(timer);
    };
  }, [q]);
  function submit(event) {
    event.preventDefault();
    setShow(false);
    if (active >= 0 && items[active])
      navigate(`/courses/${items[active].slug}`);
    else navigate(`/courses?q=${encodeURIComponent(q.trim())}`);
  }
  return (
    <form
      ref={root}
      className="nav-search relative order-3 basis-full sm:order-none sm:basis-auto"
      role="search"
      onSubmit={submit}
      onBlur={(event) => {
        if (!root.current.contains(event.relatedTarget)) setShow(false);
      }}
    >
      <input
        aria-label="Tìm khóa học"
        aria-expanded={show && items.length > 0}
        aria-controls="course-suggestions"
        autoComplete="off"
        placeholder="Tìm khóa học, chủ đề…"
        value={q}
        onFocus={() => setShow(true)}
        onChange={(e) => {
          setQ(e.target.value);
          setShow(true);
          setActive(-1);
        }}
        onKeyDown={(e) => {
          if (e.key === "Escape") setShow(false);
          if (["ArrowDown", "ArrowUp"].includes(e.key)) {
            e.preventDefault();
            setActive((previous) =>
              Math.max(
                -1,
                Math.min(
                  items.length - 1,
                  previous + (e.key === "ArrowDown" ? 1 : -1),
                ),
              ),
            );
          }
        }}
      />
      <button className="btn secondary" type="submit" aria-label="Tìm kiếm">
        {busy ? (
          <LoaderCircle size={18} className="animate-spin" />
        ) : (
          <Search size={18} />
        )}
      </button>
      {show && q.trim().length >= 2 && (
        <div
          id="course-suggestions"
          className="absolute left-0 top-full z-50 mt-2 w-full overflow-hidden rounded-xl border border-slate-200 bg-white shadow-lg"
        >
          {items.map((item, index) => (
            <Link
              className={`flex items-center gap-3 border-b border-slate-100 p-3 text-sm hover:bg-blue-50 ${active === index ? "bg-blue-50" : ""}`}
              key={item.courseId}
              to={`/courses/${item.slug}`}
              onClick={() => setShow(false)}
            >
              {item.thumbnailUrl && (
                <img
                  className="h-10 w-14 rounded object-cover"
                  src={mediaUrl(item.thumbnailUrl)}
                  alt=""
                />
              )}
              <span>{item.title}</span>
            </Link>
          ))}
          {!items.length && !busy && (
            <p className="p-3 text-xs text-slate-500">
              Chưa có gợi ý. Nhấn Enter để tìm kiếm.
            </p>
          )}
        </div>
      )}
    </form>
  );
}
