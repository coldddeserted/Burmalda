package com.burmalda.domain;

public enum TxStatus {
    COMPLETED, PENDING, FAILED;

    public String api() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
