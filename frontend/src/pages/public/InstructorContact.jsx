import { useState } from "react";
import { Link } from "react-router-dom";
import { GraduationCap, Send } from "lucide-react";
import { useAuth } from "../../contexts/AuthContext";
import { api } from "../../api/client";
import { Button } from "../../components/ui";
export default function InstructorContact() {
  const { user, token } = useAuth();
  const [form, setForm] = useState({ expertise: "", message: "", phone: "" }),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [success, setSuccess] = useState("");
  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const result = await api.post("/instructor-applications", form, token);
      setSuccess(result.message);
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="page">
      <div className="mx-auto grid max-w-5xl gap-8 lg:grid-cols-2">
        <section className="py-4">
          <GraduationCap className="mb-4 text-blue-600" size={40} />
          <span className="eyebrow">Cùng EduShop chia sẻ kiến thức</span>
          <h1 className="my-3">Trở thành giảng viên</h1>
          <p className="text-slate-500">
            Giới thiệu chuyên môn, kinh nghiệm và nội dung bạn muốn giảng dạy.
            Quản trị viên sẽ xem hồ sơ và trao đổi qua thông tin tài khoản của
            bạn.
          </p>
          <ol className="mt-6 grid gap-4 text-sm">
            <li>1. Gửi hồ sơ và chủ đề giảng dạy.</li>
            <li>2. Đội ngũ EduShop xem xét và liên hệ.</li>
            <li>
              3. Sau khi được cấp quyền, bạn tạo khóa học và lên lịch lớp.
            </li>
          </ol>
        </section>
        <section className="panel">
          {!user ? (
            <>
              <h2>Bắt đầu với tài khoản EduShop</h2>
              <p>
                Đăng nhập để gửi hồ sơ, theo dõi thông báo và tránh nhập lại
                email.
              </p>
              <Link className="btn mt-4" to="/auth/login">
                Đăng nhập để gửi hồ sơ
              </Link>
            </>
          ) : success ? (
            <div className="success" role="status">
              {success}
            </div>
          ) : (
            <form className="form m-0" onSubmit={submit}>
              <h2>Hồ sơ liên hệ giảng dạy</h2>
              {error && <div className="alert">{error}</div>}
              <label>
                Họ tên
                <input readOnly value={user.fullName} />
              </label>
              <label>
                Email nhận phản hồi
                <input readOnly value={user.email} />
              </label>
              <label>
                Điện thoại
                <input
                  maxLength={20}
                  value={form.phone}
                  onChange={(e) => setForm({ ...form, phone: e.target.value })}
                />
              </label>
              <label>
                Chuyên môn / chủ đề muốn dạy
                <input
                  required
                  maxLength={255}
                  value={form.expertise}
                  onChange={(e) =>
                    setForm({ ...form, expertise: e.target.value })
                  }
                />
              </label>
              <label>
                Kinh nghiệm và đề xuất khóa học
                <textarea
                  required
                  maxLength={10000}
                  value={form.message}
                  onChange={(e) =>
                    setForm({ ...form, message: e.target.value })
                  }
                />
              </label>
              <Button type="submit" busy={busy}>
                <Send size={16} />
                Gửi hồ sơ
              </Button>
            </form>
          )}
        </section>
      </div>
    </main>
  );
}
