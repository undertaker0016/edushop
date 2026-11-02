CREATE TABLE media_assets (
 id CHAR(36) PRIMARY KEY,
 owner_user_id BIGINT NOT NULL,
 original_name VARCHAR(255) NOT NULL,
 storage_name VARCHAR(80) NOT NULL UNIQUE,
 content_type VARCHAR(80) NOT NULL,
 byte_size BIGINT NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_media_owner FOREIGN KEY(owner_user_id) REFERENCES users(id)
);
CREATE TABLE instructor_applications (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 full_name VARCHAR(150) NOT NULL,
 email VARCHAR(255) NOT NULL,
 phone VARCHAR(20),
 expertise VARCHAR(255) NOT NULL,
 message TEXT NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_application_user FOREIGN KEY(user_id) REFERENCES users(id),
 INDEX idx_application_status(status,created_at)
);
ALTER TABLE coupons ADD COLUMN title VARCHAR(150), ADD COLUMN description VARCHAR(1000),
 ADD COLUMN minimum_order_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
 ADD COLUMN starts_at DATETIME(6),
 ADD CONSTRAINT chk_coupon_minimum CHECK(minimum_order_amount>=0);
