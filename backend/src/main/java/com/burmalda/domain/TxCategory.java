package com.burmalda.domain;

/** Категория транзакции + бейдж и цвет для списка на дашборде бухгалтера. */
public enum TxCategory {
    PAYROLL("PR", "#5b7aa8"),
    VENDOR("VN", "#7a4f8f"),
    EXPENSE("EX", "#c98a2b"),
    TAX("TX", "#566073"),
    TRANSFER("XB", "#2f7d6a"),
    FUNDING("FD", "#3d7a3d"),
    CLIENT("CL", "#2b6cb0"),
    REFUND("RF", "#8a6d3b"),
    SALARY("SL", "#5b7aa8"),
    WITHDRAWAL("WD", "#6b7280");

    public final String badge;
    public final String color;

    TxCategory(String badge, String color) {
        this.badge = badge;
        this.color = color;
    }
}
