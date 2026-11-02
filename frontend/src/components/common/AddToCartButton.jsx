import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { ShoppingCart } from "lucide-react";
import { useAuth } from "../../contexts/AuthContext";
import { useCart } from "../../contexts/CartContext";
import useApi from "../../hooks/useApi";
import { Modal, Button } from "../ui";
import { Loading, ErrorState, Empty } from "./State";
import { date, money } from "../../utils/format";
export default function AddToCartButton({ course }) {
  const { user } = useAuth();
  const { add } = useCart();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false),
    [message, setMessage] = useState("");
  const data = useApi(open ? `/courses/${course.slug}/offerings` : null);
  return (
    <div className="min-w-0 flex-1">
      <button
        type="button"
        className="btn secondary w-full"
        onClick={() => {
          if (!user) {
            navigate("/auth/login");
            return;
          }
          setMessage("");
          setOpen(true);
        }}
      >
        <ShoppingCart size={16} />
        Thêm giỏ
      </button>
      {message && (
        <small role="status" className="mt-1 block text-xs text-blue-600">
          {message}
        </small>
      )}
      <Modal
        open={open}
        dismissible
        onClose={() => setOpen(false)}
        title="Chọn lớp để thêm vào giỏ"
      >
        {data.loading ? (
          <Loading />
        ) : data.error ? (
          <ErrorState error={data.error} retry={data.reload} />
        ) : data.data?.length ? (
          <div className="grid gap-3">
            {data.data.map((row) => (
              <article className="panel" key={row.id}>
                <h3>{row.cohortName || course.title}</h3>
                <p className="text-sm text-slate-500">
                  {date(row.startDate)} · {row.scheduleSummary}
                </p>
                <div className="mt-3 flex items-center justify-between">
                  <strong>{money(row.price)}</strong>
                  <Button
                    onClick={() => {
                      try {
                        const added = add(course, row);
                        setMessage(
                          added ? "Đã thêm vào giỏ." : "Lớp đã có trong giỏ.",
                        );
                        setOpen(false);
                      } catch (e) {
                        setMessage(e.message);
                        setOpen(false);
                      }
                    }}
                  >
                    Chọn lớp này
                  </Button>
                </div>
              </article>
            ))}
          </div>
        ) : (
          <Empty>Chưa có lớp đang mở bán.</Empty>
        )}
      </Modal>
    </div>
  );
}
