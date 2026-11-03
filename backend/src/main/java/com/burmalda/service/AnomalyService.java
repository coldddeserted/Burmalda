package com.burmalda.service;

import com.burmalda.domain.PaypalTransaction;
import com.burmalda.domain.TxDirection;
import com.burmalda.domain.TxStatus;
import com.burmalda.repo.PaypalTransactionRepository;
import com.burmalda.util.Fmt;
import com.burmalda.web.dto.AccountantViews.AnomalyRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "AI Risk Alerts": find suspicious outgoing company payments.
 *  1) Duplicate: same payee and same amount within 3 days.
 *  2) Unusual amount: a payment 2x above this payee's average (needs ≥2 past payments).
 */
@Service
@Transactional(readOnly = true)
public class AnomalyService {

    private static final int LOOKBACK_DAYS = 45;
    private static final double UNUSUAL_RATIO = 2.0;

    private final PaypalTransactionRepository txs;

    public AnomalyService(PaypalTransactionRepository txs) {
        this.txs = txs;
    }

    public List<AnomalyRow> detect(String company) {
        List<PaypalTransaction> all = txs.findByCompanyAndOwnerIsNullOrderByDateTimeDesc(company).stream()
                .filter(t -> t.getDirection() == TxDirection.OUT && t.getStatus() == TxStatus.COMPLETED)
                .toList();
        LocalDateTime since = LocalDateTime.now().minusDays(LOOKBACK_DAYS);
        List<AnomalyRow> res = new ArrayList<>();
        Set<String> flagged = new HashSet<>();

        // 1) duplicates
        for (int i = 0; i < all.size(); i++) {
            PaypalTransaction a = all.get(i);
            if (a.getDateTime().isBefore(since)) continue;
            for (int j = i + 1; j < all.size(); j++) {
                PaypalTransaction b = all.get(j);
                boolean same = a.getName().equalsIgnoreCase(b.getName()) && a.getAmount().compareTo(b.getAmount()) == 0;
                long hours = Math.abs(Duration.between(a.getDateTime(), b.getDateTime()).toHours());
                if (same && hours <= 72 && flagged.add(a.getId())) {
                    res.add(new AnomalyRow(a.getId(), "Duplicate vendor payment",
                            a.getName() + " was charged twice on " + Fmt.date(a.getDateTime().toLocalDate()) + ".",
                            a.getAmount(), "High"));
                }
            }
        }

        // 2) unusual amount
        for (PaypalTransaction t : all) {
            if (t.getDateTime().isBefore(since) || flagged.contains(t.getId())) continue;
            List<BigDecimal> others = all.stream()
                    .filter(o -> o != t && o.getName().equalsIgnoreCase(t.getName()))
                    .map(PaypalTransaction::getAmount)
                    .toList();
            if (others.size() < 2) continue;
            double avg = others.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
            double ratio = avg > 0 ? t.getAmount().doubleValue() / avg : 0;
            if (ratio >= UNUSUAL_RATIO && flagged.add(t.getId())) {
                res.add(new AnomalyRow(t.getId(), "Unusual transfer amount",
                        t.getName() + ": payment is " + String.format(java.util.Locale.ENGLISH, "%.1f", ratio)
                                + "x above the average for this payee.",
                        t.getAmount(), ratio >= 3 ? "Critical" : "High"));
            }
        }
        return res;
    }
}
