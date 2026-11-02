import { createContext, useContext, useEffect, useState } from "react";
import { api } from "../api/client";
import { useAuth } from "./AuthContext";
const Context = createContext(null);
export function WishlistProvider({ children }) {
  const { token } = useAuth();
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [pending, setPending] = useState([]);
  useEffect(() => {
    let active = true;
    setRows([]);
    setError("");
    setPending([]);
    if (!token) return;
    setLoading(true);
    api
      .get("/wishlists", token)
      .then((value) => {
        if (active) setRows(value);
      })
      .catch((e) => {
        if (active) setError(e.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [token]);
  async function toggle(course) {
    if (!token || pending.includes(course.courseId)) return;
    const saved = rows.some((row) => row.courseId === course.courseId);
    setPending((value) => [...value, course.courseId]);
    try {
      if (saved) await api.del(`/wishlists/courses/${course.courseId}`, token);
      else
        await api.post(
          `/wishlists/courses/${course.courseId}`,
          undefined,
          token,
        );
      setRows(await api.get("/wishlists", token));
      setError("");
    } finally {
      setPending((value) => value.filter((id) => id !== course.courseId));
    }
  }
  return (
    <Context.Provider value={{ rows, loading, error, pending, toggle }}>
      {children}
    </Context.Provider>
  );
}
export const useWishlist = () => useContext(Context);
