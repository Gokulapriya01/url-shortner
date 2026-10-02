package com.urlshortener.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.urlshortener.domain.entity.ClickEvent;

@Repository
public interface ClickEventRepository extends JpaRepository<ClickEvent, UUID> {

    /** Returns all recorded click events for the URL. */
    List<ClickEvent> findByUrlId(UUID urlId);

    /** Returns URL click events within the inclusive timestamp range. */
    List<ClickEvent> findByUrlIdAndTimestampBetween(UUID urlId, Instant from, Instant to);

    /** Counts all recorded click events for the URL. */
    @Query("SELECT COUNT(c) FROM ClickEvent c WHERE c.url.id = :urlId")
    long countByUrlId(@Param("urlId") UUID urlId);

    /** Counts URL click events within the inclusive timestamp range. */
    @Query("SELECT COUNT(c) FROM ClickEvent c WHERE c.url.id = :urlId AND c.timestamp BETWEEN :from AND :to")
    long countByUrlIdAndTimestampBetween(
        @Param("urlId") UUID urlId,
        @Param("from") Instant from,
        @Param("to") Instant to
    );

    /** Deletes events for the supplied URL identifiers and returns the number deleted. */
    @Modifying
    @Query("DELETE FROM ClickEvent c WHERE c.url.id IN :urlIds")
    int deleteByUrlIdIn(@Param("urlIds") List<UUID> urlIds);

    /** Deletes all click events belonging to the URL. */
    @Modifying
    @Query("DELETE FROM ClickEvent c WHERE c.url.id = :urlId")
    void deleteByUrlId(@Param("urlId") UUID urlId);
}
