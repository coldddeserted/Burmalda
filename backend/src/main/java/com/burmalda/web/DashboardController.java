package com.burmalda.web;

import com.burmalda.security.AuthUser;
import com.burmalda.service.AccountantDashboardService;
import com.burmalda.service.EmployeeDashboardService;
import com.burmalda.service.UserService;
import com.burmalda.web.dto.AccountantViews;
import com.burmalda.web.dto.EmployeeViews;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final UserService users;
    private final EmployeeDashboardService employee;
    private final AccountantDashboardService accountant;

    public DashboardController(UserService users, EmployeeDashboardService employee,
                               AccountantDashboardService accountant) {
        this.users = users;
        this.employee = employee;
        this.accountant = accountant;
    }

    @GetMapping("/employee")
    public EmployeeViews.Dashboard employee(@AuthenticationPrincipal AuthUser me) {
        return employee.build(users.require(me));
    }

    @GetMapping("/accountant")
    public AccountantViews.Dashboard accountant(@AuthenticationPrincipal AuthUser me) {
        return accountant.build(users.require(me));
    }
}
