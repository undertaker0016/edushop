import { useEffect, useState } from "react";
import { Bell } from "lucide-react";
import { Link } from "react-router-dom";
import { api } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";

export default function NotificationBell() {
  const { token } = useAuth();
  const [count, setCount] = useState(0);
  const [items, setItems] = useState([]);
  const [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    async function refresh() {
      try {
        const value = await api.get("/notifications/unread-count", token);
        if (active) setCount(value.count);
      } catch {
        /* Opening the center exposes retry feedback. */
      }
    }
    if (token) refresh();
    const interval = setInterval(refresh, 30000);
    window.addEventListener("edushop:notifications-changed", refresh);
    return () => {
      active = false;
      clearInterval(interval);
      window.removeEventListener("edushop:notifications-changed", refresh);
    };
  }, [token]);
  async function open(event) {
    if (!event.currentTarget.open) return;
    setError("");
    try {
      const result = await api.get("/notifications/center?size=5", token);
      setItems(result.items);
    } catch (caught) {
      setError(caught.message);
    }
  }
  return (
    <details className="relative" onToggle={open}>
      <summary
        className="notification-trigger relative flex size-11 cursor-pointer list-none items-center justify-center rounded-full"
        aria-label={`Thông báo, ${count} chưa đọc`}
      >
        <Bell size={19} aria-hidden="true" />
        {count > 0 && (
          <span className="absolute -right-0.5 -top-0.5 rounded-full bg-blue-600 px-1 text-[10px] text-white">
            {count > 99 ? "99+" : count}
          </span>
        )}
      </summary>
      <div className="absolute right-0 z-30 mt-2 max-h-96 w-72 max-w-[85vw] overflow-auto rounded-xl border border-slate-200 bg-white p-4 text-slate-900 shadow-lg">
        <strong>Thông báo gần đây</strong>
        {error && <p role="alert">{error}</p>}
        {items.map((item) => (
          <article className="border-b border-slate-100 py-3" key={item.id}>
            {item.actionUrl?.startsWith("/") &&
            !item.actionUrl.startsWith("//") ? (
              <Link
                className="text-sm font-semibold text-blue-700 hover:underline"
                to={item.actionUrl}
                onClick={(e) =>
                  e.currentTarget.closest("details").removeAttribute("open")
                }
              >
                {item.title} →
              </Link>
            ) : (
              <strong className="text-sm">{item.title}</strong>
            )}
            <p className="line-clamp-2 text-sm text-slate-600">{item.body}</p>
            <span className="text-xs">{item.read ? "Đã đọc" : "Chưa đọc"}</span>
          </article>
        ))}
        {!items.length && !error && <p>Chưa có thông báo mới.</p>}
        <Link
          className="mt-3 inline-block text-blue-700"
          to="/buyer/notifications"
          onClick={(e) =>
            e.currentTarget.closest("details").removeAttribute("open")
          }
        >
          Xem tất cả thông báo
        </Link>
      </div>
    </details>
  );
}
