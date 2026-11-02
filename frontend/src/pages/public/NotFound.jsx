import { Link } from "react-router-dom";
import { SearchX } from "lucide-react";
export default function NotFound() {
  return (
    <main className="page py-20 text-center">
      <SearchX size={42} className="mx-auto mb-4 text-slate-400" />
      <h1>404 — Không tìm thấy trang</h1>
      <p className="mb-6 text-slate-500">
        Đường dẫn không tồn tại hoặc đã thay đổi.
      </p>
      <Link className="btn" to="/courses">
        Khám phá khóa học
      </Link>
    </main>
  );
}
