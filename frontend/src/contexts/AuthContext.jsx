import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { api } from "../api/client";

const Context = createContext(null);
const TOKEN_KEY = "edushop.accessToken";

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem(TOKEN_KEY));
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(Boolean(token));

  useEffect(() => {
    let active = true;
    if (!token) {
      setLoading(false);
      return;
    }
    setLoading(true);
    api
      .get("/auth/me", token)
      .then((profile) => {
        if (active) setUser(profile);
      })
      .catch(() => {
        if (!active) return;
        localStorage.removeItem(TOKEN_KEY);
        setToken(null);
        setUser(null);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [token]);

  async function login(email, password) {
    const auth = await api.post("/auth/login", { email, password });
    const profile = await api.get("/auth/me", auth.accessToken);
    localStorage.setItem(TOKEN_KEY, auth.accessToken);
    setUser(profile);
    setToken(auth.accessToken);
    return profile;
  }

  function logout() {
    localStorage.removeItem(TOKEN_KEY);
    setToken(null);
    setUser(null);
  }

  useEffect(() => {
    window.addEventListener("edushop:unauthorized", logout);
    return () => window.removeEventListener("edushop:unauthorized", logout);
  }, []);

  const value = useMemo(
    () => ({
      user,
      token,
      loading,
      login,
      logout,
      refresh: () => api.get("/auth/me", token).then(setUser),
    }),
    [user, token, loading],
  );

  return <Context.Provider value={value}>{children}</Context.Provider>;
}

export const useAuth = () => useContext(Context);
