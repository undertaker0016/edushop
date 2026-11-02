import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import {
  X,
  Eye,
  EyeOff,
  LoaderCircle,
  AlertCircle,
  Inbox,
  ChevronLeft,
  ChevronRight,
} from "lucide-react";
import { label } from "../../utils/labels";
export function Button({
  variant = "primary",
  busy = false,
  children,
  className = "",
  disabled = false,
  ...props
}) {
  return (
    <button
      className={`btn ${variant === "primary" ? "" : variant} ${className}`}
      disabled={busy || disabled}
      aria-busy={busy || undefined}
      {...props}
    >
      {busy && <LoaderCircle size={16} className="animate-spin" aria-hidden />}
      {children}
    </button>
  );
}
export function IconButton({ label: text, children, ...props }) {
  return (
    <Button aria-label={text} title={text} variant="secondary" {...props}>
      {children}
    </Button>
  );
}
export function Card({ children, className = "" }) {
  return <section className={`panel ${className}`}>{children}</section>;
}
export function FormField({ label: text, error, children }) {
  return (
    <label className="grid gap-1.5 text-sm font-medium text-slate-700">
      <span>{text}</span>
      {children}
      {error && <span className="text-xs text-red-600">{error}</span>}
    </label>
  );
}
export function Input(props) {
  return (
    <input
      {...props}
      className={`w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 ${props.className || ""}`}
    />
  );
}
export function Textarea(props) {
  return (
    <textarea
      {...props}
      className="min-h-24 rounded-lg border border-slate-300 p-3"
    />
  );
}
export function Select(props) {
  return (
    <select
      {...props}
      className="rounded-lg border border-slate-300 bg-white px-3 py-2.5"
    />
  );
}
export function Checkbox(props) {
  return <input type="checkbox" {...props} />;
}
export function PasswordInput(props) {
  const [visible, setVisible] = useState(false);
  return (
    <div className="relative">
      <Input
        {...props}
        type={visible ? "text" : "password"}
        className="pr-12"
      />
      <button
        className="absolute right-1 top-1 rounded p-2 text-slate-500"
        type="button"
        aria-label={visible ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
        onClick={() => setVisible(!visible)}
      >
        {visible ? <EyeOff size={18} /> : <Eye size={18} />}
      </button>
    </div>
  );
}
export function Badge({ children, tone = "neutral" }) {
  const tones = {
    neutral: "bg-slate-100 text-slate-700",
    success: "bg-green-50 text-green-700",
    warning: "bg-amber-50 text-amber-800",
    error: "bg-red-50 text-red-700",
    info: "bg-blue-50 text-blue-700",
  };
  return (
    <span
      className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ${tones[tone]}`}
    >
      {children}
    </span>
  );
}
export function StatusBadge({ value }) {
  const tone = [
    "ACTIVE",
    "PAID",
    "PUBLISHED",
    "COMPLETED",
    "OPEN",
    "SUCCEEDED",
  ].includes(value)
    ? "success"
    : ["PENDING", "DRAFT", "RESCHEDULED"].includes(value)
      ? "warning"
      : ["FAILED", "BANNED", "CANCELLED"].includes(value)
        ? "error"
        : "neutral";
  return <Badge tone={tone}>{label(value)}</Badge>;
}
// Mặc định modal KHÔNG tự đóng khi bấm ra ngoài hoặc nhấn Esc, để tránh mất
// dữ liệu đang nhập trong form. Chỉ hộp thoại đơn giản (xác nhận) mới bật
// `dismissible`.
export function Modal({ open, onClose, title, children, dismissible = false }) {
  const ref = useRef(null);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;
  const dismissibleRef = useRef(dismissible);
  dismissibleRef.current = dismissible;
  useEffect(() => {
    if (!open) return;
    const previous = document.activeElement;
    const dialog = ref.current;
    dialog.showModal();
    const close = (event) => {
      event.preventDefault();
      if (dismissibleRef.current) onCloseRef.current();
    };
    dialog.addEventListener("cancel", close);
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      dialog.removeEventListener("cancel", close);
      dialog.close();
      document.body.style.overflow = overflow;
      previous?.focus();
    };
  }, [open]);
  return createPortal(
    <dialog
      ref={ref}
      className="m-auto max-h-[90dvh] w-[calc(100%-32px)] max-w-2xl overflow-auto rounded-xl border border-slate-200 bg-white p-0 shadow-xl backdrop:bg-slate-900/50"
      onClick={(event) => {
        if (event.target === ref.current && dismissibleRef.current)
          onCloseRef.current();
      }}
      aria-label={title}
    >
      <header className="sticky top-0 z-10 flex items-center justify-between border-b border-slate-200 bg-white px-5 py-3">
        <h2>{title}</h2>
        <IconButton label="Đóng" onClick={onClose}>
          <X size={18} />
        </IconButton>
      </header>
      {open && <div className="p-5">{children}</div>}
    </dialog>,
    document.body,
  );
}
export function ConfirmDialog({
  open,
  onClose,
  onConfirm,
  title = "Xác nhận thao tác",
  children,
  busy,
}) {
  return (
    <Modal open={open} onClose={onClose} title={title} dismissible>
      <p>{children}</p>
      <div className="mt-5 flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>
          Quay lại
        </Button>
        <Button variant="danger" busy={busy} onClick={onConfirm}>
          Xác nhận
        </Button>
      </div>
    </Modal>
  );
}
export function Pagination({ page, totalPages, onChange }) {
  return (
    <nav aria-label="Phân trang" className="pagination">
      <Button
        variant="secondary"
        disabled={page <= 0}
        onClick={() => onChange(page - 1)}
      >
        <ChevronLeft size={16} />
        Trước
      </Button>
      <span>
        Trang {page + 1} / {Math.max(1, totalPages)}
      </span>
      <Button
        variant="secondary"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        Sau
        <ChevronRight size={16} />
      </Button>
    </nav>
  );
}
export function LoadingSkeleton() {
  return (
    <div className="loading-state" role="status" aria-label="Đang tải dữ liệu">
      <img
        src="/images/ui/page-loading.gif"
        width="120"
        height="120"
        alt=""
        className="loading-gif"
      />
      <span className="loading-still" aria-hidden="true">
        Đang tải…
      </span>
      <p>Đang tải dữ liệu, bạn chờ một chút nhé.</p>
    </div>
  );
}
export function EmptyState({ children = "Chưa có dữ liệu." }) {
  return (
    <div className="state">
      <Inbox className="mx-auto mb-3" size={28} aria-hidden />
      {children}
    </div>
  );
}
export function ErrorState({ error, retry }) {
  return (
    <div className="alert" role="alert">
      <AlertCircle size={18} />
      <span className="flex-1">{error?.message || String(error)}</span>
      {retry && (
        <Button variant="secondary" onClick={retry}>
          Thử lại
        </Button>
      )}
    </div>
  );
}
export function Breadcrumb({ children }) {
  return (
    <nav aria-label="Đường dẫn" className="text-xs text-slate-500">
      {children}
    </nav>
  );
}
export function Tabs({ items, value, onChange }) {
  return (
    <div className="mb-4 flex gap-2 border-b border-slate-200" role="tablist">
      {items.map((item) => (
        <button
          key={item.value}
          role="tab"
          tabIndex={value === item.value ? 0 : -1}
          onKeyDown={(event) => {
            const keys = ["ArrowRight", "ArrowLeft", "Home", "End"];
            if (!keys.includes(event.key)) return;
            event.preventDefault();
            const index = items.findIndex(
              (entry) => entry.value === item.value,
            );
            const next =
              event.key === "Home"
                ? 0
                : event.key === "End"
                  ? items.length - 1
                  : (index +
                      (event.key === "ArrowRight" ? 1 : -1) +
                      items.length) %
                    items.length;
            event.currentTarget.parentElement
              .querySelectorAll('[role="tab"]')
              [next]?.focus();
            onChange(items[next].value);
          }}
          aria-selected={value === item.value}
          className={`px-4 py-3 ${value === item.value ? "border-b-2 border-blue-600 text-blue-600" : "text-slate-500"}`}
          onClick={() => onChange(item.value)}
        >
          {item.label}
        </button>
      ))}
    </div>
  );
}
