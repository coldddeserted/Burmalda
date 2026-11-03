package com.burmalda.domain;

public enum TxDirection {
    IN, OUT;

    public String api() {
        return name().toLowerCase();
    }
}
