package com.burmalda.web;

import com.burmalda.security.AuthUser;
import com.burmalda.service.*;
import com.burmalda.web.dto.CommonViews.Ok;
import com.burmalda.web.dto.CommonViews.OkBalance;
import com.burmalda.web.dto.CommonViews.TaskDto;
import com.burmalda.web.dto.EmployeeViews.TeamMember;
import com.burmalda.web.dto.EmployeeViews.Wallet;
import com.burmalda.web.dto.Requests;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Задачи, трекер времени, кошелёк, добавление задачи коллеге. */
@RestController
@RequestMapping("/api")
public class EmployeeController {

    private final UserService users;
    private final TaskService tasks;
    private final TimeService time;
    private final WalletService wallet;

    public EmployeeController(UserService users, TaskService tasks, TimeService time, WalletService wallet) {
        this.users = users;
        this.tasks = tasks;
        this.time = time;
        this.wallet = wallet;
    }

    @GetMapping("/tasks/mine")
    public List<TaskDto> myTasks(@AuthenticationPrincipal AuthUser me) {
        return tasks.mine(users.require(me));
    }

    @PatchMapping("/tasks/{id}")
    public Ok updateTask(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                         @Valid @RequestBody Requests.TaskStatus body) {
        tasks.updateStatus(users.require(me), id, body.status());
        return Ok.OK;
    }

    @PostMapping("/team/members")
    public TeamMember addMember(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody Requests.AddMember body) {
        return tasks.addMember(users.require(me), body);
    }

    @PostMapping("/time/log")
    public Map<String, Object> logTime(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody Requests.TimeLog body) {
        long saved = time.log(users.require(me), body.seconds(), body.label());
        return Map.of("ok", true, "savedSeconds", saved);
    }

    @GetMapping("/wallet")
    public Wallet wallet(@AuthenticationPrincipal AuthUser me) {
        return wallet.wallet(users.require(me));
    }

    @PostMapping("/wallet/withdraw")
    public OkBalance withdraw(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody Requests.Amount body) {
        return new OkBalance(true, wallet.withdraw(users.require(me), body.amount()));
    }
}
