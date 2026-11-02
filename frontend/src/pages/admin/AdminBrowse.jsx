import { Plus, Eye, X } from "lucide-react";
import { label } from "../../utils/labels";
import { Link } from "react-router-dom";
import { useEffect, useState } from "react";
import { api } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import Shell from "../../components/common/Shell";
import DataTable from "../../components/tables/DataTable";
import { Loading, ErrorState } from "../../components/common/State";
function Fields({ value }) {
  if (Array.isArray(value))
    return value.length ? (
      <div>
        {value.map((row, i) => (
          <section className="panel form" key={i}>
            <Fields value={row} />
          </section>
        ))}
      </div>
    ) : (
      <p>Chưa có dữ liệu.</p>
    );
  if (value && typeof value === "object")
    return (
      <dl className="record-fields">
        {Object.entries(value).map(([key, item]) => (
          <div key={key}>
            <dt>{label(key)}</dt>
            <dd>
              <Fields value={item} />
            </dd>
          </div>
        ))}
      </dl>
    );
  return (
    <span>
      {typeof value === "boolean"
        ? value
          ? "Có"
          : "Không"
        : label(String(value ?? "—"))}
    </span>
  );
}
export default function AdminBrowse({ module, title }) {
  const { token } = useAuth(),
    [page, setPage] = useState(0),
    [query, setQuery] = useState(""),
    [status, setStatus] = useState(""),
    [data, setData] = useState(null),
    [error, setError] = useState(null),
    [loading, setLoading] = useState(true);
  const load = async (requestedPage = page) => {
    setLoading(true);
    setError(null);
    try {
      setData(
        await api.get(
          `/admin/browse/${module}?page=${requestedPage}&size=20&q=${encodeURIComponent(query)}&status=${encodeURIComponent(status)}`,
          token,
        ),
      );
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  };
  useEffect(() => {
    load();
  }, [module, page]);
  const [selected, setSelected] = useState(null);
  const [actionError, setActionError] = useState("");
  const [busy, setBusy] = useState(false);
  async function detail(row) {
    setActionError("");
    try {
      setSelected(
        ["orders", "payments", "enrollments", "users"].includes(module)
          ? await api.get(`/admin/${module}/${row.id}`, token)
          : row,
      );
    } catch (e) {
      setActionError(e.message);
    }
  }
  async function mutate(path, method) {
    if (!window.confirm("Xác nhận thực hiện thao tác này?")) return;
    setBusy(true);
    setActionError("");
    try {
      await api[method](
        path,
        ...(method === "del" ? [token] : [undefined, token]),
      );
      await load();
    } catch (e) {
      setActionError(e.message);
    } finally {
      setBusy(false);
    }
  }
  const items = data?.items || [];
  const schemas = {
    orders: [
      "id",
      "orderNumber",
      "buyerName",
      "buyerEmail",
      "totalAmount",
      "status",
      "createdAt",
    ],
    payments: [
      "id",
      "orderId",
      "provider",
      "amount",
      "status",
      "reconciliationRequired",
      "createdAt",
    ],
    enrollments: [
      "id",
      "learnerName",
      "learnerEmail",
      "offeringId",
      "status",
      "enrolledAt",
    ],
    reviews: ["id", "courseId", "rating", "comment", "hidden", "createdAt"],
  };
  const columns = (
    schemas[module] || Object.keys(items[0] || {}).slice(0, 8)
  ).map((key) => ({ key, label: label(key) }));
  columns.push({
    key: "actions",
    label: "Thao tác",
    render: (row) => (
      <div className="action-buttons">
        <button
          className="btn secondary"
          disabled={busy}
          onClick={() => detail(row)}
        >
          <Eye size={15} /> Chi tiết
        </button>
        {module === "orders" && row.status === "PENDING" && (
          <button
            className="btn danger"
            disabled={busy}
            onClick={() => mutate(`/admin/orders/${row.id}`, "del")}
          >
            <X size={15} /> Hủy đơn
          </button>
        )}
        {module === "reviews" && (
          <button
            className="btn secondary"
            disabled={busy}
            onClick={() =>
              mutate(
                `/admin/reviews/${row.id}/hidden?hidden=${!row.hidden}`,
                "patch",
              )
            }
          >
            <Eye size={15} />
            {row.hidden ? "Hiện" : "Ẩn"}
          </button>
        )}
        {module === "enrollments" && row.status === "ACTIVE" && (
          <button
            className="btn danger"
            disabled={busy}
            onClick={() =>
              mutate(`/admin/enrollments/${row.id}/cancel`, "post")
            }
          >
            <X size={15} /> Hủy quyền học
          </button>
        )}
        {module === "courses" && row.status === "PUBLISHED" && (
          <Link className="btn secondary" to={`/courses/${row.slug}`}>
            Xem công khai
          </Link>
        )}
      </div>
    ),
  });
  return (
    <Shell
      title={title}
      actions={
        <button className="btn secondary" onClick={() => load()}>
          Làm mới
        </button>
      }
    >
      {["courses", "offerings", "sessions"].includes(module) && (
        <p>
          <Link className="btn" to={`/admin/${module}/manage`}>
            <Plus size={15} /> Mở công cụ quản lý {title.toLowerCase()}
          </Link>
        </p>
      )}
      {actionError && (
        <div className="alert" role="alert">
          {actionError}
        </div>
      )}
      {selected && (
        <section className="panel form">
          <button className="btn secondary" onClick={() => setSelected(null)}>
            Đóng chi tiết
          </button>
          <Fields value={selected} />
        </section>
      )}
      <div className="filters">
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Tìm kiếm"
        />
        <input
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          placeholder="Lọc trạng thái"
        />
        <button
          className="btn"
          onClick={() => {
            setPage(0);
            load(0);
          }}
        >
          Áp dụng
        </button>
      </div>
      {loading ? (
        <Loading />
      ) : error ? (
        <ErrorState error={error} retry={load} />
      ) : (
        <>
          <DataTable rows={items} columns={columns} />
          <div className="pagination">
            <button
              className="btn secondary"
              disabled={page <= 0}
              onClick={() => setPage((v) => v - 1)}
            >
              Trước
            </button>
            <span>
              Trang {page + 1}/{Math.max(1, data.totalPages)}
            </span>
            <button
              className="btn secondary"
              disabled={page + 1 >= data.totalPages}
              onClick={() => setPage((v) => v + 1)}
            >
              Sau
            </button>
          </div>
        </>
      )}
    </Shell>
  );
}
