package com.burmalda.web;

import com.burmalda.service.LlmClient;
import com.burmalda.service.PaypalClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/** Проверка, что сервер жив, и в каком режиме PayPal / AI: GET /api/health */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final PaypalClient paypal;
    private final LlmClient llm;

    public HealthController(PaypalClient paypal, LlmClient llm) {
        this.paypal = paypal;
        this.llm = llm;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "time", Instant.now().toString(),
                "paypal", paypal.enabled() ? "sandbox" : "simulated",
                "ai", llm.enabled() ? "llm" : "rules");
    }
}
