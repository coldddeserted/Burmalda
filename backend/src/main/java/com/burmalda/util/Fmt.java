package com.burmalda.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Форматы дат и денег ровно такие, как ждёт фронт. */
public final class Fmt {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TX = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DASH_TX = DateTimeFormatter.ofPattern("dd.MM.yyyy, hh:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MONTH_SHORT = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_LONG = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private Fmt() {
    }

    /** 31.10.2026 */
    public static String date(LocalDate d) {
        return d == null ? "" : d.format(DATE);
    }

    /** 2026-10-05 10:25 — для страницы Transactions */
    public static String tx(LocalDateTime t) {
        return t.format(TX);
    }

    /** 05.10.2026, 10:25 AM — для дашборда бухгалтера */
    public static String dashTx(LocalDateTime t) {
        return t.format(DASH_TX);
    }

    /** 09:05 */
    public static String hm(LocalDateTime t) {
        return t.format(HM);
    }

    /** Длительность в секундах -> "02:10" */
    public static String dur(long seconds) {
        return String.format("%02d:%02d", seconds / 3600, (seconds % 3600) / 60);
    }

    /** Oct */
    public static String monthShort(YearMonth m) {
        return m.format(MONTH_SHORT);
    }

    /** October */
    public static String monthLong(YearMonth m) {
        return m.format(MONTH_LONG);
    }

    /** October 2026 */
    public static String monthYear(YearMonth m) {
        return m.format(MONTH_YEAR);
    }

    /** Деньги: 2 знака после запятой. */
    public static BigDecimal money(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2) : v.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    /** В тысячах с одним знаком: 23500 -> 23.5 (графики на фронте рисуются в $k). */
    public static double thousands(BigDecimal v) {
        return v.divide(BigDecimal.valueOf(1000), 1, RoundingMode.HALF_UP).doubleValue();
    }

    public static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    /** Процент изменения: (cur - prev) / prev * 100, с одним знаком. */
    public static double deltaPct(BigDecimal cur, BigDecimal prev) {
        if (prev == null || prev.signum() == 0) return 0;
        return round1(cur.subtract(prev).doubleValue() / prev.doubleValue() * 100);
    }
}
