import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "../api/client";
import { useAuth } from "../contexts/AuthContext";
export default function useApi(path, authenticated = false) {
  const { token } = useAuth();
  const [data, setData] = useState(null),
    [error, setError] = useState(null),
    [loading, setLoading] = useState(Boolean(path));
  const version = useRef(0);
  const reload = useCallback(async () => {
    const current = ++version.current;
    if (!path) {
      setData(null);
      setError(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const value = await api.get(path, authenticated ? token : undefined);
      if (current === version.current) setData(value);
    } catch (e) {
      if (current === version.current) setError(e);
    } finally {
      if (current === version.current) setLoading(false);
    }
  }, [path, authenticated, token]);
  useEffect(() => {
    setData(null);
    reload();
    return () => {
      version.current++;
    };
  }, [reload]);
  return { data, error, loading, reload };
}
