package dan.bank.bank.account.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneId;

/**
 * Transfer limits and fees. Fees are only charged when an income account is configured
 * (bank.fees.income-account-number), so money is never charged without somewhere to credit it.
 * Tiers: amount up to tier1Max pays tier1Fee, up to tier2Max pays tier2Fee, above pays tier3Fee.
 */
@Component
public class TransferPolicy {

    private final BigDecimal perTransferMax;
    private final BigDecimal dailyMax;
    private final ZoneId zone;
    private final String incomeAccountNumber;
    private final BigDecimal tier1Max;
    private final BigDecimal tier1Fee;
    private final BigDecimal tier2Max;
    private final BigDecimal tier2Fee;
    private final BigDecimal tier3Fee;
    private final BigDecimal tier0PerTransferMax;
    private final BigDecimal tier0DailyMax;

    public TransferPolicy(
            @Value("${bank.limits.per-transfer:10000000.00}") BigDecimal perTransferMax,
            @Value("${bank.limits.daily-transfer:50000000.00}") BigDecimal dailyMax,
            @Value("${bank.limits.zone:Africa/Lagos}") String zone,
            @Value("${bank.fees.income-account-number:}") String incomeAccountNumber,
            @Value("${bank.fees.tier1-max:5000.00}") BigDecimal tier1Max,
            @Value("${bank.fees.tier1-fee:10.00}") BigDecimal tier1Fee,
            @Value("${bank.fees.tier2-max:50000.00}") BigDecimal tier2Max,
            @Value("${bank.fees.tier2-fee:25.00}") BigDecimal tier2Fee,
            @Value("${bank.fees.tier3-fee:50.00}") BigDecimal tier3Fee,
            @Value("${bank.limits.tier0-per-transfer:10000000.00}") BigDecimal tier0PerTransferMax,
            @Value("${bank.limits.tier0-daily-transfer:50000000.00}") BigDecimal tier0DailyMax) {
        this.perTransferMax = perTransferMax;
        this.dailyMax = dailyMax;
        this.zone = ZoneId.of(zone);
        this.incomeAccountNumber = incomeAccountNumber;
        this.tier1Max = tier1Max;
        this.tier1Fee = tier1Fee;
        this.tier2Max = tier2Max;
        this.tier2Fee = tier2Fee;
        this.tier3Fee = tier3Fee;
        this.tier0PerTransferMax = tier0PerTransferMax;
        this.tier0DailyMax = tier0DailyMax;
    }

    /** No fees and effectively no limits; used where a policy is not injected. */
    public static TransferPolicy unrestricted() {
        return new TransferPolicy(new BigDecimal("1000000000.00"),
                new BigDecimal("1000000000.00"), "UTC", "",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("1000000000.00"), new BigDecimal("1000000000.00"));
    }

    /** Unverified (tier 0) users get the stricter limits; anyone verified gets the standard ones. */
    public BigDecimal perTransferMaxFor(int kycTier) { return kycTier < 1 ? tier0PerTransferMax.min(perTransferMax) : perTransferMax; }
    public BigDecimal dailyMaxFor(int kycTier) { return kycTier < 1 ? tier0DailyMax.min(dailyMax) : dailyMax; }
    public ZoneId zone() { return zone; }
    public String incomeAccountNumber() { return incomeAccountNumber; }
    public boolean feesEnabled() { return incomeAccountNumber != null && !incomeAccountNumber.isBlank(); }

    public BigDecimal feeFor(BigDecimal amount) {
        if (!feesEnabled()) return BigDecimal.ZERO.setScale(2);
        BigDecimal fee = amount.compareTo(tier1Max) <= 0 ? tier1Fee
                : amount.compareTo(tier2Max) <= 0 ? tier2Fee : tier3Fee;
        return fee.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
