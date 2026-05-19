package com.shiyuan.dao.entity.db;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "event_photo")
public class EventPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "thumbnail_name")
    private String thumbnailName;

    @Column(name = "original_name")
    private String originalName;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getThumbnailName() { return thumbnailName; }
    public void setThumbnailName(String thumbnailName) { this.thumbnailName = thumbnailName; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
