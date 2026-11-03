package com.burmalda.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A document checklist item (tax reports, contractor forms, etc.). */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ComplianceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String name;

    private String status;

    private boolean ok;

    public ComplianceItem(String company, String name, String status, boolean ok) {
        this.company = company;
        this.name = name;
        this.status = status;
        this.ok = ok;
    }
}
