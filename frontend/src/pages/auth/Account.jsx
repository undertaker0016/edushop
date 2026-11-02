import ImagePicker from "../../components/common/ImagePicker";
import { PasswordInput } from "../../components/ui";
import { useState } from "react";
import { api } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import Shell from "../../components/common/Shell";

export default function Account() {
  const { user, token, refresh } = useAuth();
  const [form, setForm] = useState({
    fullName: user?.fullName || "",
    phone: user?.phone || "",
    avatarUrl: user?.avatarUrl || "",
  });
  const [passwords, setPasswords] = useState({
    currentPassword: "",
    newPassword: "",
  });
  const [passwordBusy, setPasswordBusy] = useState(false);
  const [busy, setBusy] = useState(false),
    [imageBusy, setImageBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const save = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await api.put("/auth/me", form, token);
      await refresh();
      setMessage("Đã cập nhật tài khoản.");
    } catch (caught) {
      setError(caught.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Shell title="Tài khoản">
      {error && (
        <div className="alert" role="alert">
          {error}
        </div>
      )}
      <form className="panel form" onSubmit={save}>
        {message && <div className="success">{message}</div>}
        <ImagePicker
          label="Ảnh đại diện"
          variant="avatar"
          value={form.avatarUrl}
          onChange={(value) => {
            setForm({ ...form, avatarUrl: value });
            setMessage("");
          }}
          onBusyChange={setImageBusy}
        />
        <p className="text-xs text-slate-500">
          Chọn hoặc bỏ ảnh rồi nhấn “Lưu thay đổi” để cập nhật thanh tài khoản.
        </p>
        <label>
          Họ tên
          <input
            required
            maxLength={100}
            autoComplete="name"
            value={form.fullName}
            onChange={(event) =>
              setForm({ ...form, fullName: event.target.value })
            }
          />
        </label>
        <label>
          Email
          <input value={user?.email || ""} disabled />
        </label>
        <label>
          Điện thoại
          <input
            maxLength={20}
            autoComplete="tel"
            value={form.phone || ""}
            onChange={(event) =>
              setForm({ ...form, phone: event.target.value })
            }
          />
        </label>
        <button className="btn" disabled={busy || imageBusy}>
          {busy ? "Đang lưu…" : "Lưu thay đổi"}
        </button>
      </form>
      <form
        className="panel form"
        onSubmit={async (event) => {
          event.preventDefault();
          setError("");
          setPasswordBusy(true);
          try {
            await api.put("/auth/me/password", passwords, token);
            setPasswords({ currentPassword: "", newPassword: "" });
            setMessage("Đã đổi mật khẩu.");
          } catch (caught) {
            setError(caught.message);
          } finally {
            setPasswordBusy(false);
          }
        }}
      >
        <h2>Đổi mật khẩu</h2>
        <label>
          Mật khẩu hiện tại
          <PasswordInput
            required

            autoComplete="current-password"
            value={passwords.currentPassword}
            onChange={(event) =>
              setPasswords({
                ...passwords,
                currentPassword: event.target.value,
              })
            }
          />
        </label>
        <label>
          Mật khẩu mới (12–72 ký tự)
          <PasswordInput
            required

            minLength={12}
            maxLength={72}
            autoComplete="new-password"
            value={passwords.newPassword}
            onChange={(event) =>
              setPasswords({ ...passwords, newPassword: event.target.value })
            }
          />
        </label>
        <button className="btn" disabled={passwordBusy}>
          {passwordBusy ? "Đang đổi…" : "Đổi mật khẩu"}
        </button>
      </form>
    </Shell>
  );
}
