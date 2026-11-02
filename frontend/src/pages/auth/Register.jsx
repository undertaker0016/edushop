import { PasswordInput } from "../../components/ui";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api } from "../../api/client";

export default function Register() {
  const [form, setForm] = useState({
    fullName: "",
    email: "",
    password: "",
    phone: "",
  });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      await api.post("/auth/register", {
        ...form,
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        phone: form.phone.trim() || null,
      });
      navigate("/auth/login", { state: { registered: true } });
    } catch (error) {
      setError(error.status === 409 ? "Email đã được sử dụng" : error.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="auth-page">
      <form className="auth-card" onSubmit={submit}>
        <h1>Tạo tài khoản học viên</h1>
        {error && <div className="alert">{error}</div>}
        <label>
          Họ và tên
          <input
            autoComplete="name"
            maxLength="100"
            required
            minLength="2"
            value={form.fullName}
            onChange={(event) =>
              setForm({ ...form, fullName: event.target.value })
            }
          />
        </label>
        <label>
          Email
          <input
            type="email"
            autoComplete="email"
            maxLength="255"
            required
            value={form.email}
            onChange={(event) =>
              setForm({ ...form, email: event.target.value })
            }
          />
        </label>
        <label>
          Mật khẩu
          <PasswordInput
            required
            minLength="12"
            maxLength="72"
            autoComplete="new-password"
            value={form.password}
            onChange={(event) =>
              setForm({ ...form, password: event.target.value })
            }
          />
        </label>
        <small>Mật khẩu từ 12 đến 72 ký tự.</small>
        <label>
          Số điện thoại (không bắt buộc)
          <input
            type="tel"
            autoComplete="tel"
            maxLength="20"
            value={form.phone}
            onChange={(event) =>
              setForm({ ...form, phone: event.target.value })
            }
          />
        </label>
        <button className="btn" disabled={busy}>
          {busy ? "Đang tạo…" : "Đăng ký"}
        </button>
        <p>
          Đã có tài khoản? <Link to="/auth/login">Đăng nhập</Link>
        </p>
      </form>
    </main>
  );
}
