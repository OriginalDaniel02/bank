package danbank.account.dtos;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionDto(
        String reference,
        String transferGroup,
        String type,
        long accountId,
        BigDecimal amount,
        String description,
        Instant createdAt
) {
}
