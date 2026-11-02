import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";
import PublicLayout from "./PublicLayout";
import { Loading } from "../components/common/State";

export default function BuyerLayout() {
  const { user, loading } = useAuth();
  if (loading) return <Loading />;
  if (!user) return <Navigate to="/auth/login" replace />;
  return (
    <PublicLayout>
      <Outlet />
    </PublicLayout>
  );
}
