import { StatusBadge } from "../ui";
import { label } from "../../utils/labels";
import { Empty } from "../common/State";

export default function DataTable({
  columns,
  rows,
  empty = "Chưa có dữ liệu.",
}) {
  if (!rows?.length) return <Empty>{empty}</Empty>;
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key}>{column.label}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={row.id ?? row.slug ?? row.code ?? index}>
              {columns.map((column) => (
                <td key={column.key}>
                  {column.render ? (
                    column.render(row)
                  ) : column.key === "status" ? (
                    <StatusBadge value={row[column.key]} />
                  ) : typeof row[column.key] === "string" ? (
                    label(row[column.key])
                  ) : (
                    (row[column.key] ?? "—")
                  )}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
