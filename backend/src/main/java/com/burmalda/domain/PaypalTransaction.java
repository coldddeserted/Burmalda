package com.burmalda.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Движение денег по PayPal.
 * owner == null  -> транзакция счёта компании (видит бухгалтер)
 * owner != null  -> транзакция личного кошелька сотрудника
 */
@Entity
@Table(name = "paypal_transactions")
@Getter
@Setter
@NoArgsConstructor
public class PaypalTransaction {

    /** ID в стиле PayPal: 16 символов, например 8XK21094AB552140. */
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

    /** Отдел, на который списываются расходы (для бюджета по отделам). */
    private String department;
}
