package com.burmalda.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** The company's PayPal business account. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class CompanyAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String company;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    private String bankLabel;

    private boolean paypalVerified;

    public CompanyAccount(String company) {
        this.company = company;
    }
}
