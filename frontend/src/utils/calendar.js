// Portable calendar export: no API keys, emails, room passwords or private meeting URLs.
function escapeText(value) {
  return String(value ?? "")
    .replace(/\\/g, "\\\\")
    .replace(/\r\n|\r|\n/g, "\\n")
    .replace(/;/g, "\\;")
    .replace(/,/g, "\\,");
}
function utc(value) {
  const input = /(?:Z|[+-]\d{2}:\d{2})$/.test(value) ? value : `${value}+07:00`;
  const date = new Date(input);
  if (Number.isNaN(date.getTime()))
    throw new Error("Lịch học có ngày giờ không hợp lệ.");
  return date
    .toISOString()
    .replace(/[-:]/g, "")
    .replace(/\.\d{3}Z$/, "Z");
}
function fold(line) {
  const encoder = new TextEncoder();
  let result = "",
    size = 0;
  for (const character of line) {
    const bytes = encoder.encode(character).length;
    if (size + bytes > 75) {
      result += "\r\n ";
      size = 1;
    }
    result += character;
    size += bytes;
  }
  return result;
}
export function classCalendar(enrollment, sessions, now = new Date()) {
  const lines = [
    "BEGIN:VCALENDAR",
    "VERSION:2.0",
    "PRODID:-//EduShop//Class Schedule//VI",
    "CALSCALE:GREGORIAN",
    "METHOD:PUBLISH",
  ];
  for (const session of sessions.filter((row) => row.status !== "CANCELLED")) {
    const start = utc(session.startsAt),
      end = utc(session.endsAt);
    if (end <= start) throw new Error("Giờ kết thúc phải sau giờ bắt đầu.");
    lines.push(
      "BEGIN:VEVENT",
      `UID:enrollment-${enrollment.id}-session-${session.sessionNumber}@edushop.local`,
      `DTSTAMP:${utc(now.toISOString())}`,
      `DTSTART:${start}`,
      `DTEND:${end}`,
      `SUMMARY:${escapeText(`${enrollment.courseTitle} — Buổi ${session.sessionNumber}: ${session.title}`)}`,
      `DESCRIPTION:${escapeText(`Lớp ${enrollment.offeringCode}. Mở EduShop để xem phòng học và lịch mới nhất. Lịch tải về không tự đồng bộ khi giảng viên thay đổi.`)}`,
      "LOCATION:EduShop",
      "STATUS:CONFIRMED",
      "END:VEVENT",
    );
  }
  if (!lines.includes("BEGIN:VEVENT"))
    throw new Error("Lớp chưa có buổi học để xuất lịch.");
  lines.push("END:VCALENDAR");
  return lines.map(fold).join("\r\n") + "\r\n";
}
export function downloadClassCalendar(enrollment, sessions) {
  const content = classCalendar(enrollment, sessions);
  const url = URL.createObjectURL(
    new Blob([content], { type: "text/calendar;charset=utf-8" }),
  );
  const link = document.createElement("a");
  link.href = url;
  link.download = `edushop-lop-${enrollment.id}.ics`;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
