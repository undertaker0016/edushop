import { useState } from "react";
import { useWishlist } from "../../contexts/WishlistContext";
import { Link } from "react-router-dom";
import Shell from "../../components/common/Shell";
import DataTable from "../../components/tables/DataTable";
import { Loading, ErrorState } from "../../components/common/State";
import { date } from "../../utils/format";

export default function Wishlist() {
  const data = useWishlist();
  const [error, setError] = useState("");
  const remove = async (courseId) => {
    try {
      await data.toggle({ courseId });
    } catch (error) {
      setError(error.message);
    }
  };
  if (data.loading) return <Loading />;
  if (data.error) return <ErrorState error={data.error} />;
  return (
    <Shell title="Khóa học yêu thích">
      {error && <div className="alert">{error}</div>}
      <DataTable
        rows={data.rows}
        columns={[
          { key: "courseId", label: "Mã khóa học" },
          {
            key: "title",
            label: "Khóa học",
            render: (row) => (
              <Link to={`/courses/${row.slug}`}>{row.title}</Link>
            ),
          },
          {
            key: "addedAt",
            label: "Lưu lúc",
            render: (row) => date(row.addedAt),
          },
          {
            key: "action",
            label: "Thao tác",
            render: (row) => (
              <button
                className="btn danger"
                disabled={data.pending.includes(row.courseId)}
                onClick={() => remove(row.courseId)}
              >
                Bỏ lưu
              </button>
            ),
          },
        ]}
      />
    </Shell>
  );
}
