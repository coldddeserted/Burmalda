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

    /** All data is isolated per company: an accountant sees only their own employees. */
    @Column(nullable = false)
    private String company;

    /** Hourly rate — the salary is calculated from it and the tracked hours. */
    @Column(precision = 12, scale = 2)
    private BigDecimal hourlyRate = BigDecimal.ZERO;

    /** The employee's PayPal balance (where payouts arrive). */
    @Column(precision = 12, scale = 2)
    private BigDecimal walletBalance = BigDecimal.ZERO;

    private String paypalEmail;

    private boolean paypalVerified;

    private LocalDateTime createdAt = LocalDateTime.now();
}
