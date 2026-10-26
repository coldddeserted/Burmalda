package com.burmalda.service;

import com.burmalda.domain.CompanyAccount;
import com.burmalda.domain.Role;
import com.burmalda.domain.User;
import com.burmalda.repo.CompanyAccountRepository;
import com.burmalda.repo.UserRepository;
import com.burmalda.security.AuthUser;
import com.burmalda.security.JwtService;
import com.burmalda.util.Errors;
import com.burmalda.web.dto.CommonViews.LoginResponse;
import com.burmalda.web.dto.CommonViews.UserDto;
import com.burmalda.web.dto.Requests;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AuthService {

    private static final BigDecimal DEFAULT_RATE = new BigDecimal("20.00");

    private final UserRepository users;
    private final CompanyAccountRepository accounts;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, CompanyAccountRepository accounts,
                       PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.accounts = accounts;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public UserDto register(Requests.Register r) {
        String email = r.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw Errors.conflict("Пользователь с таким email уже существует");
        }
        User u = new User();
        u.setName(r.name().trim());
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(r.password()));
        u.setRole(Role.from(r.role()));
        u.setDepartment(blankTo(r.department(), u.getRole() == Role.ACCOUNTANT ? "Finance" : "Engineering"));
        u.setCompany(blankTo(r.company(), "Burmalda"));
        u.setPaypalEmail(email);
        if (u.getRole() == Role.EMPLOYEE) u.setHourlyRate(DEFAULT_RATE);
        users.save(u);

        // У компании должен быть счёт — создаём при первом бухгалтере/сотруднике
        if (accounts.findByCompany(u.getCompany()).isEmpty()) {
            CompanyAccount acc = new CompanyAccount(u.getCompany());
            acc.setBankLabel("Bank account not linked");
            accounts.save(acc);
        }
        return UserDto.from(u);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(Requests.Login r) {
        User u = users.findByEmailIgnoreCase(r.email().trim())
                .filter(x -> encoder.matches(r.password(), x.getPasswordHash()))
                .orElseThrow(() -> Errors.unauthorized("Неверный email или пароль"));
        return new LoginResponse(jwt.issue(u), UserDto.from(u));
    }

    public void logout(AuthUser me) {
        if (me != null) jwt.revoke(me);
    }

    private static String blankTo(String s, String def) {
        return s == null || s.isBlank() ? def : s.trim();
    }
}
