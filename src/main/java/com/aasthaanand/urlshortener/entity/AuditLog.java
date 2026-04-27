package com.aasthaanand.urlshortener.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "actor_id")
        private Long actorId;

        @Column(nullable = false, length = 100)
        private String action;

        @Column(name = "target_id")
        private Long targetId;

        @Column(name = "target_type", length = 50)
        private String targetType;

        @Column(length = 1024)
        private String details;

        @CreationTimestamp
        @Column(name = "created_at", updatable = false)
        private LocalDateTime createdAt;
}
