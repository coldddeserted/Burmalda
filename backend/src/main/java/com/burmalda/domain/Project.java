package com.burmalda.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String company;

    @ManyToOne
    private User lead;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status = ProjectStatus.RUNNING;

    /** 0..100 */
    private int progress;

    private LocalDate startDate;
    private LocalDate dueDate;
    private LocalDate endDate;

    @Column(precision = 12, scale = 2)
    private BigDecimal budget = BigDecimal.ZERO;

    /** Комментарий для проектов "на обсуждении". */
    private String note;
}
