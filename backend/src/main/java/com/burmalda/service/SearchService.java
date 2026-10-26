package com.burmalda.service;

import com.burmalda.domain.Project;
import com.burmalda.domain.Role;
import com.burmalda.domain.Task;
import com.burmalda.domain.User;
import com.burmalda.repo.ProjectRepository;
import com.burmalda.repo.TaskRepository;
import com.burmalda.repo.UserRepository;
import com.burmalda.web.dto.CommonViews.SearchHit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Поиск из шапки: задачи, проекты, люди (людей ищет только бухгалтер). */
@Service
@Transactional(readOnly = true)
public class SearchService {

    private static final int LIMIT = 10;

    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final UserRepository users;

    public SearchService(TaskRepository tasks, ProjectRepository projects, UserRepository users) {
        this.tasks = tasks;
        this.projects = projects;
        this.users = users;
    }

    public List<SearchHit> search(User me, String q) {
        if (q == null || q.trim().length() < 2) return List.of();
        String s = q.trim().toLowerCase();
        List<SearchHit> res = new ArrayList<>();

        List<Task> taskPool = me.getRole() == Role.ACCOUNTANT
                ? tasks.findByAssignee_Company(me.getCompany())
                : tasks.findByAssigneeOrderByDueDateAsc(me);
        for (Task t : taskPool) {
            if (res.size() >= LIMIT) return res;
            if (t.getTitle().toLowerCase().contains(s)) {
                res.add(new SearchHit("task", t.getId(), t.getTitle(),
                        t.getProject().getName() + " · " + t.getStatus().api()));
            }
        }
        for (Project p : projects.findByCompany(me.getCompany())) {
            if (res.size() >= LIMIT) return res;
            if (p.getName().toLowerCase().contains(s)) {
                res.add(new SearchHit("project", p.getId(), p.getName(), p.getStatus().name().toLowerCase()));
            }
        }
        if (me.getRole() == Role.ACCOUNTANT) {
            for (User u : users.findByCompany(me.getCompany())) {
                if (res.size() >= LIMIT) return res;
                if (u.getName().toLowerCase().contains(s) || u.getEmail().toLowerCase().contains(s)) {
                    res.add(new SearchHit("user", u.getId(), u.getName(), u.getDepartment()));
                }
            }
        }
        return res;
    }
}
