package com.burmalda.domain;

/** User role. The frontend sends it in lower case: "employee" / "accountant". */
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
