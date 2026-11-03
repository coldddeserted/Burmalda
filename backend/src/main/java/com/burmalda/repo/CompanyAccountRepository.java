package com.burmalda.repo;

import com.burmalda.domain.CompanyAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyAccountRepository extends JpaRepository<CompanyAccount, Long> {

    Optional<CompanyAccount> findByCompany(String company);
}
