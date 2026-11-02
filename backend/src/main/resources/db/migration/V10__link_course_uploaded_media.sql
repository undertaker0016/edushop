ALTER TABLE courses
 ADD COLUMN thumbnail_media_id CHAR(36) NULL,
 ADD CONSTRAINT fk_course_thumbnail_media FOREIGN KEY(thumbnail_media_id) REFERENCES media_assets(id);
