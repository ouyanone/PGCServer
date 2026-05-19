package com.shiyuan.dao.repository;

import com.shiyuan.dao.entity.db.News;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface NewsRepository extends CrudRepository<News, Long> {
    List<News> findByIsActiveTrueOrderByPublishDateDesc();
}
