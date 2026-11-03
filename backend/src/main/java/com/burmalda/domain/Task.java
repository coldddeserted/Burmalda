package com.burmalda.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @ManyToOne(optional = false)
    private Project project;

    @ManyToOne(optional = false)
    private User assignee;

    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    private Priority priority = Priority.MID;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.TODO;

    private LocalDateTime createdAt = LocalDateTime.now();

    /** Когда задачу закрыли — нужно для расчёта "сколько задач закрыто за месяц". */
    private LocalDateTime completedAt;
}
