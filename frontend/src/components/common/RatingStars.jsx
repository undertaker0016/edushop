import { Star } from "lucide-react";
export default function RatingStars({ value = 0, count = 0, onChange }) {
  if (onChange)
    return (
      <div role="radiogroup" aria-label="Chọn số sao" className="flex gap-1">
        {[1, 2, 3, 4, 5].map((n) => (
          <button
            type="button"
            role="radio"
            aria-checked={Number(value) === n}
            key={n}
            aria-label={`${n} sao`}
            className="min-h-11 min-w-11 rounded-lg p-2 text-amber-500 focus-visible:ring-2"
            onKeyDown={(e) => {
              if (e.key === "ArrowRight" || e.key === "ArrowLeft") {
                e.preventDefault();
                onChange(
                  Math.min(
                    5,
                    Math.max(
                      1,
                      Number(value) + (e.key === "ArrowRight" ? 1 : -1),
                    ),
                  ),
                );
              }
            }}
            onClick={() => onChange(n)}
          >
            <Star
              fill={n <= Number(value) ? "currentColor" : "none"}
              size={25}
            />
          </button>
        ))}
      </div>
    );
  return (
    <span className="inline-flex items-center gap-1 text-xs text-slate-500">
      <span className="flex text-amber-500" aria-hidden>
        {[1, 2, 3, 4, 5].map((n) => (
          <Star
            key={n}
            size={13}
            fill={n <= Math.round(Number(value)) ? "currentColor" : "none"}
          />
        ))}
      </span>
      <span>
        {Number(count) > 0
          ? `${Number(value).toFixed(1)} (${count} đánh giá)`
          : "Chưa có đánh giá"}
      </span>
    </span>
  );
}
