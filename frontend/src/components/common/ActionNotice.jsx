import { CheckCircle2, X } from "lucide-react";
export default function ActionNotice({ children, onDismiss }) {
  if (!children) return null;
  return (
    <div className="success flex items-center gap-3" role="status">
      <CheckCircle2 size={18} aria-hidden />
      <span className="flex-1">{children}</span>
      <button
        type="button"
        aria-label="Đóng thông báo"
        onClick={onDismiss}
        className="rounded p-2"
      >
        <X size={16} />
      </button>
    </div>
  );
}
