package com.edushop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "courses")
public class Course {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "created_by_user_id", nullable = false)
  private Long createdByUserId;

  @Column(name = "title", nullable = false, length = 255)
  private String title;

  @Column(name = "slug", nullable = false, length = 255)
  private String slug;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Column(name = "thumbnail_url", length = 500)
  private String thumbnailUrl;

  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
  @Column(name = "thumbnail_media_id", length = 36)
  private String thumbnailMediaId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private CourseStatus status;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
  private LocalDateTime updatedAt;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "course_categories",
      joinColumns = @JoinColumn(name = "course_id", nullable = false),
      inverseJoinColumns = @JoinColumn(name = "category_id", nullable = false))
  private Set<Category> categories = new HashSet<>();

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "course_tags",
      joinColumns = @JoinColumn(name = "course_id", nullable = false),
      inverseJoinColumns = @JoinColumn(name = "tag_id", nullable = false))
  private Set<Tag> tags = new HashSet<>();

  protected Course() {}

  public Course(
      Long createdByUserId, String title, String slug, String description, String thumbnailUrl) {
    this.createdByUserId =
        Objects.requireNonNull(createdByUserId, "createdByUserId must not be null");
    this.title = Objects.requireNonNull(title, "title must not be null");
    this.slug = Objects.requireNonNull(slug, "slug must not be null");
    this.description = description;
    this.thumbnailUrl = thumbnailUrl;
    this.status = CourseStatus.DRAFT;
  }

  public Long getId() {
    return id;
  }

  public Long getCreatedByUserId() {
    return createdByUserId;
  }

  public String getTitle() {
    return title;
  }

  public String getSlug() {
    return slug;
  }

  public String getDescription() {
    return description;
  }

  public String getThumbnailUrl() {
    return thumbnailUrl;
  }

  public CourseStatus getStatus() {
    return status;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public Set<Category> getCategories() {
    return categories;
  }

  public Set<Tag> getTags() {
    return tags;
  }

  public void addCategory(Category category) {
    categories.add(Objects.requireNonNull(category, "category must not be null"));
  }

  public void addTag(Tag tag) {
    tags.add(Objects.requireNonNull(tag, "tag must not be null"));
  }

  public void publish() {
    this.status = CourseStatus.PUBLISHED;
  }

  public void archive() {
    this.status = CourseStatus.ARCHIVED;
  }

  public void updateDraft(String title, String slug, String description, String thumbnailUrl) {
    if (status != CourseStatus.DRAFT) {
      throw new IllegalStateException("Only draft courses can be edited");
    }
    this.title = Objects.requireNonNull(title, "title must not be null");
    this.slug = Objects.requireNonNull(slug, "slug must not be null");
    this.description = description;
    this.thumbnailUrl = thumbnailUrl;
  }

  public void replaceCategories(Collection<Category> categories) {
    this.categories.clear();
    this.categories.addAll(categories);
  }

  public void replaceTags(Collection<Tag> tags) {
    this.tags.clear();
    this.tags.addAll(tags);
  }

  public void updatePresentation(String title, String description, String thumbnailUrl) {
    if (status != CourseStatus.PUBLISHED)
      throw new IllegalStateException("Only published course presentation can be updated");
    this.title = Objects.requireNonNull(title);
    this.description = description;
    this.thumbnailUrl = thumbnailUrl;
  }

  public void assignThumbnailMedia(String id) {
    this.thumbnailMediaId = id;
  }

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  public LocalDateTime getDeletedAt() {
    return deletedAt;
  }

  public void moveToTrash() {
    this.status = CourseStatus.ARCHIVED;
    this.deletedAt = LocalDateTime.now();
  }

  public void restoreDraft() {
    this.deletedAt = null;
    this.status = CourseStatus.DRAFT;
  }
}
