import { createContext, useContext, useEffect, useState } from "react";
import { api } from "../api/client";
import { useAuth } from "./AuthContext";
const Context = createContext(null);
function read(key) {
  try {
    const value = JSON.parse(localStorage.getItem(key) || "[]");
    return Array.isArray(value)
      ? value.filter((row) => row && Number(row.id) > 0)
      : [];
  } catch {
    return [];
  }
}
export function CartProvider({ children }) {
  const { user } = useAuth();
  const key = `edushop.cart.${user?.id || "guest"}`;
  const [entries, setEntries] = useState(() => read(key)),
    [rows, setRows] = useState([]),
    [loading, setLoading] = useState(false);
  useEffect(() => {
    const sync = () => setEntries(read(key));
    sync();
    window.addEventListener("storage", sync);
    window.addEventListener("edushop:cart", sync);
    return () => {
      window.removeEventListener("storage", sync);
      window.removeEventListener("edushop:cart", sync);
    };
  }, [key]);
  useEffect(() => {
    let active = true;
    setLoading(true);
    Promise.all(
      entries.map(async (entry) => {
        try {
          const detail = await api.get(`/offerings/${entry.id}`);
          return {
            ...entry,
            ...detail.offering,
            course: detail.course,
            available: true,
          };
        } catch {
          return { ...entry, available: false };
        }
      }),
    )
      .then((value) => {
        if (active) setRows(value);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [entries, key]);
  function save(next) {
    localStorage.setItem(key, JSON.stringify(next));
    setEntries(next);
    window.dispatchEvent(new Event("edushop:cart"));
  }
  function add(course, offering) {
    const existing = read(key);
    if (existing.some((row) => row.id === offering.id)) return false;
    save([
      ...existing,
      {
        id: offering.id,
        title: course.title,
        slug: course.slug,
        thumbnailUrl: course.thumbnailUrl,
        cohortName: offering.cohortName,
        price: offering.price,
      },
    ]);
    return true;
  }
  function remove(id) {
    save(read(key).filter((row) => row.id !== id));
  }
  return (
    <Context.Provider
      value={{ count: entries.length, rows, loading, add, remove }}
    >
      {children}
    </Context.Provider>
  );
}
export const useCart = () => useContext(Context);
