package com.burmalda.util;

import java.security.SecureRandom;

/** Генерация ID в стиле PayPal: 16 символов [0-9A-Z]. */
public final class Ids {

    private static final String CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RND = new SecureRandom();

    private Ids() {
    }

    public static String paypal() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) sb.append(CHARS.charAt(RND.nextInt(CHARS.length())));
        return sb.toString();
    }
}
