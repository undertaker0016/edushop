import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import useApi from "../../hooks/useApi";
import { api } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import Shell from "../../components/common/Shell";
import { Loading, ErrorState, Empty } from "../../components/common/State";
import { Button, Pagination } from "../../components/ui";
import { date } from "../../utils/format";

export default function Notifications() {
  const [params, setParams] = useSearchParams();
  const { token } = useAuth();
  const page = Math.max(0, Number(params.get("page")) || 0);
  const read = params.get("read") || "";
  const type = params.get("type") || "";
  const data = useApi(
    `/notifications/center?page=${page}&size=20&read=${read}&type=${encodeURIComponent(type)}`,
    true,
  );
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  function update(key, value) {
    const next = new URLSearchParams(params);
    value === "" ? next.delete(key) : next.set(key, value);
    if (key !== "page") next.delete("page");
    setParams(next);
  }
  async function mark(id) {
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      await api.patch(
        id ? `/notifications/${id}/read` : "/notifications/read-all",
        {},
        token,
      );
      data.reload();
      window.dispatchEvent(new Event("edushop:notifications-changed"));
    } catch (caught) {
      setError(caught.message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Shell
      title="Trung tâm thông báo"
      subtitle="Cập nhật từ lớp học và EduShop."
      actions={
        <Button variant="secondary" busy={busy} onClick={() => mark(null)}>
          Đánh dấu tất cả đã đọc
        </Button>
      }
    >
      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}
      <div className="filters">
        <label>
          Trạng thái
          <select value={read} onChange={(e) => update("read", e.target.value)}>
            <option value="">Tất cả</option>
            <option value="false">Chưa đọc</option>
            <option value="true">Đã đọc</option>
          </select>
        </label>
        <label>
          Loại thông báo
          <select value={type} onChange={(e) => update("type", e.target.value)}>
            <option value="">Tất cả loại</option>
            {data.data?.types?.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </label>
      </div>
      {data.loading ? (
        <Loading />
      ) : data.error ? (
        <ErrorState error={data.error} retry={data.reload} />
      ) : (
        <>
          <div className="grid gap-3">
            {data.data?.items.map((item) => (
              <article className="panel p-5" key={item.id}>
                <div className="flex flex-wrap justify-between gap-3">
                  <h2 className="text-lg font-semibold">{item.title}</h2>
                  <span className="text-sm text-slate-500">
                    {date(item.createdAt)} · {item.read ? "Đã đọc" : "Chưa đọc"}
                  </span>
                </div>
                <p className="my-3 whitespace-pre-wrap break-words">
                  {item.body}
                </p>
                {item.actionUrl?.startsWith("/") &&
                  !item.actionUrl.startsWith("//") && (
                    <Link
                      className="btn secondary mr-2"
                      to={item.actionUrl}
                      onClick={() => {
                        if (!item.read) mark(item.id);
                      }}
                    >
                      {item.type === "INSTRUCTOR_APPLICATION"
                        ? "Xem hồ sơ đăng ký"
                        : item.type === "ENROLLMENT_CREATED" ||
                            item.type === "ORDER_PAID"
                          ? "Mở lớp đã đăng ký"
                          : "Xem chi tiết"}{" "}
                      →
                    </Link>
                  )}
                {!item.read && (
                  <Button
                    variant="secondary"
                    busy={busy}
                    onClick={() => mark(item.id)}
                  >
                    Đánh dấu đã đọc
                  </Button>
                )}
              </article>
            ))}
          </div>
          {!data.data?.items.length && (
            <Empty>
              Không có thông báo phù hợp. Hãy đổi bộ lọc hoặc quay lại sau.
            </Empty>
          )}
          <Pagination
            page={page}
            totalPages={data.data?.totalPages || 0}
            onChange={(value) => update("page", value)}
          />
        </>
      )}
    </Shell>
  );
}
