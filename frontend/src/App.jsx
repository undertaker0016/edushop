import ShoppingHub from "./pages/buyer/ShoppingHub";
import PaymentReturn from "./pages/buyer/PaymentReturn";
import InstructorContact from "./pages/public/InstructorContact";
import InstructorApplications from "./pages/admin/InstructorApplications";
import { Route, Routes } from "react-router-dom";

import PublicLayout from "./layouts/PublicLayout";
import BuyerLayout from "./layouts/BuyerLayout";
import InstructorLayout from "./layouts/InstructorLayout";
import AdminLayout from "./layouts/AdminLayout";

import Home from "./pages/public/Home";
import Catalog from "./pages/public/Catalog";
import CourseDetail from "./pages/public/CourseDetail";
import NotFound from "./pages/public/NotFound";
import Forbidden from "./pages/public/Forbidden";
import ForgotPassword from "./pages/auth/ForgotPassword";
import Login from "./pages/auth/Login";
import Register from "./pages/auth/Register";
import Account from "./pages/auth/Account";

import Cart from "./pages/buyer/Cart";
import BuyerHome from "./pages/buyer/BuyerHome";
import Checkout from "./pages/buyer/Checkout";
import OrderDetail from "./pages/buyer/OrderDetail";
import Orders from "./pages/buyer/Orders";
import Classroom from "./pages/buyer/Classroom";
import Wishlist from "./pages/buyer/Wishlist";
import Notifications from "./pages/buyer/Notifications";
import BuyerReviews from "./pages/buyer/Reviews";
import Review from "./pages/buyer/Review";

import InstructorEnrollments from "./pages/instructor/Enrollments";
import InstructorNotifications from "./pages/instructor/Notifications";
import InstructorHome from "./pages/instructor/InstructorHome";
import InstructorProfile from "./pages/instructor/InstructorProfile";
import InstructorCourses from "./pages/instructor/InstructorCourses";
import InstructorOfferings from "./pages/instructor/InstructorOfferings";
import InstructorSessions from "./pages/instructor/InstructorSessions";

import AdminDashboard from "./pages/admin/AdminDashboard";
import Users from "./pages/admin/Users";
import InstructorManagement from "./pages/admin/InstructorManagement";
import Categories from "./pages/admin/Categories";
import Tags from "./pages/admin/Tags";
import Courses from "./pages/admin/Courses";
import Offerings from "./pages/admin/Offerings";
import Sessions from "./pages/admin/Sessions";
import CouponManagement from "./pages/admin/CouponManagement";
import AdminOrders from "./pages/admin/Orders";
import Payments from "./pages/admin/Payments";
import Reconciliation from "./pages/admin/Reconciliation";
import Enrollments from "./pages/admin/Enrollments";
import Reviews from "./pages/admin/Reviews";
import AdminNotifications from "./pages/admin/Notifications";

export default function App() {
  return (
    <Routes>
      <Route element={<PublicLayout />}>
        <Route index element={<Home />} />
        <Route path="courses" element={<Catalog />} />
        <Route path="contact" element={<InstructorContact />} />
        <Route path="courses/:slug" element={<CourseDetail />} />
        <Route path="auth/login" element={<Login />} />
        <Route path="payment/momo-return" element={<PaymentReturn />} />
        <Route path="payment/zalopay-return" element={<PaymentReturn />} />
        <Route path="payment/return" element={<PaymentReturn />} />
        <Route path="auth/forgot-password" element={<ForgotPassword />} />
        <Route path="auth/register" element={<Register />} />
        <Route path="403" element={<Forbidden />} />
        <Route path="401" element={<Login />} />
        <Route path="404" element={<NotFound />} />
        <Route path="*" element={<NotFound />} />
      </Route>

      <Route path="buyer" element={<BuyerLayout />}>
        <Route index element={<BuyerHome />} />
        <Route path="account" element={<Account />} />
        <Route path="checkout" element={<Checkout />} />
        <Route path="orders" element={<ShoppingHub />} />
        <Route path="orders/:id" element={<OrderDetail />} />
        <Route path="cart" element={<ShoppingHub />} />
        <Route path="classroom" element={<Classroom />} />
        <Route path="wishlist" element={<Wishlist />} />
        <Route path="notifications" element={<Notifications />} />
        <Route path="reviews" element={<BuyerReviews />} />
        <Route path="reviews/:courseId" element={<Review />} />
      </Route>

      <Route path="instructor" element={<InstructorLayout />}>
        <Route index element={<InstructorHome />} />
        <Route path="profile" element={<InstructorProfile />} />
        <Route path="courses" element={<InstructorCourses />} />
        <Route path="offerings" element={<InstructorOfferings />} />
        <Route path="sessions" element={<InstructorSessions />} />
        <Route path="enrollments" element={<InstructorEnrollments />} />
        <Route path="notifications" element={<InstructorNotifications />} />
      </Route>

      <Route path="admin" element={<AdminLayout />}>
        <Route index element={<AdminDashboard />} />
        <Route path="users" element={<Users />} />
        <Route path="instructors" element={<InstructorManagement />} />
        <Route
          path="instructor-applications"
          element={<InstructorApplications />}
        />
        <Route path="categories" element={<Categories />} />
        <Route path="tags" element={<Tags />} />
        <Route path="courses" element={<Courses />} />
        <Route path="courses/manage" element={<InstructorCourses />} />
        <Route path="offerings" element={<Offerings />} />
        <Route path="offerings/manage" element={<InstructorOfferings />} />
        <Route path="sessions" element={<Sessions />} />
        <Route path="sessions/manage" element={<InstructorSessions />} />
        <Route path="coupons" element={<CouponManagement />} />
        <Route path="orders" element={<AdminOrders />} />
        <Route path="payments" element={<Payments />} />
        <Route path="reconciliation" element={<Reconciliation />} />
        <Route path="enrollments" element={<Enrollments />} />
        <Route path="reviews" element={<Reviews />} />
        <Route path="notifications" element={<AdminNotifications />} />
      </Route>
    </Routes>
  );
}
