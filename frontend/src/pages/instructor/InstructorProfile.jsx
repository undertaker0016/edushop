import ImagePicker from "../../components/common/ImagePicker";
import { useEffect, useState } from "react";
import { api } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import Shell from "../../components/common/Shell";
import { Loading, ErrorState } from "../../components/common/State";

export default function InstructorProfile() {
  const { token, refresh } = useAuth();
  const [form, setForm] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [imageBusy, setImageBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [dirty, setDirty] = useState(false);
  useEffect(() => {
    api.get("/instructor/profile", token).then(setForm).catch(setError);
  }, [token]);
  if (error && !form) return <ErrorState error={error} />;
  if (!form) return <Loading />;
  const change = (key, value) => {
    setForm((previous) => ({ ...previous, [key]: value }));
    setMessage("");
    setDirty(true);
  };
  const set = (key) => (event) => change(key, event.target.value);
  const save = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setMessage("");
    try {
      setForm(await api.put("/instructor/profile", form, token));
      await refresh();
      setDirty(false);
      setMessage("Đã lưu hồ sơ.");
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };
  return (
    <Shell title="Hồ sơ giảng viên">
      <form className="panel form" onSubmit={save}>
        {error && <ErrorState error={error} />}
        {message && <div className="success">{message}</div>}
        <label>
          Họ tên
          <input
            required
            maxLength={100}
            value={form.fullName || ""}
            onChange={set("fullName")}
          />
        </label>
        <label>
          Tiểu sử
          <textarea value={form.bio || ""} onChange={set("bio")} />
        </label>
        <ImagePicker
          value={form.avatarUrl}
          onChange={(value) => change("avatarUrl", value)}
          label="Ảnh đại diện"
          onBusyChange={setImageBusy}
        />
        <label>
          Email liên hệ
          <input
            type="email"
            value={form.contactEmail || ""}
            onChange={set("contactEmail")}
          />
        </label>
        <label>
          Điện thoại
          <input value={form.phone || ""} onChange={set("phone")} />
        </label>
        <label>
          Zalo
          <input value={form.zalo || ""} onChange={set("zalo")} />
        </label>
        <label>
          Facebook
          <input value={form.facebookUrl || ""} onChange={set("facebookUrl")} />
        </label>
        <p className="text-xs text-slate-500">
          Ảnh và tên được cập nhật trên thanh tài khoản sau khi bạn lưu hồ sơ.
          Bỏ ảnh rồi lưu để dùng chữ cái đại diện.
        </p>
        {dirty && (
          <p role="status" className="text-sm text-amber-700">
            Bạn có thay đổi chưa lưu.
          </p>
        )}
        <button className="btn" disabled={busy || imageBusy}>
          {busy ? "Đang lưu hồ sơ…" : "Lưu hồ sơ"}
        </button>
      </form>
    </Shell>
  );
}
