package com.urlshortener.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.urlshortener.domain.entity.ClickAggregate;
import com.urlshortener.domain.enums.PeriodType;

@Repository
public interface ClickAggregateRepository extends JpaRepository<ClickAggregate, UUID> {

    /** Atomically inserts or increments the click aggregate for a URL, period type and period start. */
    @Modifying
    @Query(value = """
        INSERT INTO click_aggregates (id, url_id, period_type, period_start, click_count)
        VALUES (gen_random_uuid(), :urlId, :periodType, :periodStart, :count)
        ON CONFLICT (url_id, period_type, period_start)
        DO UPDATE SET click_count = click_aggregates.click_count + EXCLUDED.click_count
        """, nativeQuery = true)
    void addClicks(@Param("urlId") UUID urlId, @Param("periodType") String periodType,
        @Param("periodStart") Instant periodStart, @Param("count") int count);

    /** Returns the aggregates for the URL at the requested period granularity. */
    List<ClickAggregate> findByUrlIdAndPeriodType(UUID urlId, PeriodType periodType);

    /** Returns aggregates whose period starts fall within the inclusive time range. */
    List<ClickAggregate> findByUrlIdAndPeriodTypeAndPeriodStartBetween(
        UUID urlId, PeriodType periodType, Instant from, Instant to);

    /** Returns the aggregate matching the URL, period type and exact period start. */
    Optional<ClickAggregate> findByUrlIdAndPeriodTypeAndPeriodStart(
        UUID urlId, PeriodType periodType, Instant periodStart);

    /** Returns the total aggregate click count for the URL and period type, or zero when absent. */
    @Query("SELECT COALESCE(SUM(c.clickCount), 0) FROM ClickAggregate c WHERE c.url.id = :urlId AND c.periodType = :periodType")
    long sumClickCountByUrlIdAndPeriodType(@Param("urlId") UUID urlId, @Param("periodType") PeriodType periodType);

    /** Deletes aggregates for the supplied URL identifiers and returns the number deleted. */
    @Modifying
    @Query("DELETE FROM ClickAggregate c WHERE c.url.id IN :urlIds")
    int deleteByUrlIdIn(@Param("urlIds") List<UUID> urlIds);

    /** Deletes all aggregates belonging to the URL. */
    @Modifying
    @Query("DELETE FROM ClickAggregate c WHERE c.url.id = :urlId")
    void deleteByUrlId(@Param("urlId") UUID urlId);

    /** Returns the URL aggregates ordered from newest to oldest period start. */
    List<ClickAggregate> findByUrlIdOrderByPeriodStartDesc(UUID urlId);
}
