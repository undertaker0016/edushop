# EduShop API Reference

Base URL: http://localhost:8080/api/v1

Public: GET /categories, GET /tags, GET /courses, GET /courses/{slug}, GET /courses/{slug}/offerings, GET /reviews/courses/{slug}.

Authentication: POST /auth/register, POST /auth/login, GET/PUT /auth/me, PUT /auth/me/password.

Buyer: POST /orders/checkout with Idempotency-Key; GET/DELETE /orders; POST/GET /payments; GET /enrollments; GET /enrollments/{id}/sessions; wishlist; review; notifications.

Instructor: dashboard, courses, offerings, sessions and offering enrollments under /instructor.

Admin: dashboard, browse, users, instructors, categories, tags, coupons, orders, payments, enrollments and reviews under /admin.

Payment: DEV_SIMULATOR is available only in profile dev. MoMo uses POST /payments/webhooks/momo with provider-specific HMAC IPN and admin reconciliation.

Errors use RFC 7807 ProblemDetail. Common statuses: 400, 401, 403, 404, 409, 503.
