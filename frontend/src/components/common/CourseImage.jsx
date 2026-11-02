import { useEffect, useState } from "react";
import { ImageOff } from "lucide-react";
import { mediaUrl } from "../../api/client";
export default function CourseImage({ src, alt, className = "", ...props }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [src]);
  return failed || !src ? (
    <div
      className={`${className} flex min-h-32 w-full flex-col items-center justify-center gap-2 rounded-lg bg-slate-100 text-slate-400`}
      role="img"
      aria-label={`Ảnh ${alt} đang cập nhật`}
    >
      <ImageOff size={30} />
      <span className="text-xs font-normal">Ảnh đang cập nhật</span>
    </div>
  ) : (
    <img
      {...props}
      className={className}
      src={mediaUrl(src)}
      alt={alt}
      onError={() => setFailed(true)}
    />
  );
}
