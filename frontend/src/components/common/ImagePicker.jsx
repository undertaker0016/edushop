import { useRef, useState } from "react";
import {
  ImagePlus,
  Upload,
  X,
  Link as LinkIcon,
  LoaderCircle,
} from "lucide-react";
import { api, mediaUrl } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
export default function ImagePicker({
  value,
  onChange,
  onBusyChange = () => {},
  label = "Ảnh khóa học",
  variant,
}) {
  const { token } = useAuth();
  const avatar =
    variant === "avatar" ||
    label.toLowerCase().includes("đại diện") ||
    label.toLowerCase().includes("giảng viên");
  const input = useRef(null);
  const [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [external, setExternal] = useState(false);
  async function choose(event) {
    const file = event.target.files?.[0];
    if (!file) return;
    setError("");
    if (
      !["image/jpeg", "image/png"].includes(file.type) ||
      file.size > 8 * 1024 * 1024
    ) {
      setError("Chọn ảnh JPG hoặc PNG không quá 8 MB.");
      return;
    }
    setBusy(true);
    onBusyChange(true);
    try {
      const body = new FormData();
      body.append("file", file);
      const result = await api.post("/media", body, token);
      onChange(result.url);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
      onBusyChange(false);
      event.target.value = "";
    }
  }
  return (
    <section className="rounded-xl border border-dashed border-slate-300 bg-slate-50 p-4">
      <h3 className="mb-3 text-sm">{label}</h3>
      {value ? (
        <div
          className={
            avatar
              ? "relative mx-auto mb-3 w-fit rounded-xl bg-white p-2"
              : "relative mb-3"
          }
        >
          <img
            src={mediaUrl(value)}
            alt="Ảnh xem trước"
            className={
              avatar
                ? "size-48 max-w-full rounded-lg object-contain"
                : "h-44 w-full rounded-lg object-cover"
            }
          />
          <button
            type="button"
            className="absolute right-2 top-2 rounded-full bg-white p-2 shadow"
            aria-label="Bỏ ảnh đã chọn"
            onClick={() => onChange("")}
            disabled={busy}
          >
            <X size={16} />
          </button>
        </div>
      ) : (
        <div className="mb-3 grid h-24 place-items-center text-slate-400">
          <ImagePlus size={32} />
        </div>
      )}
      <input
        ref={input}
        type="file"
        accept="image/jpeg,image/png"
        className="sr-only"
        aria-label={`Chọn ${label.toLowerCase()} từ máy`}
        onChange={choose}
      />
      <div className="flex flex-wrap gap-2">
        <button
          className="btn secondary"
          type="button"
          disabled={busy}
          onClick={() => input.current.click()}
        >
          {busy ? (
            <LoaderCircle className="animate-spin" size={16} />
          ) : (
            <Upload size={16} />
          )}{" "}
          {busy ? "Đang tải ảnh…" : "Chọn ảnh từ máy"}
        </button>
        <button
          className="link"
          type="button"
          onClick={() => setExternal(!external)}
        >
          <LinkIcon size={15} className="mr-1 inline" />
          Dùng đường dẫn có sẵn
        </button>
      </div>
      <p className="mt-2 text-xs text-slate-500">
        JPG/PNG · Tối đa 8 MB. Ảnh được tải lên và lưu trong hệ thống.
      </p>
      {external && (
        <label className="mt-3 grid gap-1 text-sm">
          Đường dẫn ảnh
          <input
            value={value || ""}
            onChange={(e) => onChange(e.target.value)}
            placeholder="https://..."
            className="rounded-lg border border-slate-300 bg-white p-2"
          />
        </label>
      )}
      {error && (
        <p className="mt-2 text-sm text-red-600" role="alert">
          {error}
        </p>
      )}
    </section>
  );
}
