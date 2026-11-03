package com.burmalda.repo;

import com.burmalda.domain.PaypalTransaction;
import com.burmalda.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaypalTransactionRepository extends JpaRepository<PaypalTransaction, String> {

    /** An employee's own wallet. */
    List<PaypalTransaction> findByOwnerOrderByDateTimeDesc(User owner);

    /** The company account (owner == null). */
    List<PaypalTransaction> findByCompanyAndOwnerIsNullOrderByDateTimeDesc(String company);
}
