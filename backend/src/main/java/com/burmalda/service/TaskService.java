package com.burmalda.service;

import com.burmalda.domain.*;
import com.burmalda.repo.ProjectRepository;
import com.burmalda.repo.TaskRepository;
import com.burmalda.repo.UserRepository;
import com.burmalda.util.Errors;
import com.burmalda.util.Fmt;
import com.burmalda.web.dto.CommonViews.TaskDto;
import com.burmalda.web.dto.EmployeeViews.TeamMember;
import com.burmalda.web.dto.Requests;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class TaskService {

    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final UserRepository users;

    public TaskService(TaskRepository tasks, ProjectRepository projects, UserRepository users) {
        this.tasks = tasks;
        this.projects = projects;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<TaskDto> mine(User me) {
        return tasks.findByAssigneeOrderByDueDateAsc(me).stream().map(TaskService::toDto).toList();
    }

    /** Смена статуса. Если все задачи проекта закрыты — проект считается законченным. */
    public void updateStatus(User me, Long id, String status) {
        Task t = tasks.findById(id).orElseThrow(() -> Errors.notFound("Задача не найдена"));
        boolean own = t.getAssignee().getId().equals(me.getId());
        boolean accountantOfCompany = me.getRole() == Role.ACCOUNTANT
                && t.getAssignee().getCompany().equals(me.getCompany());
        if (!own && !accountantOfCompany) throw Errors.forbidden("Это не ваша задача");

        TaskStatus newStatus = TaskStatus.from(status);
        t.setStatus(newStatus);
        t.setCompletedAt(newStatus == TaskStatus.DONE ? LocalDateTime.now() : null);

        recalcProject(t.getProject());
    }

    /** Добавить коллеге задачу (кнопка "Add member" на дашборде сотрудника). */
    public TeamMember addMember(User me, Requests.AddMember r) {
        if (r.email() == null || r.email().isBlank()) throw Errors.badRequest("Укажите email коллеги");
        User member = users.findByEmailIgnoreCase(r.email().trim())
                .filter(u -> u.getCompany().equals(me.getCompany()))
                .orElseThrow(() -> Errors.notFound("Сотрудник с таким email не найден в компании"));

        Project general = projects.findFirstByCompanyAndNameIgnoreCase(me.getCompany(), "General")
                .orElseGet(() -> {
                    Project p = new Project();
                    p.setName("General");
                    p.setCompany(me.getCompany());
                    p.setStartDate(LocalDate.now());
                    p.setNote("Задачи без отдельного проекта");
                    return projects.save(p);
                });

        Task t = new Task();
        t.setTitle(r.task() == null || r.task().isBlank() ? "New task" : r.task().trim());
        t.setProject(general);
        t.setAssignee(member);
        t.setDueDate(LocalDate.now().plusDays(7));
        tasks.save(t);
        recalcProject(general);
        return new TeamMember(member.getId(), member.getName(), t.getTitle(), "pending");
    }

    private void recalcProject(Project p) {
        List<Task> all = tasks.findByProject(p);
        if (all.isEmpty()) return;
        long done = all.stream().filter(x -> x.getStatus() == TaskStatus.DONE).count();
        p.setProgress((int) Math.round(done * 100.0 / all.size()));
        if (done == all.size() && p.getStatus() == ProjectStatus.RUNNING) {
            p.setStatus(ProjectStatus.ENDED);
            p.setEndDate(LocalDate.now());
        } else if (done < all.size() && p.getStatus() == ProjectStatus.ENDED) {
            p.setStatus(ProjectStatus.RUNNING); // задачу переоткрыли
            p.setEndDate(null);
        }
    }

    public static TaskDto toDto(Task t) {
        return new TaskDto(t.getId(), t.getTitle(), t.getProject().getName(), Fmt.date(t.getDueDate()),
                t.getPriority().api(), t.getStatus().api());
    }
}
