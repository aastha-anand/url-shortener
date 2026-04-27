package com.urlshortener.repository;

import com.urlshortener.model.ShortUrl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    Page<ShortUrl> findByUserIdAndStatusNotOrderByCreatedAtDesc(Long userId, ShortUrl.Status status, Pageable pageable);

    @Query("SELECT s FROM ShortUrl s WHERE s.userId = :userId AND s.status != 'DELETED' " +
           "AND (LOWER(s.title) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(s.originalUrl) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<ShortUrl> searchByUser(@Param("userId") Long userId, @Param("q") String query, Pageable pageable);

    List<ShortUrl> findByStatusAndExpiresAtBefore(ShortUrl.Status status, LocalDateTime now);

    @Modifying
    @Query("UPDATE ShortUrl s SET s.clickCount = s.clickCount + 1 WHERE s.id = :id")
    void incrementClickCount(@Param("id") Long id);
}
