import { useEffect, useState } from "react";
import { mediaUrl } from "../../api/client";
export default function Avatar({ user, size = "size-8" }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [user?.avatarUrl]);
  return user?.avatarUrl && !failed ? (
    <img
      src={mediaUrl(user.avatarUrl)}
      alt={`Ảnh ${user.fullName}`}
      className={`${size} shrink-0 rounded-full object-cover`}
      onError={() => setFailed(true)}
    />
  ) : (
    <span
      aria-hidden
      className={`${size} inline-flex shrink-0 items-center justify-center rounded-full bg-blue-100 text-xs font-semibold text-blue-700`}
    >
      {user?.fullName?.trim()?.charAt(0)?.toUpperCase() || "U"}
    </span>
  );
}
