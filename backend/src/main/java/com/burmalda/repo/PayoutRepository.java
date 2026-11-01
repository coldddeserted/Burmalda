package com.burmalda.repo;

import com.burmalda.domain.Payout;
import com.burmalda.domain.PayoutStatus;
import com.burmalda.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayoutRepository extends JpaRepository<Payout, Long> {

    List<Payout> findByUserOrderByDateDesc(User user);

    boolean existsByUserAndPeriodAndStatus(User user, String period, PayoutStatus status);

    /** Latest real PayPal batch of the company (for "did the money arrive?"). */
    Optional<Payout> findFirstByUser_CompanyAndBatchIdNotNullOrderByIdDesc(String company);

    /** Latest payout of one employee that went through a PayPal batch. */
    Optional<Payout> findFirstByUserAndBatchIdNotNullOrderByIdDesc(User user);
}
