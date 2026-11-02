CREATE TABLE notification_campaigns (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sender_user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    type VARCHAR(40) NOT NULL,
    audience VARCHAR(40) NOT NULL,
    course_id BIGINT NULL,
    offering_id BIGINT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SENDING',
    recipient_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    sent_at DATETIME(6) NULL,
    CONSTRAINT uk_campaign_sender_key UNIQUE (sender_user_id, idempotency_key),
    FOREIGN KEY (sender_user_id) REFERENCES users(id),
    FOREIGN KEY (course_id) REFERENCES courses(id),
    FOREIGN KEY (offering_id) REFERENCES course_offerings(id),
    INDEX idx_campaign_sender_created (sender_user_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE notification_deliveries (
    campaign_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (campaign_id, user_id),
    FOREIGN KEY (campaign_id) REFERENCES notification_campaigns(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

ALTER TABLE notifications
    ADD COLUMN campaign_id BIGINT NULL,
    ADD CONSTRAINT fk_notification_campaign FOREIGN KEY (campaign_id) REFERENCES notification_campaigns(id),
    ADD CONSTRAINT uk_notification_campaign_user UNIQUE (campaign_id, user_id);
