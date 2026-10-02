package com.urlshortener.domain.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "gates", indexes = {
    @Index(name = "idx_gates_session_id", columnList = "session_id"),
    @Index(name = "idx_gates_phase", columnList = "phase")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Gate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "definition_id", length = 100)
    private String definitionId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private int phase;

    @Column(name = "requires_approval", nullable = false)
    @Builder.Default
    private boolean requiresApproval = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean approved = false;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_reason", columnDefinition = "TEXT")
    private String rejectedReason;

    @Column(name = "blocked_tasks", columnDefinition = "TEXT[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Builder.Default
    private List<String> blockedTasks = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    /** Approve. */
    public void approve(String approvedBy) {
        this.approved = true;
        this.approvedBy = approvedBy;
        this.approvedAt = Instant.now();
    }

    /** Rejects. */
    public void reject(String reason) {
        this.rejectedReason = reason;
    }
}
