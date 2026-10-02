package com.urlshortener.domain.entity;

import java.time.Instant;
import java.util.UUID;

import com.urlshortener.domain.enums.PeriodType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "click_aggregates",
    indexes = {
        @Index(name = "idx_click_aggregates_url_period", columnList = "url_id, period_type"),
        @Index(name = "idx_click_aggregates_period_start", columnList = "period_start")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_click_aggregates", columnNames = {"url_id", "period_type", "period_start"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClickAggregate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "url_id", nullable = false)
    private Url url;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 10)
    private PeriodType periodType;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "click_count", nullable = false)
    @Builder.Default
    private int clickCount = 0;

    /** Increment click count. */
    public void incrementClickCount() {
        this.clickCount++;
    }

    /** Increments click count. */
    public void incrementClickCount(int amount) {
        this.clickCount += amount;
    }
}
