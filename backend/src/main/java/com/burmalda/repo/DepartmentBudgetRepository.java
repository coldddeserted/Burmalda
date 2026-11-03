package com.burmalda.repo;

import com.burmalda.domain.DepartmentBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepartmentBudgetRepository extends JpaRepository<DepartmentBudget, Long> {

    List<DepartmentBudget> findByCompanyOrderByAllocatedDesc(String company);
}
