-- =============================================
-- EduShop V2: Business tables
-- Existing tables from V1:
-- users, roles, user_roles
-- MySQL 8+
-- =============================================

-- 1. Instructor profiles
CREATE TABLE instructors (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    bio TEXT NULL,
    avatar_url VARCHAR(500) NULL,
    contact_email VARCHAR(255) NULL,
    phone VARCHAR(20) NULL,
    zalo VARCHAR(40) NULL,
    facebook_url VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_instructors_user UNIQUE (user_id),
    CONSTRAINT fk_instructors_user
        FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- 2. Categories
CREATE TABLE categories (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) NOT NULL,
    icon VARCHAR(30) NULL,
    sort_order INT UNSIGNED NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uk_categories_slug UNIQUE (slug)
) ENGINE=InnoDB;

-- 3. Courses
CREATE TABLE courses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_by_user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    description TEXT NULL,
    thumbnail_url VARCHAR(500) NULL,
    status ENUM('DRAFT', 'PUBLISHED', 'ARCHIVED')
        NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_courses_slug UNIQUE (slug),
    CONSTRAINT fk_courses_creator
        FOREIGN KEY (created_by_user_id) REFERENCES users(id),
    INDEX idx_courses_status (status),
    INDEX idx_courses_creator (created_by_user_id)
) ENGINE=InnoDB;

-- 4. Course-category relationship
CREATE TABLE course_categories (
    course_id BIGINT NOT NULL,
    category_id INT UNSIGNED NOT NULL,
    PRIMARY KEY (course_id, category_id),
    CONSTRAINT fk_course_categories_course
        FOREIGN KEY (course_id) REFERENCES courses(id),
    CONSTRAINT fk_course_categories_category
        FOREIGN KEY (category_id) REFERENCES categories(id)
) ENGINE=InnoDB;

-- 5. Tags
CREATE TABLE tags (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tags_name UNIQUE (name)
) ENGINE=InnoDB;

-- 6. Course-tag relationship
CREATE TABLE course_tags (
    course_id BIGINT NOT NULL,
    tag_id INT UNSIGNED NOT NULL,
    PRIMARY KEY (course_id, tag_id),
    CONSTRAINT fk_course_tags_course
        FOREIGN KEY (course_id) REFERENCES courses(id),
    CONSTRAINT fk_course_tags_tag
        FOREIGN KEY (tag_id) REFERENCES tags(id)
) ENGINE=InnoDB;

-- 7. Course offerings: each scheduled class sold
CREATE TABLE course_offerings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    instructor_id BIGINT NOT NULL,
    offering_code VARCHAR(60) NOT NULL,
    cohort_name VARCHAR(120) NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    schedule_summary VARCHAR(255) NOT NULL,
    capacity SMALLINT UNSIGNED NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    status ENUM(
        'DRAFT', 'OPEN', 'CLOSED', 'COMPLETED', 'CANCELLED'
    ) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_offerings_code UNIQUE (offering_code),
    CONSTRAINT fk_offerings_course
        FOREIGN KEY (course_id) REFERENCES courses(id),
    CONSTRAINT fk_offerings_instructor
        FOREIGN KEY (instructor_id) REFERENCES instructors(id),
    CONSTRAINT chk_offerings_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_offerings_price CHECK (price >= 0),
    CONSTRAINT chk_offerings_capacity CHECK (capacity > 0),
    INDEX idx_offerings_course_status (course_id, status),
    INDEX idx_offerings_dates (start_date, end_date)
) ENGINE=InnoDB;

-- 8. Sessions within a scheduled class
CREATE TABLE class_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    offering_id BIGINT NOT NULL,
    session_number SMALLINT UNSIGNED NOT NULL,
    title VARCHAR(150) NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    meeting_provider ENUM(
        'ZOOM', 'GOOGLE_MEET', 'OTHER'
    ) NULL,
    meeting_url VARCHAR(1000) NULL,
    meeting_password VARCHAR(255) NULL,
    notes TEXT NULL,
    status ENUM(
        'SCHEDULED', 'RESCHEDULED', 'CANCELLED', 'COMPLETED'
    ) NOT NULL DEFAULT 'SCHEDULED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_sessions_number UNIQUE (offering_id, session_number),
    CONSTRAINT fk_sessions_offering
        FOREIGN KEY (offering_id) REFERENCES course_offerings(id),
    CONSTRAINT chk_sessions_time CHECK (ends_at > starts_at),
    INDEX idx_sessions_start (starts_at)
) ENGINE=InnoDB;

