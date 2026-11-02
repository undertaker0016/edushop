package com.edushop.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "class_sessions")
public class ClassSession {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "offering_id", nullable = false)
  private CourseOffering offering;

  @Column(name = "session_number", nullable = false)
  private Short sessionNumber;

  @Column(nullable = false, length = 150)
  private String title;

  @Column(name = "starts_at", nullable = false)
  private LocalDateTime startsAt;

  @Column(name = "ends_at", nullable = false)
  private LocalDateTime endsAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "meeting_provider")
  private MeetingProvider meetingProvider;

  @Column(name = "meeting_url", length = 1000)
  private String meetingUrl;

  @Column(name = "meeting_password", length = 255)
  private String meetingPassword;

  @Column(columnDefinition = "text")
  private String notes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SessionStatus status;

  protected ClassSession() {}

  public ClassSession(
      CourseOffering offering,
      Short sessionNumber,
      String title,
      LocalDateTime startsAt,
      LocalDateTime endsAt,
      MeetingProvider meetingProvider,
      String meetingUrl,
      String meetingPassword,
      String notes) {
    this.offering = Objects.requireNonNull(offering);
    this.sessionNumber = Objects.requireNonNull(sessionNumber);
    this.title = Objects.requireNonNull(title);
    this.startsAt = Objects.requireNonNull(startsAt);
    this.endsAt = Objects.requireNonNull(endsAt);
    this.meetingProvider = meetingProvider;
    this.meetingUrl = meetingUrl;
    this.meetingPassword = meetingPassword;
    this.notes = notes;
    this.status = SessionStatus.SCHEDULED;
  }

  public Long getId() {
    return id;
  }

  public Short getSessionNumber() {
    return sessionNumber;
  }

  public String getTitle() {
    return title;
  }

  public LocalDateTime getStartsAt() {
    return startsAt;
  }

  public LocalDateTime getEndsAt() {
    return endsAt;
  }

  public SessionStatus getStatus() {
    return status;
  }

  public MeetingProvider getMeetingProvider() {
    return meetingProvider;
  }

  public String getMeetingUrl() {
    return meetingUrl;
  }

  public String getMeetingPassword() {
    return meetingPassword;
  }

  public String getNotes() {
    return notes;
  }

  public void update(
      String title,
      LocalDateTime startsAt,
      LocalDateTime endsAt,
      MeetingProvider meetingProvider,
      String meetingUrl,
      String meetingPassword,
      String notes) {
    if (!this.startsAt.equals(startsAt) || !this.endsAt.equals(endsAt))
      this.status = SessionStatus.RESCHEDULED;
    this.title = Objects.requireNonNull(title);
    this.startsAt = Objects.requireNonNull(startsAt);
    this.endsAt = Objects.requireNonNull(endsAt);
    this.meetingProvider = meetingProvider;
    this.meetingUrl = meetingUrl;
    this.meetingPassword = meetingPassword;
    this.notes = notes;
    if (this.status == SessionStatus.CANCELLED) this.status = SessionStatus.RESCHEDULED;
  }

  public void cancel() {
    this.status = SessionStatus.CANCELLED;
  }

  public void updateRoom(MeetingProvider provider, String url, String password, String notes) {
    this.meetingProvider = provider;
    this.meetingUrl = url;
    this.meetingPassword = password;
    this.notes = notes;
  }
}
