import { Link, useLocation } from "react-router-dom";
import { ShoppingCart, ReceiptText } from "lucide-react";
import { useCart } from "../../contexts/CartContext";
import Shell from "../../components/common/Shell";
import Cart from "./Cart";
import Orders from "./Orders";
export default function ShoppingHub() {
  const path = useLocation().pathname;
  const orders = path.endsWith("/orders");
  const { count } = useCart();
  return (
    <Shell
      title="Mua sắm của tôi"
      subtitle="Giỏ hàng và lịch sử đăng ký được quản lý trong cùng một nơi."
    >
      <nav
        className="mb-5 flex gap-2 border-b border-slate-200"
        aria-label="Giỏ hàng và đơn hàng"
      >
        <Link
          className={`inline-flex items-center gap-2 border-b-2 px-4 py-3 font-medium ${!orders ? "border-blue-600 text-blue-600" : "border-transparent text-slate-500"}`}
          to="/buyer/cart"
        >
          <ShoppingCart size={17} />
          Giỏ hàng ({count})
        </Link>
        <Link
          className={`inline-flex items-center gap-2 border-b-2 px-4 py-3 font-medium ${orders ? "border-blue-600 text-blue-600" : "border-transparent text-slate-500"}`}
          to="/buyer/orders"
        >
          <ReceiptText size={17} />
          Đơn đã đăng ký
        </Link>
      </nav>
      {orders ? <Orders embedded /> : <Cart />}
    </Shell>
  );
}
