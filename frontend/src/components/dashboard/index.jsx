import { Link } from "react-router-dom";
import { ArrowRight, ArrowUpRight, CheckCircle2 } from "lucide-react";
import { label } from "../../utils/labels";

/* Shared building blocks for the buyer, instructor and admin dashboards.
   Charts are plain CSS so no extra UI/chart library is needed. */

const STATUS_TONES = {
  success: ["ACTIVE", "PAID", "PUBLISHED", "COMPLETED", "OPEN", "SUCCEEDED", "APPROVED"],
  warning: ["PENDING", "DRAFT", "RESCHEDULED", "REVIEWED", "SCHEDULED"],
  danger: ["FAILED", "BANNED", "CANCELLED", "EXPIRED", "REFUNDED"],
  info: ["CLOSED", "ARCHIVED"],
};
export function toneOf(status) {
  return (
    Object.keys(STATUS_TONES).find((tone) =>
      STATUS_TONES[tone].includes(status),
    ) || "neutral"
  );
}

export const sum = (rows = [], key = "count") =>
  rows.reduce((total, row) => total + Number(row?.[key] || 0), 0);
export const countOf = (rows = [], status) =>
  Number(rows.find((row) => row.status === status)?.count || 0);

export function greeting(now = new Date()) {
  const hour = now.getHours();
  if (hour < 11) return "Chào buổi sáng";
  if (hour < 14) return "Chào buổi trưa";
  if (hour < 18) return "Chào buổi chiều";
  return "Chào buổi tối";
}
export function todayLabel(now = new Date()) {
  const text = now.toLocaleDateString("vi-VN", {
    weekday: "long",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  });
  return text.charAt(0).toUpperCase() + text.slice(1);
}
export const compact = (value) =>
  new Intl.NumberFormat("vi-VN", { notation: "compact" }).format(value || 0);

