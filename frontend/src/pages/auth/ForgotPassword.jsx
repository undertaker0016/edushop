import { PasswordInput } from "../../components/ui";
import { useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../../api/client";
export default function ForgotPassword() {
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [step, setStep] = useState(1);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [nextSend, setNextSend] = useState(0);
  async function send() {
    if (Date.now() < nextSend) {
      setError("Vui lòng chờ đủ 60 giây trước khi gửi lại.");
      return;
    }
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const result = await api.post("/auth/password/request", {
        email: email.trim(),
      });
      setMessage(result.message);
      setStep(2);
      setNextSend(Date.now() + 60000);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function reset(event) {
    event.preventDefault();
    setError("");
    if (password !== confirm) {
      setError("Mật khẩu xác nhận không khớp.");
      return;
    }
    setBusy(true);
    try {
      const result = await api.post("/auth/password/reset", {
        email: email.trim(),
        code,
        newPassword: password,
      });
      setMessage(result.message);
      setPassword("");
      setConfirm("");
      setCode("");
      setStep(3);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="auth-page">
      <section className="auth-card">
        <span className="eyebrow">Khôi phục tài khoản</span>
        <h1>Quên mật khẩu</h1>
        <p>Nhận mã xác nhận 6 số qua email để đặt lại mật khẩu.</p>
        {message && (
          <div className="success" role="status">
            {message}
          </div>
        )}
        {error && (
          <div className="alert" role="alert">
            {error}
          </div>
        )}
        {step === 1 && (
          <form
            className="auth-fields"
            onSubmit={(event) => {
              event.preventDefault();
              send();
            }}
          >
            <label>
              Email đăng ký
              <input
                type="email"
                required
                maxLength={255}
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </label>
            <button className="btn" disabled={busy}>
              {busy ? "Đang gửi…" : "Gửi mã xác nhận"}
            </button>
          </form>
        )}
        {step === 2 && (
          <form className="auth-fields" onSubmit={reset}>
            <p>
              Mã được gửi đến <strong>{email}</strong> và có hiệu lực 10 phút.
              Kiểm tra cả thư rác.
            </p>
            <label>
              Mã xác nhận
              <input
                inputMode="numeric"
                pattern="[0-9]{6}"
                maxLength={6}
                required
                autoComplete="one-time-code"
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
              />
            </label>
            <label>
              Mật khẩu mới
              <PasswordInput
                required
                minLength={12}
                maxLength={72}
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </label>
            <small>Từ 12 đến 72 ký tự; tối đa 72 byte.</small>
            <label>
              Nhập lại mật khẩu
              <PasswordInput
                required
                autoComplete="new-password"
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
              />
            </label>
            <button className="btn" disabled={busy}>
              Xác nhận đổi mật khẩu
            </button>
            <button
              className="btn secondary"
              type="button"
              disabled={busy}
              onClick={send}
            >
              Gửi lại mã (sau 60 giây)
            </button>
            <button
              className="link"
              type="button"
              disabled={busy}
              onClick={() => {
                setStep(1);
                setCode("");
                setMessage("");
                setError("");
              }}
            >
              Đổi email
            </button>
          </form>
        )}
        <Link to="/auth/login">← Quay lại đăng nhập</Link>
      </section>
    </main>
  );
}
