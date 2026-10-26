package com.burmalda.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Отрезок отработанного времени (из трекера на дашборде сотрудника). */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class TimeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private long seconds;

    /** Над чем работал (название проекта). */
    private String label;

    public TimeLog(User user, LocalDateTime startedAt, long seconds, String label) {
        this.user = user;
        this.startedAt = startedAt;
        this.seconds = seconds;
        this.label = label;
    }
}
