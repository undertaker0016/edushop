import { Link } from "react-router-dom";

export default function Forbidden() {
  return (
    <main className="page state">
      <h1>403</h1>
      <p>Tài khoản của bạn không có quyền truy cập khu vực này.</p>
      <Link className="btn" to="/">
        Về trang chủ
      </Link>
    </main>
  );
}
