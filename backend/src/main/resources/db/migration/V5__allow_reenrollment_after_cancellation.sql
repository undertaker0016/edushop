-- Preserve enrollment history while allowing the same user to purchase again
-- after a previous enrollment was cancelled.
ALTER TABLE enrollments
    DROP INDEX uk_enrollment_user_offering,
    ADD INDEX idx_enrollments_user_offering_status (user_id, offering_id, status);
