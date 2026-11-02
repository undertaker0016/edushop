import { useEffect, useState } from "react";
import { api } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import Shell from "../../components/common/Shell";
import DataTable from "../../components/tables/DataTable";
import { ErrorState, Loading } from "../../components/common/State";
import { ConfirmDialog } from "../../components/ui";

export default function Tags() {
  const { token } = useAuth();
  const [rows, setRows] = useState([]);
  const [editing, setEditing] = useState(null);
  const [name, setName] = useState("");
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true),
    [busy, setBusy] = useState(false),
    [message, setMessage] = useState(""),
    [pending, setPending] = useState(null),
    [query, setQuery] = useState("");
  const load = () => {
    setError(null);
    return api
      .get("/admin/catalog/tags", token)
      .then(setRows)
      .catch(setError)
      .finally(() => setLoading(false));
  };
  useEffect(() => {
    load();
  }, []);
  async function save(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setMessage("");
    try {
      if (editing)
        await api.put(
          `/admin/catalog/tags/${encodeURIComponent(editing)}`,
          { name },
          token,
        );
      else await api.post("/admin/catalog/tags", { name }, token);
      setEditing(null);
      setName("");
      setMessage(editing ? "Đã cập nhật thẻ." : "Đã thêm thẻ.");
      await load();
    } catch (e) {
      setError(e);
      return;
    } finally {
      setBusy(false);
    }
  }
  async function remove(row) {
    setBusy(true);
    setError(null);
    setMessage("");
    try {
      await api.del(
        `/admin/catalog/tags/${encodeURIComponent(row.name)}`,
        token,
      );
      setPending(null);
      setMessage("Đã xóa thẻ chưa được sử dụng.");
      await load();
    } catch (e) {
      setError(e);
      setPending(null);
    } finally {
      setBusy(false);
    }
  }
  if (loading) return <Loading />;
  return (
    <Shell title="Thẻ">
      {error && <ErrorState error={error} retry={load} />}
      {message && (
        <p role="status" className="success">
          {message}
        </p>
      )}
      <p className="text-sm text-slate-500">
        Thẻ đang gắn với khóa học được bảo vệ khỏi xóa để giữ quan hệ dữ liệu.
      </p>
      <form className="panel form inline" onSubmit={save}>
        <label>
          Tên thẻ
          <input
            required
            maxLength="50"
            value={name}
            onChange={(e) => setName(e.target.value)}
            aria-label="Tên thẻ"
            placeholder="Tên thẻ"
          />
        </label>
        <button className="btn" disabled={busy}>
          {busy ? "Đang lưu…" : editing ? "Lưu thay đổi" : "Thêm thẻ"}
        </button>
        {editing && (
          <button
            className="btn secondary"
            type="button"
            disabled={busy}
            onClick={() => {
              setEditing(null);
              setName("");
              setError(null);
            }}
          >
            Hủy sửa
          </button>
        )}
      </form>
      <label className="form mb-4">
        Tìm thẻ
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Nhập tên thẻ"
        />
      </label>
      <DataTable
        rows={rows.filter((row) =>
          row.name
            .toLocaleLowerCase("vi")
            .includes(query.toLocaleLowerCase("vi")),
        )}
        columns={[
          { key: "name", label: "Tên" },
          {
            key: "action",
            label: "Thao tác",
            render: (row) => (
              <div className="action-buttons">
                <button
                  className="btn secondary"
                  disabled={busy}
                  onClick={() => {
                    setEditing(row.name);
                    setName(row.name);
                  }}
                >
                  Sửa
                </button>
                <button
                  className="btn danger"
                  disabled={busy}
                  onClick={() => setPending(row)}
                >
                  Xóa
                </button>
              </div>
            ),
          },
        ]}
      />
      <ConfirmDialog
        open={!!pending}
        busy={busy}
        onClose={() => setPending(null)}
        onConfirm={() => remove(pending)}
        title="Xóa thẻ chưa sử dụng"
        children={`Xóa vĩnh viễn thẻ “${pending?.name || ""}”? Hệ thống sẽ từ chối nếu thẻ đang gắn với khóa học.`}
      />
    </Shell>
  );
}
