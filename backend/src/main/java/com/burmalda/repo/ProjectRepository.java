package com.burmalda.repo;

import com.burmalda.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByCompany(String company);

    Optional<Project> findFirstByCompanyAndNameIgnoreCase(String company, String name);
}
