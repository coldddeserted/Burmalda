package com.burmalda.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A PayPal money movement.
 * owner == null  -> a company account transaction (seen by the accountant)
 * owner != null  -> a transaction of an employee's own wallet
 */
@Entity
@Table(name = "paypal_transactions")
@Getter
@Setter
@NoArgsConstructor
public class PaypalTransaction {

    /** PayPal-style id: 16 characters, e.g. 8XK21094AB552140. */
    @Id
    private String id;

    @Column(nullable = false)
    private String company;

    @ManyToOne
    private User owner;

    @Column(nullable = false)
    private LocalDateTime dateTime;

    @Column(nullable = false)
    private String name;

    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TxDirection direction;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(precision = 12, scale = 2)
    private BigDecimal fee = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TxStatus status = TxStatus.COMPLETED;

    @Enumerated(EnumType.STRING)
    private TxCategory category;

    /** Department the expense is charged to (for department budgets). */
    private String department;
}