-- 9. Coupons
CREATE TABLE coupons (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    discount_type ENUM('PERCENT', 'FIXED') NOT NULL,
    discount_value DECIMAL(12,2) NOT NULL,
    max_uses INT UNSIGNED NULL,
    used_count INT UNSIGNED NOT NULL DEFAULT 0,
    expires_at DATETIME(6) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_coupons_code UNIQUE (code),
    CONSTRAINT chk_coupons_discount CHECK (discount_value > 0),
    INDEX idx_coupons_active_expiry (is_active, expires_at)
) ENGINE=InnoDB;

-- 10. Orders
CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_number VARCHAR(40) NOT NULL,
    user_id BIGINT NOT NULL,
    coupon_id BIGINT NULL,
    subtotal DECIMAL(12,2) NOT NULL,
    discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(12,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    status ENUM(
        'PENDING', 'PAID', 'FAILED', 'CANCELLED', 'EXPIRED'
    ) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    paid_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_orders_number UNIQUE (order_number),
    CONSTRAINT fk_orders_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_orders_coupon
        FOREIGN KEY (coupon_id) REFERENCES coupons(id),
    CONSTRAINT chk_orders_amounts CHECK (
        subtotal >= 0
        AND discount_amount >= 0
        AND total_amount >= 0
    ),
    INDEX idx_orders_user_created (user_id, created_at),
    INDEX idx_orders_status (status)
) ENGINE=InnoDB;

-- 11. Order items
CREATE TABLE order_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    offering_id BIGINT NOT NULL,
    offering_title_snapshot VARCHAR(255) NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_order_items_offering
        FOREIGN KEY (offering_id) REFERENCES course_offerings(id),
    CONSTRAINT chk_order_items_price CHECK (unit_price >= 0),
    INDEX idx_order_items_offering (offering_id)
) ENGINE=InnoDB;

-- 12. Payments
CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    provider VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    provider_transaction_id VARCHAR(120) NULL,
    provider_order_ref VARCHAR(120) NULL,
    amount DECIMAL(12,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    status ENUM(
        'PENDING', 'SUCCEEDED', 'FAILED', 'CANCELLED'
    ) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    paid_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_payments_order
        FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT chk_payments_amount CHECK (amount >= 0),
    INDEX idx_payments_order_status (order_id, status),
    INDEX idx_payments_provider_tx (provider, provider_transaction_id)
) ENGINE=InnoDB;

-- 13. Enrollments
CREATE TABLE enrollments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    offering_id BIGINT NOT NULL,
    order_item_id BIGINT NULL,
    learner_contact_name VARCHAR(150) NULL,
    learner_contact_phone VARCHAR(20) NULL,
    instructor_role TEXT NULL,
    status ENUM(
        'ACTIVE', 'EXPIRED', 'CANCELLED'
    ) NOT NULL DEFAULT 'ACTIVE',
    enrolled_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_enrollment_user_offering UNIQUE (user_id, offering_id),
    CONSTRAINT fk_enrollments_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_enrollments_offering
        FOREIGN KEY (offering_id) REFERENCES course_offerings(id),
    CONSTRAINT fk_enrollments_order_item
        FOREIGN KEY (order_item_id) REFERENCES order_items(id),
    INDEX idx_enrollments_offering_status (offering_id, status)
) ENGINE=InnoDB;

-- 14. Reviews
CREATE TABLE reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    rating TINYINT UNSIGNED NOT NULL,
    comment TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_reviews_user_course UNIQUE (user_id, course_id),
    CONSTRAINT fk_reviews_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_reviews_course
        FOREIGN KEY (course_id) REFERENCES courses(id),
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    INDEX idx_reviews_course_created (course_id, created_at)
) ENGINE=InnoDB;

-- 15. Wishlists
CREATE TABLE wishlists (
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    added_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, course_id),
    CONSTRAINT fk_wishlists_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_wishlists_course
        FOREIGN KEY (course_id) REFERENCES courses(id)
) ENGINE=InnoDB;

-- 16. Notifications
CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    type VARCHAR(40) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    read_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_notifications_user_read (user_id, is_read, created_at)
) ENGINE=InnoDB;