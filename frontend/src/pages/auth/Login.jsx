import { PasswordInput } from "../../components/ui";
import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../../contexts/AuthContext";
import { paymentReference } from "../../utils/payment";

export default function Login() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const location = useLocation();
  const { login } = useAuth();
  const navigate = useNavigate();
  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const profile = await login(email.trim(), password);
      const reference = paymentReference(location.state?.paymentReference);
      if (reference) {
        navigate(`/payment/return?reference=${encodeURIComponent(reference)}`, {
          replace: true,
        });
        return;
      }
      navigate(
        profile.roles?.includes("ADMIN")
          ? "/admin"
          : profile.roles?.includes("INSTRUCTOR")
            ? "/instructor"
            : "/buyer",
      );
    } catch (caught) {
      setError(
        caught.status === 401
          ? "Email hoặc mật khẩu không đúng."
          : caught.message,
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="auth-page">
      <form className="auth-card" onSubmit={submit}>
        <div>
          <span className="eyebrow">Chào mừng trở lại</span>
          <h1>Đăng nhập</h1>
        </div>
        {location.state?.registered && (
          <div className="success">
            Tạo tài khoản thành công. Bạn có thể đăng nhập.
          </div>
        )}
        {error && <div className="alert">{error}</div>}
        <label>
          Email
          <input
            type="email"
            required
            autoComplete="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </label>
        <label>
          Mật khẩu
          <PasswordInput
            required
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </label>
        <button className="btn" disabled={busy}>
          {busy ? "Đang đăng nhập…" : "Đăng nhập"}
        </button>
        <Link to="/auth/forgot-password">Quên mật khẩu?</Link>
        <p>
          Chưa có tài khoản? <Link to="/auth/register">Đăng ký ngay</Link>
        </p>
      </form>
    </main>
  );
}
