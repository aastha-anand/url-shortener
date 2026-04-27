package com.urlshortener.repository;

import com.urlshortener.model.DailyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DailyStatsRepository extends JpaRepository<DailyStats, Long> {

    List<DailyStats> findByShortUrlIdAndStatDateBetweenOrderByStatDate(Long shortUrlId, LocalDate from, LocalDate to);

    Optional<DailyStats> findByShortUrlIdAndStatDate(Long shortUrlId, LocalDate date);

    @Modifying
    @Query("UPDATE DailyStats d SET d.clickCount = d.clickCount + 1 WHERE d.shortUrl.id = :id AND d.statDate = :date")
    int incrementDailyCount(@Param("id") Long shortUrlId, @Param("date") LocalDate date);
}
