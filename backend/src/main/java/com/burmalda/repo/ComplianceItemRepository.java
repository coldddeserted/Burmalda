package com.burmalda.repo;

import com.burmalda.domain.ComplianceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplianceItemRepository extends JpaRepository<ComplianceItem, Long> {

    List<ComplianceItem> findByCompany(String company);
}
