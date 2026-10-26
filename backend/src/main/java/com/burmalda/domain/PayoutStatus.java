package com.burmalda.domain;

public enum PayoutStatus {
    SCHEDULED, PAID, FAILED;

    /** "Paid", "Scheduled" — как ждёт фронт. */
    public String api() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
