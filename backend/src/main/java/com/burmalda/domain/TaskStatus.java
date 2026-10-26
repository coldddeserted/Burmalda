package com.burmalda.domain;

public enum TaskStatus {
    TODO, PROGRESS, DONE;

    public String api() {
        return name().toLowerCase();
    }

    public static TaskStatus from(String s) {
        if (s == null) throw new IllegalArgumentException("status is required");
        return switch (s.trim().toLowerCase()) {
            case "todo" -> TODO;
            case "progress" -> PROGRESS;
            case "done" -> DONE;
            default -> throw new IllegalArgumentException("Unknown status: " + s);
        };
    }
}
