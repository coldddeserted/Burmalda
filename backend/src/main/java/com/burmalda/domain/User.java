package com.burmalda.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private String department;

    /** Все данные изолированы по компании: бухгалтер видит только своих сотрудников. */
    @Column(nullable = false)
    private String company;

    /** Ставка в час — из неё + отработанных часов считается зарплата. */
    @Column(precision = 12, scale = 2)
    private BigDecimal hourlyRate = BigDecimal.ZERO;

    /** Баланс сотрудника на PayPal (куда падают выплаты). */
    @Column(precision = 12, scale = 2)
    private BigDecimal walletBalance = BigDecimal.ZERO;

    private String paypalEmail;

    private boolean paypalVerified;

    private LocalDateTime createdAt = LocalDateTime.now();
}
