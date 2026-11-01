package com.shiyuan.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import com.shiyuan.dao.entity.db.Event;
import com.shiyuan.dao.entity.db.EventPhoto;
import com.shiyuan.dao.repository.EventPhotoRepository;

/**
 * The home carousel should showcase photos from the most recent EVENTS (by event date,
 * not by when someone happened to upload them), rather than a random sample of the archive.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PhotoControllerTest {

    @Mock
    EventPhotoRepository photoRepository;

    @InjectMocks
    PhotoController photoController;

    private EventPhoto photo(Long id, String file, String thumb, String eventName) {
        Event e = new Event();
        e.setEventName(eventName);
        EventPhoto p = new EventPhoto();
        p.setId(id);
        p.setFileName(file);
        p.setThumbnailName(thumb);
        p.setUploadedAt(LocalDateTime.now());
        p.setEvent(e);
        return p;
    }

    @Test
    void latestPhotos_asksTheDatabaseForTheMostRecentEventsFirst() {
        when(photoRepository.findLatestByEventDate(any(Pageable.class)))
                .thenReturn(List.of(photo(1L, "a.jpg", "a_t.jpg", "Crab Cup")));

        photoController.getLatestPhotos(15);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(photoRepository).findLatestByEventDate(page.capture());
        assertEquals(15, page.getValue().getPageSize(), "should ask for exactly the requested count");
        assertEquals(0, page.getValue().getPageNumber());
        // The whole archive must not be pulled into memory to be shuffled.
        verify(photoRepository, never()).findAll();
    }

    @Test
    void latestPhotos_mapsThumbnailAndEventName() {
        when(photoRepository.findLatestByEventDate(any(Pageable.class)))
                .thenReturn(List.of(photo(7L, "full.jpg", "thumb.jpg", "Crab Cup")));

        ResponseEntity<List<Map<String, Object>>> res = photoController.getLatestPhotos(15);
        Map<String, Object> row = res.getBody().get(0);

        assertEquals(7L, row.get("id"));
        assertEquals("/webapi/photos/file/thumb.jpg", row.get("thumbnailUrl"));
        assertEquals("/webapi/photos/file/full.jpg", row.get("url"));
        assertEquals("Crab Cup", row.get("eventName"));
    }

    @Test
    void latestPhotos_fallsBackToTheFullImageWhenNoThumbnailExists() {
        when(photoRepository.findLatestByEventDate(any(Pageable.class)))
                .thenReturn(List.of(photo(9L, "full.jpg", null, "Crab Cup")));

        Map<String, Object> row = photoController.getLatestPhotos(15).getBody().get(0);

        assertEquals("/webapi/photos/file/full.jpg", row.get("thumbnailUrl"));
    }
}
