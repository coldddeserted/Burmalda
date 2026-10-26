package com.burmalda.service;

import com.burmalda.domain.User;
import com.burmalda.repo.UserRepository;
import com.burmalda.security.AuthUser;
import com.burmalda.util.Errors;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    /** Текущий пользователь из БД по токену. */
    public User require(AuthUser me) {
        if (me == null) throw Errors.unauthorized("Нужно войти в систему");
        return users.findById(me.id()).orElseThrow(() -> Errors.unauthorized("Пользователь не найден"));
    }
}