/** Days between today (local) and an ISO date/datetime. Negative = past. */
export function daysUntil(value, now = new Date()) {
  if (!value) return null;
  const target = new Date(
    /^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value}T00:00:00` : value,
  );
  if (Number.isNaN(target.getTime())) return null;
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const end = new Date(
    target.getFullYear(),
    target.getMonth(),
    target.getDate(),
  );
  return Math.round((end - start) / 86400000);
}

export function DashboardHero({ eyebrow, title, description, actions, aside }) {
  return (
    <section className="dash-hero">
      <div className="dash-hero-copy">
        {eyebrow && <span className="eyebrow">{eyebrow}</span>}
        <h2>{title}</h2>
        {description && <p>{description}</p>}
        {actions && <div className="dash-hero-actions">{actions}</div>}
      </div>
      {aside && <div className="dash-hero-aside">{aside}</div>}
    </section>
  );
}

export function StatCard({ icon: Icon, label: text, value, hint, to, tone = "info" }) {
  const body = (
    <>
      <div className="dash-stat-top">
        <span className="dash-stat-label">{text}</span>
        {Icon && (
          <span className="dash-icon" data-tone={tone} aria-hidden="true">
            <Icon size={18} />
          </span>
        )}
      </div>
      <strong className="dash-stat-value">{value ?? 0}</strong>
      {hint && <span className="dash-stat-hint">{hint}</span>}
      {to && <ArrowUpRight className="dash-stat-arrow" size={16} aria-hidden="true" />}
    </>
  );
  return to ? (
    <Link className="dash-stat" to={to}>
      {body}
    </Link>
  ) : (
    <div className="dash-stat">{body}</div>
  );
}

export function DashPanel({ title, description, action, children, className = "" }) {
  return (
    <section className={`dash-panel ${className}`}>
      <header className="dash-panel-head">
        <div>
          <h2>{title}</h2>
          {description && <p>{description}</p>}
        </div>
        {action && (
          <Link className="dash-panel-link" to={action.to}>
            {action.label}
            <ArrowRight size={14} aria-hidden="true" />
          </Link>
        )}
      </header>
      {children}
    </section>
  );
}

/** Stacked bar + legend. Status is always written as text, not colour only. */
export function StatusBreakdown({ rows = [], emptyText = "Chưa có dữ liệu." }) {
  const total = sum(rows);
  if (!total) return <p className="dash-empty">{emptyText}</p>;
  const sorted = [...rows].sort((a, b) => Number(b.count) - Number(a.count));
  return (
    <div className="dash-breakdown">
      <div className="dash-stack" role="img" aria-label={sorted.map((row) => `${label(row.status)}: ${row.count}`).join(", ")}>
        {sorted.map((row) => (
          <span
            key={row.status}
            data-tone={toneOf(row.status)}
            style={{ width: `${(Number(row.count) / total) * 100}%` }}
          />
        ))}
      </div>
      <ul className="dash-legend">
        {sorted.map((row) => (
          <li key={row.status}>
            <span className="dash-dot" data-tone={toneOf(row.status)} aria-hidden="true" />
            <span className="dash-legend-label">{label(row.status)}</span>
            <strong>{row.count}</strong>
            <small>{Math.round((Number(row.count) / total) * 100)}%</small>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** Ranked horizontal bars, e.g. popular classes or payment providers. */
export function BarList({ rows = [], emptyText = "Chưa có dữ liệu." }) {
  if (!rows.length) return <p className="dash-empty">{emptyText}</p>;
  const max = Math.max(...rows.map((row) => Number(row.value) || 0), 1);
  return (
    <ol className="dash-barlist">
      {rows.map((row, index) => (
        <li key={row.key ?? index}>
          <div className="dash-barlist-text">
            <span className="dash-rank">{index + 1}</span>
            <span className="dash-barlist-label" title={row.label}>
              {row.to ? <Link to={row.to}>{row.label}</Link> : row.label}
            </span>
            <strong>{row.display ?? row.value}</strong>
          </div>
          <div className="dash-track" aria-hidden="true">
            <span style={{ width: `${(Number(row.value) / max) * 100}%` }} />
          </div>
        </li>
      ))}
    </ol>
  );
}

/** Column chart for a short time series. */
export function TrendChart({ points = [], format = (value) => value, emptyText = "Chưa có dữ liệu." }) {
  const max = Math.max(...points.map((point) => point.value), 0);
  if (!max) return <p className="dash-empty">{emptyText}</p>;
  return (
    <div className="dash-trend" role="img" aria-label={points.map((point) => `${point.label}: ${format(point.value)}`).join(", ")}>
      {points.map((point, index) => (
        <div className="dash-trend-col" key={point.label + index} title={`${point.label}: ${format(point.value)}`}>
          <div className="dash-trend-bar">
            <span
              className={point.value ? "" : "is-zero"}
              style={{ height: `${Math.max((point.value / max) * 100, point.value ? 6 : 2)}%` }}
            />
          </div>
          <small>{point.short ?? point.label}</small>
        </div>
      ))}
    </div>
  );
}

export function ProgressBar({ value = 0, label: text }) {
  const safe = Math.min(100, Math.max(0, Math.round(value)));
  return (
    <div className="dash-progress" role="progressbar" aria-valuenow={safe} aria-valuemin={0} aria-valuemax={100} aria-label={text}>
      <span style={{ width: `${safe}%` }} />
    </div>
  );
}

/** "Needs attention" list; shows a calm success message when empty. */
export function AttentionList({ items = [], doneText = "Không có việc nào cần xử lý." }) {
  const visible = items.filter((item) => Number(item.count) > 0);
  if (!visible.length)
    return (
      <div className="dash-all-clear">
        <CheckCircle2 size={20} aria-hidden="true" />
        <span>{doneText}</span>
      </div>
    );
  return (
    <ul className="dash-attention">
      {visible.map(({ key, icon: Icon, title, description, to, tone = "warning", count }) => (
        <li key={key || to}>
          <Link to={to}>
            <span className="dash-icon" data-tone={tone} aria-hidden="true">
              {Icon && <Icon size={18} />}
            </span>
            <span className="dash-attention-copy">
              <strong>{title}</strong>
              {description && <small>{description}</small>}
            </span>
            <span className="dash-count" data-tone={tone}>{count}</span>
            <ArrowRight size={16} aria-hidden="true" />
          </Link>
        </li>
      ))}
    </ul>
  );
}

export function DateChip({ value }) {
  const parsed = value
    ? new Date(/^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value}T00:00:00` : value)
    : null;
  if (!parsed || Number.isNaN(parsed.getTime()))
    return <span className="dash-date">—</span>;
  return (
    <span className="dash-date" aria-hidden="true">
      <small>Th{parsed.getMonth() + 1}</small>
      <strong>{String(parsed.getDate()).padStart(2, "0")}</strong>
    </span>
  );
}

export function SkeletonBlock({ lines = 3 }) {
  return (
    <div className="dash-skeleton" role="status" aria-label="Đang tải dữ liệu">
      {Array.from({ length: lines }, (_, index) => (
        <span key={index} />
      ))}
    </div>
  );
}

export function DashboardSkeleton() {
  return (
    <main className="page" aria-busy="true">
      <div className="dash-skeleton dash-skeleton-hero" role="status" aria-label="Đang tải bảng điều khiển">
        <span />
      </div>
      <div className="dash-stats">
        {Array.from({ length: 4 }, (_, index) => (
          <div className="dash-stat" key={index}>
            <SkeletonBlock lines={2} />
          </div>
        ))}
      </div>
      <div className="dash-grid">
        <div className="dash-panel span-8"><SkeletonBlock lines={5} /></div>
        <div className="dash-panel span-4"><SkeletonBlock lines={5} /></div>
      </div>
    </main>
  );
}
