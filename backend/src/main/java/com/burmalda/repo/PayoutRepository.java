package com.burmalda.repo;

import com.burmalda.domain.Payout;
import com.burmalda.domain.PayoutStatus;
import com.burmalda.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PayoutRepository extends JpaRepository<Payout, Long> {

    List<Payout> findByUserOrderByDateDesc(User user);

    boolean existsByUserAndPeriodAndStatus(User user, String period, PayoutStatus status);
}
