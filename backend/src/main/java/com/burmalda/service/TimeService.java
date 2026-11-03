package com.burmalda.service;

import com.burmalda.domain.Task;
import com.burmalda.domain.TaskStatus;
import com.burmalda.domain.TimeLog;
import com.burmalda.domain.User;
import com.burmalda.repo.TaskRepository;
import com.burmalda.repo.TimeLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@Transactional
public class TimeService {

    private final TimeLogRepository timeLogs;
    private final TaskRepository tasks;
    private final PayrollService payroll;

    public TimeService(TimeLogRepository timeLogs, TaskRepository tasks, PayrollService payroll) {
        this.timeLogs = timeLogs;
        this.tasks = tasks;
        this.payroll = payroll;
    }

    /**
     * The frontend starts the timer from "already worked today" and on Stop sends the current value,
     * then resets to 0. So:
     *  - seconds > today's total -> it is a continuation, store the difference;
     *  - otherwise -> a new session from zero, store seconds as is.
     */
    public long log(User me, long seconds, String label) {
        long today = payroll.secondsOn(me, LocalDate.now());
        long delta = seconds > today ? seconds - today : seconds;
        if (delta <= 0) return 0;
        delta = Math.min(delta, 16 * 3600); // guard against garbage: at most 16 hours at once

        String what = label != null && !label.isBlank() ? label : currentProject(me);
        timeLogs.save(new TimeLog(me, LocalDateTime.now().minusSeconds(delta), delta, what));
        return delta;
    }

    private String currentProject(User me) {
        return tasks.findByAssigneeOrderByDueDateAsc(me).stream()
                .filter(t -> t.getStatus() == TaskStatus.PROGRESS)
                .map(Task::getProject)
                .map(p -> p.getName())
                .findFirst()
                .orElse("General");
    }
}
