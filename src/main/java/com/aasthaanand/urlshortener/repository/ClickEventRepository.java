package com.urlshortener.repository;

import com.urlshortener.model.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    long countByShortUrlId(Long shortUrlId);

    List<ClickEvent> findByShortUrlIdAndClickedAtBetween(Long shortUrlId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT COUNT(c) FROM ClickEvent c WHERE c.shortUrl.id = :id AND c.clickedAt >= :since")
    long countRecentClicks(@Param("id") Long shortUrlId, @Param("since") LocalDateTime since);
}
