import CourseImage from "../../components/common/CourseImage";
import { Trash2, RotateCcw, Eye } from "lucide-react";
import ActionNotice from "../../components/common/ActionNotice";
import { useNavigate, Link } from "react-router-dom";
import ImagePicker from "../../components/common/ImagePicker";
import { useState } from "react";
import { Plus, Pencil, Archive, Check, Search } from "lucide-react";
import useApi from "../../hooks/useApi";
import { api } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import Shell from "../../components/common/Shell";
import DataTable from "../../components/tables/DataTable";
import { Loading, ErrorState } from "../../components/common/State";
import { Modal, Button, ConfirmDialog, Tabs } from "../../components/ui";
const initial = {
  title: "",
  slug: "",
  description: "",
  thumbnailUrl: "",
  categorySlugs: [],
  tags: [],
};
export default function InstructorCourses() {
  const data = useApi("/instructor/courses?includeDeleted=true", true),
    categories = useApi("/categories");
  const { token, user } = useAuth();
  const navigate = useNavigate();
  const prefix = user.roles.includes("ADMIN") ? "/admin" : "/instructor";
  const [section, setSection] = useState("active"),
    [viewing, setViewing] = useState(null);
  const [notice, setNotice] = useState("");
  const [imageBusy, setImageBusy] = useState(false);
  const [form, setForm] = useState(initial),
    [editing, setEditing] = useState(null),
    [open, setOpen] = useState(false),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [query, setQuery] = useState(""),
    [confirmation, setConfirmation] = useState(null);
  function field(key, value) {
    setForm((previous) => ({ ...previous, [key]: value }));
  }
  async function save(event) {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const payload = {
        ...form,
        tags: form.tags.map((value) => value.trim()).filter(Boolean),
      };
      let saved;
      if (editing)
        saved = await api.put(`/instructor/courses/${editing}`, payload, token);
      else saved = await api.post("/instructor/courses", payload, token);
      if (!editing) {
        setOpen(false);
        navigate(`${prefix}/offerings?courseId=${saved.id}&setup=1`);
        return;
      }
      setOpen(false);
      setNotice("Đã cập nhật khóa học.");
      await data.reload();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function edit(row) {
    setError("");
    try {
      const detail = await api.get(`/instructor/courses/${row.id}`, token);
      if (!Array.isArray(detail.categorySlugs))
        throw new Error(
          "Chưa tải được danh mục của khóa học. Vui lòng tải lại trang rồi thử lại.",
        );
      setForm({ ...initial, ...detail });
      setEditing(row.id);
      setOpen(true);
    } catch (e) {
      setError(e.message);
    }
  }
  async function action() {
    setBusy(true);
    try {
      const id = confirmation.row.id,
        action = confirmation.action;
      if (action === "trash") await api.del(`/instructor/courses/${id}`, token);
      else if (action === "purge")
        await api.del(`/instructor/courses/${id}/permanent`, token);
      else
        await api.post(`/instructor/courses/${id}/${action}`, undefined, token);
      setNotice(
        action === "restore"
          ? "Đã khôi phục về bản nháp."
          : action === "trash"
            ? "Đã chuyển vào thùng rác."
            : action === "purge"
              ? "Đã xóa vĩnh viễn khóa chưa có lịch sử."
              : action === "archive"
                ? "Đã lưu trữ. Xem trong tab Lưu trữ."
                : "Đã xuất bản khóa học.",
      );
      setConfirmation(null);
      await data.reload();
    } catch (e) {
      setError(e.message);
      setConfirmation(null);
    } finally {
      setBusy(false);
    }
  }
  if (data.loading) return <Loading />;
  if (data.error) return <ErrorState error={data.error} retry={data.reload} />;
  const all = data.data || [];
  const matches = (row, key) =>
    key === "trash"
      ? Boolean(row.deletedAt)
      : key === "archived"
        ? !row.deletedAt && row.status === "ARCHIVED"
        : !row.deletedAt && row.status !== "ARCHIVED";
  const rows = all.filter(
    (row) =>
      matches(row, section) &&
      row.title.toLowerCase().includes(query.toLowerCase()),
  );
  const actionNames = {
    publish: "Xuất bản",
    archive: "Lưu trữ",
    trash: "Chuyển vào thùng rác",
    restore: "Khôi phục về bản nháp",
    purge: "Xóa vĩnh viễn",
  };
  return (
    <Shell
      title={
        user.roles.includes("ADMIN") ? "Quản lý khóa học" : "Khóa học của tôi"
      }
      subtitle="Bước 1: thông tin khóa học → Bước 2: lớp, giảng viên, học phí → Bước 3: chọn ngày và giờ học."
      actions={
        <Button
          onClick={() => {
            setForm(initial);
            setEditing(null);
            setError("");
            setOpen(true);
          }}
        >
          <Plus size={16} />
          Thêm khóa học
        </Button>
      }
    >
      <ActionNotice onDismiss={() => setNotice("")}>{notice}</ActionNotice>
      {error && !open && <div className="alert">{error}</div>}
      <div className="filters">
        <Search size={18} />
        <input
          aria-label="Tìm khóa học"
          placeholder="Tìm theo tên khóa học"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
      </div>
      <Tabs
        value={section}
        onChange={setSection}
        items={[
          {
            value: "active",
            label: `Đang dùng (${all.filter((row) => matches(row, "active")).length})`,
          },
          {
            value: "archived",
            label: `Lưu trữ (${all.filter((row) => matches(row, "archived")).length})`,
          },
          {
            value: "trash",
            label: `Thùng rác (${all.filter((row) => matches(row, "trash")).length})`,
          },
        ]}
      />
      <p className="mb-4 text-xs text-slate-500">
        Lưu trữ ẩn khỏi danh mục. Thùng rác có thể khôi phục. Xóa vĩnh viễn chỉ
        cho khóa chưa có đơn hàng, ghi danh, đánh giá hoặc lịch sử thông báo.
      </p>
      <DataTable
        rows={rows}
        columns={[
          { key: "id", label: "Mã" },
          { key: "title", label: "Khóa học" },
          { key: "status", label: "Trạng thái" },
          {
            key: "actions",
            label: "Thao tác",
            render: (row) => (
              <div className="action-buttons">
                <Button variant="secondary" onClick={() => setViewing(row)}>
                  <Eye size={15} />
                  Xem
                </Button>
                {!row.deletedAt && row.status !== "ARCHIVED" && (
                  <>
                    <Link
                      className="btn secondary"
                      to={`${prefix}/offerings?courseId=${row.id}`}
                    >
                      Lớp & lịch
                    </Link>
                    <Button variant="secondary" onClick={() => edit(row)}>
                      <Pencil size={15} />
                      Sửa
                    </Button>
                    {row.status === "DRAFT" && (
                      <Button
                        onClick={() =>
                          setConfirmation({ row, action: "publish" })
                        }
                      >
                        <Check size={15} />
                        Xuất bản
                      </Button>
                    )}
                    <Button
                      variant="secondary"
                      onClick={() =>
                        setConfirmation({ row, action: "archive" })
                      }
                    >
                      <Archive size={15} />
                      Lưu trữ
                    </Button>
                  </>
                )}
                {row.status === "ARCHIVED" && (
                  <Button
                    variant="secondary"
                    onClick={() => setConfirmation({ row, action: "restore" })}
                  >
                    <RotateCcw size={15} />
                    Khôi phục
                  </Button>
                )}
                {!row.deletedAt ? (
                  <Button
                    variant="danger"
                    onClick={() => setConfirmation({ row, action: "trash" })}
                  >
                    <Trash2 size={15} />
                    Xóa mềm
                  </Button>
                ) : (
                  <Button
                    variant="danger"
                    onClick={() => setConfirmation({ row, action: "purge" })}
                  >
                    <Trash2 size={15} />
                    Xóa vĩnh viễn
                  </Button>
                )}
              </div>
            ),
          },
        ]}
      />
      <Modal
        title={editing ? "Sửa khóa học" : "Thêm khóa học"}
        open={open}
        onClose={() => {
          if (!busy && !imageBusy) setOpen(false);
        }}
      >
        <form className="form" onSubmit={save}>
          {form.status === "PUBLISHED" && (
            <p className="rounded-lg bg-blue-50 p-3 text-sm text-blue-900">
              Khóa đã xuất bản: bạn có thể cập nhật ảnh, tên và mô tả. Liên kết
              và danh mục được giữ nguyên.
            </p>
          )}
          {error && <div className="alert">{error}</div>}
          <label>
            Tên khóa học
            <input
              required
              maxLength={255}
              value={form.title}
              onChange={(e) => {
                const title = e.target.value;
                setForm((previous) => ({
                  ...previous,
                  title,
                  ...(!editing
                    ? {
                        slug: title
                          .normalize("NFD")
                          .replace(/[\u0300-\u036f]/g, "")
                          .replace(/đ/g, "d")
                          .replace(/Đ/g, "D")
                          .toLowerCase()
                          .replace(/[^a-z0-9]+/g, "-")
                          .replace(/^-|-$/g, ""),
                      }
                    : {}),
                }));
              }}
            />
          </label>
          <label>
            Đường dẫn tự tạo (có thể sửa)
            <input
              required
              pattern="[a-z0-9]+(?:-[a-z0-9]+)*"
              maxLength={255}
              disabled={form.status === "PUBLISHED"}
              value={form.slug}
              onChange={(e) => field("slug", e.target.value)}
            />
          </label>
          <label>
            Mô tả
            <textarea
              maxLength={10000}
              value={form.description || ""}
              onChange={(e) => field("description", e.target.value)}
            />
          </label>
          <ImagePicker
            value={form.thumbnailUrl}
            onChange={(value) => field("thumbnailUrl", value)}
            onBusyChange={setImageBusy}
          />
          <fieldset disabled={form.status === "PUBLISHED"}>
            <legend className="mb-2 font-medium">Danh mục</legend>
            {categories.error && <ErrorState error={categories.error} />}
            <div className="flex flex-wrap gap-3">
              {(categories.data || []).map((item) => (
                <label key={item.slug} className="!flex items-center">
                  <input
                    type="checkbox"
                    checked={form.categorySlugs.includes(item.slug)}
                    onChange={(e) =>
                      field(
                        "categorySlugs",
                        e.target.checked
                          ? [...form.categorySlugs, item.slug]
                          : form.categorySlugs.filter(
                              (value) => value !== item.slug,
                            ),
                      )
                    }
                  />
                  {item.name}
                </label>
              ))}
            </div>
          </fieldset>
          <label>
            Thẻ (cách nhau bằng dấu phẩy)
            <input
              disabled={form.status === "PUBLISHED"}
              value={form.tags.join(",")}
              onChange={(e) => field("tags", e.target.value.split(","))}
            />
          </label>
          <Button busy={busy || imageBusy} type="submit">
            Lưu khóa học
          </Button>
        </form>
      </Modal>
      <Modal
        title="Thông tin khóa học"
        open={Boolean(viewing)}
        onClose={() => setViewing(null)}
      >
        {viewing && (
          <div className="grid gap-4">
            <CourseImage
              className="max-h-56 w-full rounded-lg object-contain"
              src={viewing.thumbnailUrl}
              alt={viewing.title}
            />
            <h2>{viewing.title}</h2>
            <p className="whitespace-pre-line text-sm text-slate-500">
              {viewing.description}
            </p>
            <Link
              className="btn secondary"
              to={`${prefix}/offerings?courseId=${viewing.id}`}
            >
              Xem các lớp và lịch học
            </Link>
          </div>
        )}
      </Modal>
      <ConfirmDialog
        open={Boolean(confirmation)}
        onClose={() => setConfirmation(null)}
        onConfirm={action}
        busy={busy}
      >
        {actionNames[confirmation?.action]} khóa “{confirmation?.row.title}”?{" "}
        {confirmation?.action === "purge"
          ? "Thao tác này không khôi phục được. Khóa có lịch sử sẽ bị hệ thống từ chối xóa."
          : confirmation?.action === "trash"
            ? "Lớp đã ghi danh và lịch sử vẫn được giữ; có thể khôi phục từ Thùng rác."
            : ""}
      </ConfirmDialog>
    </Shell>
  );
}
