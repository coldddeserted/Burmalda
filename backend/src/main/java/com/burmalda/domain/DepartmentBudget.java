package com.burmalda.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Monthly department budget. Spending is calculated from the department's salaries and expenses. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class DepartmentBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String department;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal allocated;

    public DepartmentBudget(String company, String department, BigDecimal allocated) {
        this.company = company;
        this.department = department;
        this.allocated = allocated;
    }
}
