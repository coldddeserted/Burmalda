package com.burmalda.web;

import com.burmalda.security.AuthUser;
import com.burmalda.service.AuthService;
import com.burmalda.service.UserService;
import com.burmalda.web.dto.CommonViews.LoginResponse;
import com.burmalda.web.dto.CommonViews.Ok;
import com.burmalda.web.dto.CommonViews.RegisterResponse;
import com.burmalda.web.dto.CommonViews.UserDto;
import com.burmalda.web.dto.Requests;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final UserService users;

    public AuthController(AuthService auth, UserService users) {
        this.auth = auth;
        this.users = users;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody Requests.Login body) {
        return auth.login(body);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody Requests.Register body) {
        return new RegisterResponse(auth.register(body));
    }

    @PostMapping("/logout")
    public Ok logout(@AuthenticationPrincipal AuthUser me) {
        auth.logout(me);
        return Ok.OK;
    }

    /** Кто я — удобно проверять токен. */
    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AuthUser me) {
        return UserDto.from(users.require(me));
    }
}
