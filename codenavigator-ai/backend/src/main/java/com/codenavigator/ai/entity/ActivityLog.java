package com.codenavigator.ai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Append-only audit trail. Written by services whenever a notable action happens
 * (register, login, repo submitted, analysis run, report generated, etc.).
 * Read by the Admin Dashboard's "Recent Activity" feed.
 */
@Entity
@Table(name = "activity_logs")
@Getter
@Setter
@NoArgsConstructor
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String action; // e.g. "USER_REGISTERED", "USER_LOGIN"

    @Column(length = 255)
    private String description;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    public ActivityLog(User user, String action, String description) {
        this.user = user;
        this.action = action;
        this.description = description;
    }
}
