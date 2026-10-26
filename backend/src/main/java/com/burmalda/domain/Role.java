package com.burmalda.domain;

/** Роль пользователя. Во фронте приходит строкой в нижнем регистре: "employee" / "accountant". */
public enum Role {
    EMPLOYEE, ACCOUNTANT;

    public String api() {
        return name().toLowerCase();
    }

    public static Role from(String s) {
        if (s == null) return EMPLOYEE;
        return "accountant".equalsIgnoreCase(s.trim()) ? ACCOUNTANT : EMPLOYEE;
    }
}
