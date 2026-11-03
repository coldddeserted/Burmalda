package com.burmalda.domain;

public enum Priority {
    HIGH, MID, LOW;

    public String api() {
        return name().toLowerCase();
    }
}
