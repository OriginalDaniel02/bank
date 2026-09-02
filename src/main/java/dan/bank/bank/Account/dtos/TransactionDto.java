package dan.bank.bank.Account.dtos;
import java.math.BigDecimal;
import java.time.Instant;
public record TransactionDto(
        String reference,
        String type,
        long accountId,
        BigDecimal amount,
        String description,
        Instant createdAt) { }
