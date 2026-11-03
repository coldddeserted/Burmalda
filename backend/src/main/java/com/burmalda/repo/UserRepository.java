package com.burmalda.repo;

import com.burmalda.domain.Role;
import com.burmalda.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByCompanyAndRoleOrderByNameAsc(String company, Role role);

    List<User> findByCompanyAndDepartmentAndRole(String company, String department, Role role);

    List<User> findByCompany(String company);
}
