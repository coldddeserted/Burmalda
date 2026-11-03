package com.burmalda.domain;

public enum PayoutStatus {
    SCHEDULED, PAID, FAILED;

    /** "Paid", "Scheduled" — as the frontend expects. */
    public String api() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
