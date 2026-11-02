import { Link } from "react-router-dom";
import { useState } from "react";
import useApi from "../../hooks/useApi";
import { api } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import Shell from "../../components/common/Shell";
import DataTable from "../../components/tables/DataTable";
import { Loading, ErrorState } from "../../components/common/State";
import { date, money } from "../../utils/format";

export default function Orders({ embedded = false }) {
  const data = useApi("/orders", true);
  const { token } = useAuth();
  const [error, setError] = useState("");
  const cancel = async (id) => {
    if (!confirm("Huỷ đơn hàng này?")) return;
    try {
      await api.del(`/orders/${id}`, token);
      data.reload();
    } catch (e) {
      setError(e.message);
    }
  };
  if (data.loading) return <Loading />;
  if (data.error) return <ErrorState error={data.error} retry={data.reload} />;
  const content = (
    <>
      {error && <div className="alert">{error}</div>}
      <DataTable
        rows={data.data || []}
        columns={[
          { key: "id", label: "ID" },
          {
            key: "orderNumber",
            label: "Mã đơn",
            render: (row) => (
              <Link
                className="text-blue-600 underline"
                to={`/buyer/orders/${row.id}`}
              >
                {row.orderNumber}
              </Link>
            ),
          },
          { key: "status", label: "Trạng thái" },
          {
            key: "totalAmount",
            label: "Tổng",
            render: (row) => money(row.totalAmount),
          },
          {
            key: "createdAt",
            label: "Tạo lúc",
            render: (row) => date(row.createdAt),
          },
          {
            key: "action",
            label: "Thao tác",
            render: (row) =>
              row.status === "PENDING" ? (
                <button className="btn danger" onClick={() => cancel(row.id)}>
                  Huỷ
                </button>
              ) : null,
          },
        ]}
      />
    </>
  );
  return embedded ? content : <Shell title="Đơn hàng">{content}</Shell>;
}
