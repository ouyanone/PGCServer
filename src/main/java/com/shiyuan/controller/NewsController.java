package com.shiyuan.controller;

import com.shiyuan.dao.entity.db.News;
import com.shiyuan.dao.repository.NewsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class NewsController {

    @Autowired
    private NewsRepository newsRepository;

    @GetMapping("/webapi/news")
    public ResponseEntity<List<News>> getActiveNews() {
        return ResponseEntity.ok(newsRepository.findByIsActiveTrueOrderByPublishDateDesc());
    }

    @GetMapping("/webapi/news/all")
    public ResponseEntity<Iterable<News>> getAllNews() {
        return ResponseEntity.ok(newsRepository.findAll());
    }

    @PostMapping("/webapi/admin/news")
    public ResponseEntity<News> createNews(@RequestBody News news) {
        news.setId(null);
        return ResponseEntity.ok(newsRepository.save(news));
    }

    @PutMapping("/webapi/admin/news/{id}")
    public ResponseEntity<News> updateNews(@PathVariable Long id, @RequestBody News news) {
        news.setId(id);
        return ResponseEntity.ok(newsRepository.save(news));
    }

    @DeleteMapping("/webapi/admin/news/{id}")
    public ResponseEntity<Void> deleteNews(@PathVariable Long id) {
        newsRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
