package com.burmalda.repo;

import com.burmalda.domain.Project;
import com.burmalda.domain.Task;
import com.burmalda.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByAssigneeOrderByDueDateAsc(User assignee);

    List<Task> findByProject(Project project);

    List<Task> findByAssignee_Company(String company);
}
