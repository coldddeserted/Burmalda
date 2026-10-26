package com.burmalda.repo;

import com.burmalda.domain.ScheduledPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduledPaymentRepository extends JpaRepository<ScheduledPayment, Long> {

    List<ScheduledPayment> findByCompanyOrderByDueDateAsc(String company);

    List<ScheduledPayment> findByCompanyAndApprovedFalseOrderByDueDateAsc(String company);
}
