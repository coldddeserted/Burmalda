package com.burmalda.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A payout to an employee (salary, bonus, expense refund). */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Payout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private User user;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    private String label;

    @Column(name = "pay_date")
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayoutStatus status;

    /** Which month the salary is for, format "2026-10". May be null for bonuses/refunds. */
    private String period;

    /** PayPal Mass Pay batch id. */
    private String batchId;
}
