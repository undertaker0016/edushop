package com.edushop.service;

import com.edushop.dto.catalog.CourseWriteRequest;
import com.edushop.dto.catalog.ManagedCourseResponse;
import com.edushop.entity.Course;
import com.edushop.entity.CourseStatus;
import com.edushop.repository.CategoryRepository;
import com.edushop.repository.CourseRepository;
import com.edushop.repository.TagRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CourseManagementService {
  private final CourseRepository courses;
  private final CategoryRepository categories;
  private final TagRepository tags;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public CourseManagementService(
      CourseRepository courses,
      CategoryRepository categories,
      TagRepository tags,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.courses = courses;
    this.categories = categories;
    this.tags = tags;
    this.jdbc = jdbc;
  }

  @Transactional
  public ManagedCourseResponse create(long userId, CourseWriteRequest request) {
    if (courses.existsBySlug(request.slug())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Course slug already exists");
    }
    Course course =
        new Course(
            userId, request.title(), request.slug(), request.description(), request.thumbnailUrl());
    course.assignThumbnailMedia(mediaId(request.thumbnailUrl(), userId, false));
    applyTaxonomy(course, request.categorySlugs(), request.tags());
    return ManagedCourseResponse.from(courses.saveAndFlush(course));
  }

  @Transactional(readOnly = true)
  public List<ManagedCourseResponse> list(long userId, boolean admin) {
    List<Course> result =
        admin ? courses.findAll() : courses.findByCreatedByUserIdOrderByUpdatedAtDesc(userId);
    return result.stream()
        .filter(course -> course.getDeletedAt() == null)
        .map(ManagedCourseResponse::from)
        .toList();
  }

  @Transactional
  public ManagedCourseResponse update(
      long userId, boolean admin, long courseId, CourseWriteRequest request) {
    Course course = ownedCourse(userId, admin, courseId);
    course.assignThumbnailMedia(mediaId(request.thumbnailUrl(), userId, admin));
    if (courses.existsBySlugAndIdNot(request.slug(), courseId)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Course slug already exists");
    }

    if (course.getStatus() == CourseStatus.PUBLISHED) {
      var categorySlugs =
          course.getCategories().stream()
              .map(item -> item.getSlug())
              .collect(java.util.stream.Collectors.toSet());
      var tagNames =
          course.getTags().stream()
              .map(item -> item.getName())
              .collect(java.util.stream.Collectors.toSet());
      if (!course.getSlug().equals(request.slug())
          || (request.categorySlugs() != null
              && !categorySlugs.equals(new java.util.HashSet<>(request.categorySlugs())))
          || (request.tags() != null && !tagNames.equals(new java.util.HashSet<>(request.tags()))))
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Khóa đã xuất bản chỉ sửa tên, mô tả và ảnh. Đường dẫn/danh mục được giữ để tránh thay"
                + " đổi lịch sử.");
      course.updatePresentation(request.title(), request.description(), request.thumbnailUrl());
      return ManagedCourseResponse.from(courses.saveAndFlush(course));
    }
    try {
      course.updateDraft(
          request.title(), request.slug(), request.description(), request.thumbnailUrl());
      applyTaxonomy(course, request.categorySlugs(), request.tags());
    } catch (IllegalStateException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
    }
    return ManagedCourseResponse.from(courses.saveAndFlush(course));
  }

  @Transactional
  public ManagedCourseResponse changeStatus(
      long userId, boolean admin, long courseId, boolean publish) {
    Course course = ownedCourse(userId, admin, courseId);
    if (publish) {
      if (course.getStatus() != CourseStatus.DRAFT) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Only draft courses can be published");
      }
      if (course.getDescription() == null
          || course.getDescription().isBlank()
          || course.getCategories().stream().noneMatch(com.edushop.entity.Category::isActive)) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "A course needs a description and at least one active category before publishing");
      }
      course.publish();
    } else {
      if (course.getStatus() == CourseStatus.ARCHIVED) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Course is already archived");
      }
      course.archive();
    }
    return ManagedCourseResponse.from(courses.saveAndFlush(course));
  }

  private void applyTaxonomy(Course course, List<String> categorySlugs, List<String> tagNames) {
    List<String> slugs =
        categorySlugs == null
            ? List.of()
            : categorySlugs.stream().map(String::trim).distinct().toList();
    List<String> normalizedTags =
        tagNames == null ? List.of() : tagNames.stream().map(String::trim).distinct().toList();
    var foundCategories =
        slugs.isEmpty()
            ? List.<com.edushop.entity.Category>of()
            : categories.findBySlugInAndActiveTrue(slugs);
    if (foundCategories.size() != slugs.size())
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "One or more categories are missing or inactive");
    var foundTags =
        normalizedTags.isEmpty()
            ? List.<com.edushop.entity.Tag>of()
            : tags.findByNameIn(normalizedTags);
    if (foundTags.size() != normalizedTags.size())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more tags do not exist");
    course.replaceCategories(foundCategories);
    course.replaceTags(foundTags);
  }

  private Course ownedCourse(long userId, boolean admin, long courseId) {
    Course course =
        courses
            .findById(courseId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
    if (!admin && !course.getCreatedByUserId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
    }
    return course;
  }

  @Transactional(readOnly = true)
  public ManagedCourseResponse detail(long userId, boolean admin, long id) {
    return ManagedCourseResponse.from(ownedCourse(userId, admin, id));
  }

  private String mediaId(String value, long actor, boolean admin) {
    if (value == null || !value.startsWith("/api/v1/media/")) return null;
    String id = value.substring("/api/v1/media/".length());
    try {
      java.util.UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tham chiếu ảnh không hợp lệ.");
    }
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM media_assets WHERE id=? AND (owner_user_id=? OR ?)",
            Long.class,
            id,
            actor,
            admin)
        == 0)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Ảnh không tồn tại hoặc không thuộc tài khoản của bạn.");
    return id;
  }

  @Transactional(readOnly = true)
  public List<ManagedCourseResponse> listWithTrash(long userId, boolean admin) {
    return (admin ? courses.findAll() : courses.findByCreatedByUserIdOrderByUpdatedAtDesc(userId))
        .stream().map(ManagedCourseResponse::from).toList();
  }

  private Course lifecycleCourse(long actor, boolean admin, long id) {
    var course =
        courses
            .findByIdForUpdate(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!admin && !course.getCreatedByUserId().equals(actor))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return course;
  }

  @Transactional
  public ManagedCourseResponse trash(long actor, boolean admin, long id) {
    var course = lifecycleCourse(actor, admin, id);
    course.moveToTrash();
    return ManagedCourseResponse.from(courses.saveAndFlush(course));
  }

  @Transactional
  public ManagedCourseResponse restore(long actor, boolean admin, long id) {
    var course = lifecycleCourse(actor, admin, id);
    if (course.getStatus() != CourseStatus.ARCHIVED)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Chỉ khôi phục khóa đã lưu trữ hoặc trong thùng rác.");
    course.restoreDraft();
    return ManagedCourseResponse.from(courses.saveAndFlush(course));
  }

  @Transactional
  public void purge(long actor, boolean admin, long id) {
    var course = lifecycleCourse(actor, admin, id);
    if (course.getDeletedAt() == null)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Chuyển vào thùng rác trước khi xóa vĩnh viễn.");
    long history =
        jdbc.queryForObject(
            "SELECT (SELECT COUNT(*) FROM order_items i JOIN course_offerings o ON"
                + " o.id=i.offering_id WHERE o.course_id=?)+(SELECT COUNT(*) FROM enrollments e"
                + " JOIN course_offerings o ON o.id=e.offering_id WHERE o.course_id=?)+(SELECT"
                + " COUNT(*) FROM reviews WHERE course_id=?)+(SELECT COUNT(*) FROM"
                + " notification_campaigns n WHERE n.course_id=? OR n.offering_id IN (SELECT id"
                + " FROM course_offerings WHERE course_id=?))",
            Long.class,
            id,
            id,
            id,
            id,
            id);
    if (history > 0)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Khóa đã có đơn hàng, ghi danh, đánh giá hoặc lịch sử thông báo. Chỉ được xóa mềm để giữ"
              + " dữ liệu.");
    jdbc.update(
        "DELETE s FROM class_sessions s JOIN course_offerings o ON o.id=s.offering_id WHERE"
            + " o.course_id=?",
        id);
    jdbc.update("DELETE FROM course_offerings WHERE course_id=?", id);
    jdbc.update("DELETE FROM wishlists WHERE course_id=?", id);
    courses.delete(course);
    courses.flush();
  }
}
