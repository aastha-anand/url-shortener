package com.aasthaanand.urlshortener.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "blocked_domains",
        uniqueConstraints = @UniqueConstraint(columnNames = {"domain", "active"})
)

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class BloackedDomain {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(nullable = false)
        private String domain;

        private String reason;

        @Column(nullable = false)
        private boolean active = true;

}
