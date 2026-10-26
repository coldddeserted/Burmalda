package com.burmalda.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Запланированный платёж компании. Пока не approved — висит в "Pending Approvals". */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ScheduledPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String label;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    private LocalDate dueDate;

    /** Payroll / Tax / Vendor / Expense / Transfer */
    private String kind;

    private String requestedBy;

    private boolean approved;
}
