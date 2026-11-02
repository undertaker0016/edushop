-- EduShop V3: prepare reference roles, order reservations, and coupon usage.
-- Existing V1/V2 migrations remain unchanged.

-- Roles are reference data. This does not create any user or ADMIN account.
INSERT INTO roles (name, description)
SELECT 'STUDENT', 'Learner account'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'STUDENT');

INSERT INTO roles (name, description)
SELECT 'INSTRUCTOR', 'Instructor account'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'INSTRUCTOR');

INSERT INTO roles (name, description)
SELECT 'ADMIN', 'Administrator account'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ADMIN');

-- NULL means the default registration deadline is derived by application policy.
ALTER TABLE course_offerings
    ADD COLUMN registration_closes_at DATETIME(6) NULL
        AFTER end_date,
    ADD CONSTRAINT chk_offerings_currency_vnd
        CHECK (currency = 'VND');

-- Existing orders may predate idempotency and reservation expiry. Keep the new
-- fields nullable for those rows; the checkout service must populate them for
-- every new order. The unique key scopes an idempotency key to its user.
ALTER TABLE orders
    ADD COLUMN expires_at DATETIME(6) NULL
        AFTER paid_at,
    ADD COLUMN idempotency_key VARCHAR(100)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN request_fingerprint CHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD CONSTRAINT uk_orders_user_idempotency
        UNIQUE (user_id, idempotency_key),
    ADD CONSTRAINT chk_orders_idempotency_pair
        CHECK (
            (idempotency_key IS NULL AND request_fingerprint IS NULL)
            OR
            (idempotency_key IS NOT NULL AND request_fingerprint IS NOT NULL)
        ),
    ADD CONSTRAINT chk_orders_currency_vnd
        CHECK (currency = 'VND');

-- FIXED coupon discounts are denominated in VND. Percent discounts are
-- percentages, while a NULL cap means no fixed maximum.
-- A NULL per-user limit means unlimited uses by that user; default is one use.
ALTER TABLE coupons
    ADD COLUMN max_discount_amount DECIMAL(12,2) NULL
        AFTER discount_value,
    ADD COLUMN max_uses_per_user INT UNSIGNED NULL DEFAULT 1
        AFTER max_uses,
    ADD CONSTRAINT chk_coupons_percent_value_range
        CHECK (discount_type <> 'PERCENT' OR discount_value <= 100),
    ADD CONSTRAINT chk_coupons_max_discount_positive
        CHECK (max_discount_amount IS NULL OR max_discount_amount > 0),
    ADD CONSTRAINT chk_coupons_per_user_limit_positive
        CHECK (max_uses_per_user IS NULL OR max_uses_per_user > 0),
    ADD CONSTRAINT chk_coupons_usage_limit
        CHECK (max_uses IS NULL OR used_count <= max_uses);

-- One row records one order's coupon reservation/use/release lifecycle.
-- The service serializes coupon claims on the coupon row and changes this
-- status together with used_count in the same transaction.
CREATE TABLE coupon_redemptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    coupon_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    status ENUM('RESERVED', 'CONSUMED', 'RELEASED')
        NOT NULL DEFAULT 'RESERVED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_coupon_redemptions_order UNIQUE (order_id),
    CONSTRAINT fk_coupon_redemptions_coupon
        FOREIGN KEY (coupon_id) REFERENCES coupons(id),
    CONSTRAINT fk_coupon_redemptions_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_coupon_redemptions_order
        FOREIGN KEY (order_id) REFERENCES orders(id),
    INDEX idx_coupon_redemptions_user_coupon_status
        (user_id, coupon_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE payments
    ADD CONSTRAINT chk_payments_currency_vnd
        CHECK (currency = 'VND');

-- MVP allows one offering per order and one enrollment per purchased item.
ALTER TABLE order_items
    ADD CONSTRAINT uk_order_items_order_offering
        UNIQUE (order_id, offering_id);

ALTER TABLE enrollments
    ADD CONSTRAINT uk_enrollments_order_item
        UNIQUE (order_item_id);
