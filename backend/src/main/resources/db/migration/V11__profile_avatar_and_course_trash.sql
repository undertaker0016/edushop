ALTER TABLE users
 ADD COLUMN avatar_url VARCHAR(500) NULL,
 ADD COLUMN avatar_media_id CHAR(36) NULL,
 ADD CONSTRAINT fk_user_avatar_media FOREIGN KEY(avatar_media_id) REFERENCES media_assets(id);
ALTER TABLE courses ADD COLUMN deleted_at DATETIME(6) NULL;
CREATE INDEX idx_courses_deleted_status ON courses(deleted_at,status);

UPDATE users u JOIN instructors i ON i.user_id=u.id
SET u.avatar_url=i.avatar_url,
 u.avatar_media_id=(SELECT m.id FROM media_assets m WHERE CONCAT('/api/v1/media/',m.id)=i.avatar_url AND m.owner_user_id=u.id LIMIT 1)
WHERE u.avatar_url IS NULL AND i.avatar_url IS NOT NULL;
ALTER TABLE courses ADD CONSTRAINT chk_course_trash_archived CHECK(deleted_at IS NULL OR status='ARCHIVED');
