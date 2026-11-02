import { useState } from "react";
import { useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import useApi from "../../hooks/useApi";
import { api } from "../../api/client";
import { useAuth } from "../../contexts/AuthContext";
import Shell from "../../components/common/Shell";
import DataTable from "../../components/tables/DataTable";
import { Loading, ErrorState } from "../../components/common/State";
import { Button, Modal } from "../../components/ui";
import { date } from "../../utils/format";
export default function InstructorApplications() {
  const data = useApi("/admin/instructor-applications", true);
  const [params, setParams] = useSearchParams();
  const { token } = useAuth();
  const [selected, setSelected] = useState(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  useEffect(() => {
    const id = Number(params.get("applicationId"));
    if (id && data.data) {
      const match = data.data.find((row) => row.id === id);
      if (match) setSelected(match);
    }
  }, [params, data.data]);
  function close() {
    setSelected(null);
    const next = new URLSearchParams(params);
    next.delete("applicationId");
    setParams(next, { replace: true });
  }
  async function reviewed() {
    setBusy(true);
    try {
      await api.patch(
        `/admin/instructor-applications/${selected.id}/reviewed`,
        undefined,
        token,
      );
      close();
      await data.reload();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  async function approve() {
    setBusy(true);
    setError("");
    try {
      const result = await api.patch(
        `/admin/instructor-applications/${selected.id}/approve`,
        undefined,
        token,
      );
      setError("");
      close();
      await data.reload();
      window.dispatchEvent(new Event("edushop:notifications-changed"));
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }
  if (data.loading) return <Loading />;
  if (data.error) return <ErrorState error={data.error} retry={data.reload} />;
  return (
    <Shell
      title="Liên hệ đăng ký giảng viên"
      subtitle="Mở hồ sơ, xem chuyên môn và nội dung liên hệ; duyệt tại đây sẽ cấp quyền giảng viên và tạo hồ sơ giảng viên. Không có thanh toán."
    >
      {error && <div className="alert">{error}</div>}
      <DataTable
        rows={data.data || []}
        columns={[
          { key: "fullName", label: "Người đăng ký" },
          { key: "email", label: "Email" },
          { key: "expertise", label: "Chuyên môn" },
          {
            key: "status",
            label: "Trạng thái",
            render: (row) => (
              <span className="badge">
                {row.status === "PENDING"
                  ? "Chờ duyệt"
                  : row.status === "REVIEWED"
                    ? "Đã xem · Chờ quyết định"
                    : row.status === "APPROVED"
                      ? "Đã cấp quyền"
                      : "Đã đóng"}
              </span>
            ),
          },
          {
            key: "createdAt",
            label: "Ngày gửi",
            render: (row) => date(row.createdAt),
          },
          {
            key: "actions",
            label: "Thao tác",
            render: (row) => (
              <Button
                variant="secondary"
                onClick={() => {
                  setSelected(row);
                  const next = new URLSearchParams(params);
                  next.set("applicationId", row.id);
                  setParams(next, { replace: true });
                }}
              >
                Xem hồ sơ
              </Button>
            ),
          },
        ]}
      />
      <Modal title="Hồ sơ giảng viên" open={Boolean(selected)} onClose={close}>
        {selected && (
          <div className="grid gap-3">
            <h2>{selected.fullName}</h2>
            <p>
              {selected.email} · {selected.phone}
            </p>
            <strong>{selected.expertise}</strong>
            <p className="whitespace-pre-wrap">{selected.message}</p>
            <p className="text-sm">
              Trạng thái:{" "}
              {selected.status === "PENDING"
                ? "Chờ duyệt"
                : selected.status === "APPROVED"
                  ? "Đã cấp quyền giảng viên"
                  : "Đã xem, chờ quyết định"}
              . Đây là hồ sơ liên hệ, không có khoản thanh toán.
            </p>
            {selected.status !== "APPROVED" && (
              <div className="flex flex-wrap gap-2">
                <Button busy={busy} onClick={approve}>
                  Duyệt và cấp quyền giảng viên
                </Button>
                {selected.status === "PENDING" && (
                  <Button variant="secondary" busy={busy} onClick={reviewed}>
                    Đánh dấu đã xem
                  </Button>
                )}
              </div>
            )}
          </div>
        )}
      </Modal>
    </Shell>
  );
}
