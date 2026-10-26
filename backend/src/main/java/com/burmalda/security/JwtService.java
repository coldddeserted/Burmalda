package com.burmalda.security;

import com.burmalda.domain.Role;
import com.burmalda.domain.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Минимальный JWT (HS256) без сторонних библиотек — чтобы было видно, как он устроен:
 * base64url(header) + "." + base64url(payload) + "." + base64url(HMAC-SHA256(подписи)).
 */
@Service
public class JwtService {

    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();
    private static final String HEADER = ENC.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final byte[] secret;
    private final long ttlSeconds;
    private final ObjectMapper mapper;

    /** Токены, отозванные через /auth/logout: tokenId -> когда истекает. */
    private final Map<String, Long> revoked = new ConcurrentHashMap<>();

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.ttl-hours:12}") long ttlHours,
                      ObjectMapper mapper) {
        if (secret.length() < 32) {
            throw new IllegalStateException("app.jwt.secret должен быть минимум 32 символа");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlHours * 3600;
        this.mapper = mapper;
    }

    public String issue(User user) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", String.valueOf(user.getId()));
        payload.put("email", user.getEmail());
        payload.put("role", user.getRole().name());
        payload.put("jti", UUID.randomUUID().toString());
        payload.put("iat", now);
        payload.put("exp", now + ttlSeconds);
        try {
            String body = ENC.encodeToString(mapper.writeValueAsBytes(payload));
            String unsigned = HEADER + "." + body;
            return unsigned + "." + ENC.encodeToString(sign(unsigned));
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось создать токен", e);
        }
    }

    /** Пусто, если токен битый, просрочен или отозван. */
    public Optional<AuthUser> verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3 || !parts[0].equals(HEADER)) return Optional.empty();

            byte[] expected = sign(parts[0] + "." + parts[1]);
            byte[] actual = DEC.decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) return Optional.empty();

            JsonNode p = mapper.readTree(DEC.decode(parts[1]));
            long exp = p.path("exp").asLong(0);
            if (exp < Instant.now().getEpochSecond()) return Optional.empty();

            String jti = p.path("jti").asText();
            if (revoked.containsKey(jti)) return Optional.empty();

            return Optional.of(new AuthUser(
                    Long.parseLong(p.path("sub").asText()),
                    p.path("email").asText(),
                    Role.valueOf(p.path("role").asText()),
                    jti));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void revoke(AuthUser user) {
        long now = Instant.now().getEpochSecond();
        revoked.put(user.tokenId(), now + ttlSeconds);
        revoked.values().removeIf(until -> until < now); // чистим старое, чтобы мапа не росла
    }

    private byte[] sign(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }
}
