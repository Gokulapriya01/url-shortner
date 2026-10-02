package com.urlshortener.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.urlshortener.domain.entity.Url;

@Repository
public interface UrlRepository extends JpaRepository<Url, UUID> {

    /** Returns the URL with the generated short code, if present. */
    Optional<Url> findByShortCode(String shortCode);

    /** Returns the URL with the custom alias, if present. */
    Optional<Url> findByCustomAlias(String customAlias);

    /** Returns whether the generated short code is already stored. */
    boolean existsByShortCode(String shortCode);

    /** Returns whether the custom alias is already stored. */
    boolean existsByCustomAlias(String customAlias);

    /** Atomically increments the URL click count by one and updates its modification timestamp. */
    @Modifying
    @Query("UPDATE Url u SET u.clickCount = u.clickCount + 1, u.updatedAt = CURRENT_TIMESTAMP WHERE u.id = :id")
    void incrementClickCount(@Param("id") UUID id);

    /** Atomically adds the supplied count to the URL click count and updates its modification timestamp. */
    @Modifying
    @Query("UPDATE Url u SET u.clickCount = u.clickCount + :count, u.updatedAt = CURRENT_TIMESTAMP WHERE u.id = :id")
    void addClickCount(@Param("id") UUID id, @Param("count") int count);

    /** Returns URLs with expiration timestamps strictly before the supplied instant. */
    List<Url> findByExpiresAtBefore(Instant timestamp);

    /** Returns a page of URL identifiers whose expiration timestamps are strictly before the supplied instant. */
    @Query("SELECT u.id FROM Url u WHERE u.expiresAt < :now")
    List<UUID> findExpiredUrlIds(@Param("now") Instant now, Pageable pageable);

    /** Counts URLs with expiration timestamps strictly before the supplied instant. */
    long countByExpiresAtBefore(Instant timestamp);

    /** Returns the URL matching either its generated short code or custom alias, if present. */
    @Query("SELECT u FROM Url u WHERE u.shortCode = :code OR u.customAlias = :code")
    Optional<Url> findByShortCodeOrCustomAlias(@Param("code") String code);
}
