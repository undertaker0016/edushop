# RBAC Matrix

| API | Anonymous | Buyer | Instructor | Admin | Ownership |
|---|---:|---:|---:|---:|---|
| Public catalog | Có | Có | Có | Có | Không |
| Register/login | Có | Có | Có | Có | Không |
| Auth me/profile/password | Không | Có | Có | Có | Chính mình |
| Checkout/orders/payments | Không | Có | Có | Có | Order của chính mình |
| Enrollment/classroom | Không | Có | Có | Có | Enrollment của chính mình |
| Instructor course/offering/session | Không | Không | Có | Có | Course do instructor quản lý |
| Admin APIs | Không | Không | Không | Có | Hệ thống |
| Public reviews | Có | Có | Có | Có | Không |
| Write review/wishlist/notification | Không | Có | Có | Có | Chính mình |
| Payment webhook | Provider signature | Provider signature | Provider signature | Provider signature | Provider/order match |

Owner mismatch trả 404 để tránh lộ dữ liệu; thiếu authentication trả 401; thiếu role trả 403.

