import { useEffect, useMemo, useState } from "react";
import { CalendarDays, Clock, Check, X } from "lucide-react";
import { api } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import { Button } from "../ui";
import { date } from "../../utils/format";
export default function ScheduleBuilder({
  offering,
  onSaved,
  onRowsChange,
  initialRows = [],
}) {
  const { token } = useAuth();
  const [days, setDays] = useState([2, 4]),
    [start, setStart] = useState("19:00"),
    [end, setEnd] = useState("20:30"),
    [rows, setRowsState] = useState(initialRows),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [manual, setManual] = useState("");
  function setRows(value) {
    setRowsState((previous) => {
      const next = (typeof value === "function" ? value(previous) : value)
        .slice()
        .sort(
          (a, b) =>
            a.date.localeCompare(b.date) || a.start.localeCompare(b.start),
        )
        .map((row, index) => ({
          ...row,
          title: /^Buổi\s+\d+$/.test(row.title || "")
            ? `Buổi ${index + 1}`
            : row.title,
        }));
      onRowsChange?.(next);
      return next;
    });
  }
  useEffect(() => {
    setRowsState(initialRows);
  }, [initialRows]);
  const dates = useMemo(() => {
    const result = [];
    if (!offering.startDate || !offering.endDate) return result;
    const current = new Date(offering.startDate + "T12:00:00"),
      last = new Date(offering.endDate + "T12:00:00");
    for (let n = 0; current <= last && n < 366; n++) {
      result.push({
        date: `${current.getFullYear()}-${String(current.getMonth() + 1).padStart(2, "0")}-${String(current.getDate()).padStart(2, "0")}`,
        weekday: current.getDay(),
      });
      current.setDate(current.getDate() + 1);
    }
    return result;
  }, [offering.startDate, offering.endDate]);
  function generate() {
    setError("");
    if (start >= end) {
      setError("Giờ kết thúc phải sau giờ bắt đầu.");
      return;
    }
    const list = dates
      .filter((item) => days.includes(item.weekday))
      .map((item, index) => ({
        date: item.date,
        start,
        end,
        title: `Buổi ${index + 1}`,
      }));
    if (!list.length || list.length > 100) {
      setError("Chọn lịch từ 1 đến 100 buổi trong khoảng thời gian lớp.");
      return;
    }
    setRows(list);
  }
  async function save() {
    setBusy(true);
    setError("");
    try {
      const existing = await api.get(
        `/instructor/offerings/${offering.id}/sessions`,
        token,
      );
      const first =
        existing.reduce((max, row) => Math.max(max, row.sessionNumber), 0) + 1;
      const sessions = [...rows]
        .sort((a, b) => a.date.localeCompare(b.date))
        .map((row, index) => ({
          sessionNumber: first + index,
          title: row.title,
          startsAt: `${row.date}T${row.start}:00`,
          endsAt: `${row.date}T${row.end}:00`,
          meetingProvider: "GOOGLE_MEET",
          meetingUrl: null,
          notes: null,
        }));
      await api.post(
        `/instructor/offerings/${offering.id}/schedule`,
        { sessions },
        token,
      );
      onSaved?.();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="form m-0">
      <div className="rounded-lg bg-blue-50 p-3 text-sm text-blue-900">
        <strong>
          <CalendarDays className="mr-2 inline" size={17} />
          {offering.cohortName || offering.code}
        </strong>
        <p>
          {date(offering.startDate)} — {date(offering.endDate)}
        </p>
        <p>
          Chọn thứ, giờ học và xem trước từng buổi. Lịch chỉ được lưu khi bạn
          xác nhận.
        </p>
      </div>
      {error && <div className="alert">{error}</div>}
      <fieldset>
        <legend className="mb-2 text-sm font-medium">Học vào các ngày</legend>
        <div className="flex flex-wrap gap-2">
          {[
            [1, "Thứ 2"],
            [2, "Thứ 3"],
            [3, "Thứ 4"],
            [4, "Thứ 5"],
            [5, "Thứ 6"],
            [6, "Thứ 7"],
            [0, "Chủ nhật"],
          ].map(([value, label]) => (
            <label
              key={value}
              className={`!flex min-h-11 items-center rounded-lg border px-3 ${days.includes(value) ? "border-blue-500 bg-blue-50" : "border-slate-200"}`}
            >
              <input
                type="checkbox"
                checked={days.includes(value)}
                onChange={(e) =>
                  setDays(
                    e.target.checked
                      ? [...days, value]
                      : days.filter((day) => day !== value),
                  )
                }
              />
              {label}
            </label>
          ))}
        </div>
      </fieldset>
      <div className="grid grid-cols-2 gap-3">
        <label>
          <span>Giờ bắt đầu</span>
          <input
            type="time"
            value={start}
            onChange={(e) => setStart(e.target.value)}
          />
        </label>
        <label>
          <span>Giờ kết thúc</span>
          <input
            type="time"
            value={end}
            onChange={(e) => setEnd(e.target.value)}
          />
        </label>
      </div>
      <Button variant="secondary" onClick={generate}>
        <CalendarDays size={16} />
        Tạo bảng lịch xem trước
      </Button>
      <div className="grid grid-cols-7 gap-1 rounded-xl border border-slate-200 p-3 text-center text-xs">
        {["T2", "T3", "T4", "T5", "T6", "T7", "CN"].map((text) => (
          <strong key={text}>{text}</strong>
        ))}
        {dates.slice(0, 70).map((item, index) => (
          <button
            type="button"
            key={item.date}
            style={
              index === 0
                ? { gridColumnStart: ((item.weekday + 6) % 7) + 1 }
                : undefined
            }
            title={date(item.date)}
            onClick={() => {
              if (rows.some((row) => row.date === item.date))
                setRows(rows.filter((row) => row.date !== item.date));
              else if (start < end)
                setRows([
                  ...rows,
                  {
                    date: item.date,
                    start,
                    end,
                    title: `Buổi ${rows.length + 1}`,
                  },
                ]);
            }}
            className={`min-h-11 rounded-md border p-1 ${rows.some((row) => row.date === item.date) ? "border-blue-600 bg-blue-600 text-white" : "border-slate-100 bg-slate-50"}`}
          >
            {item.date.slice(8)}/{item.date.slice(5, 7)}
          </button>
        ))}
      </div>
      {dates.length > 70 && (
        <p className="text-xs text-slate-500">
          Lưới hiển thị 70 ngày đầu. Danh sách xem trước bên dưới chứa đầy đủ
          các buổi đã tạo.
        </p>
      )}
      <div className="max-h-64 overflow-auto rounded-lg border border-slate-200">
        {[...rows]
          .sort(
            (a, b) =>
              a.date.localeCompare(b.date) || a.start.localeCompare(b.start),
          )
          .map((row, index) => (
            <div
              key={row.date}
              className="flex items-center gap-2 border-b border-slate-100 p-2"
            >
              <span className="w-32 shrink-0 text-xs font-medium">
                Buổi {index + 1} · {date(row.date)}
              </span>
              <input
                aria-label={`Tên buổi ${index + 1}`}
                value={row.title}
                onChange={(e) =>
                  setRows(
                    rows.map((item, n) =>
                      n === index ? { ...item, title: e.target.value } : item,
                    ),
                  )
                }
              />
              <span className="whitespace-nowrap text-xs">
                {row.start}–{row.end}
              </span>
              <button
                type="button"
                aria-label={`Bỏ buổi ${date(row.date)}`}
                onClick={() => setRows(rows.filter((_, n) => n !== index))}
              >
                <X size={16} />
              </button>
            </div>
          ))}
      </div>
      <p>
        {rows.length} buổi được chọn. Đường dẫn phòng học có thể bổ sung trong
        mục sửa buổi học.
      </p>
      {onRowsChange ? null : (
        <Button
          busy={busy}
          disabled={!rows.length || rows.length > 100}
          onClick={save}
        >
          <Check size={16} />
          Lưu toàn bộ lịch học
        </Button>
      )}
    </div>
  );
}
