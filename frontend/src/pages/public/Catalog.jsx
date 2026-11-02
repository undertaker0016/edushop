import { Filter } from "lucide-react";
import CourseCard from "../../components/common/CourseCard";
import WishlistButton from "../../components/common/WishlistButton";
import { useEffect, useState, useRef } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../../api/client";
import { Empty, ErrorState, Loading } from "../../components/common/State";

function CourseGrid({ rows }) {
  return rows.length ? (
    <div className="course-grid">
      {rows.map((course) => (
        <CourseCard key={course.courseId} course={course} />
      ))}
    </div>
  ) : (
    <Empty>Chưa có khóa học phù hợp.</Empty>
  );
}

export default function Catalog() {
  const [params, setParams] = useSearchParams();
  const [data, setData] = useState(null);
  const [categories, setCategories] = useState([]);
  const [tags, setTags] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const page = Number(params.get("page") || 0);
  const query = params.get("q") || "";
  const category = params.get("category") || "";
  const tag = params.get("tag") || "";
  const sort = params.get("sort") || "newest",
    minPrice = params.get("minPrice") || "",
    maxPrice = params.get("maxPrice") || "",
    minRating = params.get("minRating") || "";
  const [filtersOpen, setFiltersOpen] = useState(
    () => window.matchMedia("(min-width: 768px)").matches,
  );
  useEffect(() => {
    const media = window.matchMedia("(min-width: 768px)");
    const change = () => setFiltersOpen(media.matches);
    media.addEventListener("change", change);
    return () => media.removeEventListener("change", change);
  }, []);
  const requestVersion = useRef(0);
  async function load() {
    const current = ++requestVersion.current;
    setLoading(true);
    try {
      const [courses, categoryRows, tagRows] = await Promise.all([
        api.get(
          `/courses?page=${page}&size=12&q=${encodeURIComponent(query)}&category=${encodeURIComponent(category)}&tag=${encodeURIComponent(tag)}&sort=${sort}${minPrice ? `&minPrice=${minPrice}` : ""}${maxPrice ? `&maxPrice=${maxPrice}` : ""}${minRating ? `&minRating=${minRating}` : ""}`,
        ),
        api.get("/categories"),
        api.get("/tags"),
      ]);
      if (current !== requestVersion.current) return;
      setData(courses);
      setCategories(categoryRows);
      setTags(tagRows);
      setError(null);
    } catch (caught) {
      if (current === requestVersion.current) setError(caught);
    } finally {
      if (current === requestVersion.current) setLoading(false);
    }
  }
  useEffect(() => {
    load();
    return () => {
      requestVersion.current++;
    };
  }, [page, query, category, tag, sort, minPrice, maxPrice, minRating]);
  function update(key, value) {
    const next = new URLSearchParams(params);
    value ? next.set(key, value) : next.delete(key);
    if (key !== "page") next.delete("page");
    setParams(next);
  }
  return (
    <main className="page">
      <header className="page-heading">
        <div>
          <span className="eyebrow">Khám phá</span>
          <h1>Danh mục khóa học</h1>
          <p>Tìm khóa học theo từ khóa, danh mục hoặc thẻ.</p>
        </div>
      </header>
      <details
        className="mb-5 rounded-xl border border-slate-200 bg-white"
        open={filtersOpen}
        onToggle={(event) => setFiltersOpen(event.currentTarget.open)}
      >
        <summary className="flex min-h-11 cursor-pointer items-center gap-2 px-4 py-3 text-sm font-medium md:hidden">
          <Filter size={16} />
          Bộ lọc và sắp xếp
          {(category || tag || maxPrice || minRating) && (
            <span className="text-xs text-blue-600">Đang áp dụng</span>
          )}
        </summary>
        <div className="filters !mb-0 !border-0 !shadow-none">
          <label className="grid gap-1 text-xs">
            Sắp xếp
            <select
              aria-label="Sắp xếp khóa học"
              value={sort}
              onChange={(e) => update("sort", e.target.value)}
            >
              <option value="newest">Mới nhất</option>
              <option value="price_asc">Giá thấp → cao</option>
              <option value="price_desc">Giá cao → thấp</option>
              <option value="rating_desc">Đánh giá cao nhất</option>
              <option value="students_desc">Nhiều học viên nhất</option>
              <option value="title_asc">Tên A–Z</option>
            </select>
          </label>
          <label className="grid gap-1 text-xs">
            Mức học phí
            <select
              value={maxPrice}
              onChange={(e) => {
                const next = new URLSearchParams(params);
                e.target.value
                  ? next.set("maxPrice", e.target.value)
                  : next.delete("maxPrice");
                next.delete("page");
                setParams(next);
              }}
            >
              <option value="">Tất cả mức giá</option>
              <option value="0">Miễn phí</option>
              <option value="300000">Đến 300.000đ</option>
              <option value="500000">Đến 500.000đ</option>
              <option value="1000000">Đến 1.000.000đ</option>
            </select>
          </label>
          <label className="grid gap-1 text-xs">
            Đánh giá
            <select
              value={minRating}
              onChange={(e) => update("minRating", e.target.value)}
            >
              <option value="">Tất cả đánh giá</option>
              <option value="4">Từ 4 sao</option>
              <option value="3">Từ 3 sao</option>
            </select>
          </label>
          <select
            aria-label="Lọc theo thẻ"
            value={tag}
            onChange={(event) => update("tag", event.target.value)}
          >
            <option value="">Tất cả thẻ</option>
            {tags.map((item) => (
              <option key={item.name} value={item.name}>
                {item.name}
              </option>
            ))}
          </select>
          <button className="btn secondary" onClick={() => setParams({})}>
            Xóa bộ lọc
          </button>
          <input
            defaultValue={query}
            key={query}
            onKeyDown={(event) =>
              event.key === "Enter" && update("q", event.currentTarget.value)
            }
            aria-label="Tìm khóa học theo từ khóa"
            placeholder="Tìm khóa học…"
          />
          <select
            aria-label="Lọc danh mục"
            value={category}
            onChange={(event) => update("category", event.target.value)}
          >
            <option value="">Tất cả danh mục</option>
            {categories.map((item) => (
              <option key={item.slug} value={item.slug}>
                {item.name}
              </option>
            ))}
          </select>
          <button
            className="btn secondary"
            onClick={() =>
              update("q", document.querySelector(".filters input").value)
            }
          >
            Tìm kiếm
          </button>
        </div>
      </details>
      {loading ? (
        <Loading />
      ) : error ? (
        <ErrorState error={error} retry={load} />
      ) : !data?.items?.length ? (
        <Empty>Không tìm thấy khóa học phù hợp.</Empty>
      ) : (
        <>
          <CourseGrid rows={data.items} />
          <div className="pagination">
            <button
              className="btn secondary"
              disabled={page <= 0}
              onClick={() => update("page", page - 1)}
            >
              Trước
            </button>
            <span>
              Trang {page + 1}/{Math.max(1, data.totalPages)}
            </span>
            <button
              className="btn secondary"
              disabled={page + 1 >= data.totalPages}
              onClick={() => update("page", page + 1)}
            >
              Sau
            </button>
          </div>
        </>
      )}
    </main>
  );
}
