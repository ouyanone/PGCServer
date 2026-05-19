package com.shiyuan.controller;

import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.dao.entity.db.EventPhoto;
import com.shiyuan.dao.repository.EventPhotoRepository;
import com.shiyuan.dao.repository.EventRepository;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class PhotoController extends BaseController {

    private static final int THUMB_WIDTH = 800;

    @Value("${photo.upload.dir:/opt/pgc/photos}")
    private String uploadDir;

    @Autowired
    private EventPhotoRepository photoRepository;

    @Autowired
    private EventRepository eventRepository;

    /** GET /webapi/photos/random?count=N — random photo thumbnails for carousel */
    @GetMapping(path = "/webapi/photos/random", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> getRandomPhotos(
            @RequestParam(defaultValue = "20") int count) {
        List<EventPhoto> all = photoRepository.findAll();
        Collections.shuffle(all);
        List<Map<String, Object>> result = all.stream().limit(count).map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            String thumb = p.getThumbnailName() != null ? p.getThumbnailName() : p.getFileName();
            m.put("thumbnailUrl", "/webapi/photos/file/" + thumb);
            m.put("url", "/webapi/photos/file/" + p.getFileName());
            m.put("eventName", p.getEvent() != null ? p.getEvent().getEventName() : "");
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /** GET /webapi/photos/events — events that have photos, ordered by date desc */
    @GetMapping(path = "/webapi/photos/events", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> getEventsWithPhotos() {
        List<Long> eventIds = photoRepository.findDistinctEventIds();
        if (eventIds.isEmpty()) return ResponseEntity.ok(Collections.emptyList());

        List<Map<String, Object>> result = new ArrayList<>();
        for (Long eid : eventIds) {
            eventRepository.findById(eid).ifPresent(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", e.getId());
                m.put("eventName", e.getEventName());
                m.put("eventDate", e.getEventDate());
                result.add(m);
            });
        }
        result.sort(Comparator.comparing(m -> {
            Object d = m.get("eventDate");
            return d != null ? d.toString() : "";
        }, Comparator.reverseOrder()));
        return ResponseEntity.ok(result);
    }

    /** GET /webapi/photos/event/{eventId} — photos for a specific event */
    @GetMapping(path = "/webapi/photos/event/{eventId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> getEventPhotos(@PathVariable Long eventId) {
        List<EventPhoto> photos = photoRepository.findByEventIdOrderByUploadedAtAsc(eventId);
        List<Map<String, Object>> result = photos.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("fileName", p.getFileName());
            m.put("originalName", p.getOriginalName());
            m.put("uploadedAt", p.getUploadedAt());
            m.put("url", "/webapi/photos/file/" + p.getFileName());
            String thumb = p.getThumbnailName() != null ? p.getThumbnailName() : p.getFileName();
            m.put("thumbnailUrl", "/webapi/photos/file/" + thumb);
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /** GET /webapi/photos/file/{fileName} — serve photo file */
    @GetMapping("/webapi/photos/file/{fileName:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String fileName) {
        try {
            Path file = Paths.get(uploadDir).resolve(fileName);
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists()) return ResponseEntity.notFound().build();
            String contentType = Files.probeContentType(file);
            if (contentType == null) contentType = "application/octet-stream";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /** POST /webapi/admin/photos/upload — upload photo and generate thumbnail */
    @PostMapping(path = "/webapi/admin/photos/upload", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> uploadPhoto(
            @RequestParam("file") MultipartFile file,
            @RequestParam("eventId") Long eventId) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) return ResponseEntity.badRequest().build();

        try {
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);

            String orig = file.getOriginalFilename();
            String ext = (orig != null && orig.contains("."))
                    ? orig.substring(orig.lastIndexOf('.')).toLowerCase()
                    : ".jpg";
            String uuid = UUID.randomUUID().toString();
            String storedName = uuid + ext;
            String thumbName = uuid + "_thumb.jpg";

            Path dest = uploadPath.resolve(storedName);
            file.transferTo(dest.toFile());

            // Generate thumbnail — Thumbnailator handles EXIF rotation automatically
            Path thumbDest = uploadPath.resolve(thumbName);
            Thumbnails.of(dest.toFile())
                    .width(THUMB_WIDTH)
                    .outputQuality(0.9)
                    .outputFormat("jpg")
                    .toFile(thumbDest.toFile());

            EventPhoto photo = new EventPhoto();
            photo.setEvent(eventOpt.get());
            photo.setFileName(storedName);
            photo.setThumbnailName(thumbName);
            photo.setOriginalName(orig);
            photo.setUploadedAt(LocalDateTime.now());
            EventPhoto saved = photoRepository.save(photo);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", saved.getId());
            m.put("fileName", saved.getFileName());
            m.put("originalName", saved.getOriginalName());
            m.put("url", "/webapi/photos/file/" + saved.getFileName());
            m.put("thumbnailUrl", "/webapi/photos/file/" + saved.getThumbnailName());
            return ResponseEntity.ok(m);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /** DELETE /webapi/admin/photos/{id} — delete photo and its thumbnail */
    @DeleteMapping("/webapi/admin/photos/{id}")
    public ResponseEntity<Void> deletePhoto(@PathVariable Long id) {
        return photoRepository.findById(id).map(p -> {
            try {
                Files.deleteIfExists(Paths.get(uploadDir).resolve(p.getFileName()));
                if (p.getThumbnailName() != null) {
                    Files.deleteIfExists(Paths.get(uploadDir).resolve(p.getThumbnailName()));
                }
            } catch (IOException ignored) {}
            photoRepository.delete(p);
            return ResponseEntity.noContent().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }
}
