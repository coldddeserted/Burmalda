package com.burmalda.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Выплата сотруднику (зарплата, бонус, возврат расходов). */
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

    /** За какой месяц зарплата, формат "2026-10". У бонусов/возвратов может быть null. */
    private String period;

    /** ID пачки PayPal Mass Pay. */
    private String batchId;
}
