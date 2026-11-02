CREATE TABLE password_reset_codes (
 user_id BIGINT NOT NULL PRIMARY KEY,
 code_hash VARCHAR(255) NOT NULL,
 expires_at DATETIME(6) NOT NULL,
 next_send_at DATETIME(6) NOT NULL,
 attempts INT NOT NULL DEFAULT 0,
 consumed BOOLEAN NOT NULL DEFAULT FALSE,
 CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users(id)
);
