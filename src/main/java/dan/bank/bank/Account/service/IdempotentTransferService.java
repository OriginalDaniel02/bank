package dan.bank.bank.Account.service;

import dan.bank.bank.Account.dtos.AccountResponse;
import dan.bank.bank.Account.dtos.TransactionDto;
import dan.bank.bank.Account.dtos.TransferRequest;
import dan.bank.bank.Account.model.IdempotencyKey;
import dan.bank.bank.Account.model.TransactionType;
import dan.bank.bank.Account.repository.AccountTransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Makes transfers safe to retry. A client sends the same Idempotency-Key with the same body and
 * gets the original result back instead of moving money twice.
 */
@Service
public class IdempotentTransferService {

    private final AccountServ accountService;
    private final IdempotencyStore store;
    private final AccountTransactionRepository transactionRepository;

    public IdempotentTransferService(AccountServ accountService, IdempotencyStore store,
                                     AccountTransactionRepository transactionRepository) {
        this.accountService = accountService;
        this.store = store;
        this.transactionRepository = transactionRepository;
    }

    public AccountResponse<List<TransactionDto>> transfer(long userId, String key, TransferRequest request) {
        if (key == null || key.isBlank()) {
            return accountService.transfer(request);
        }
        if (key.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key is too long");
        }
        String hash = hash(request);
        try {
            store.claim(userId, key, hash);
        } catch (DataIntegrityViolationException alreadyClaimed) {
            return replay(userId, key, hash);
        }
        try {
            AccountResponse<List<TransactionDto>> response = accountService.transfer(request);
            String reference = response.data().get(0).reference();
            store.complete(userId, key, reference.substring(0, reference.lastIndexOf('-')));
            return response;
        } catch (RuntimeException failure) {
            store.release(userId, key); // a failed transfer may be retried with the same key
            throw failure;
        }
    }

    @Transactional(readOnly = true)
    AccountResponse<List<TransactionDto>> replay(long userId, String key, String hash) {
        IdempotencyKey existing = store.find(userId, key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Request is being processed"));
        if (!existing.getRequestHash().equals(hash)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Idempotency-Key was already used with a different request");
        }
        if (existing.getTransferId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request is being processed");
        }
        List<TransactionDto> entries = transactionRepository.findByReferenceStartingWith(existing.getTransferId())
                .stream()
                .filter(t -> t.getType() == TransactionType.TRANSFER_OUT || t.getType() == TransactionType.TRANSFER_IN)
                .map(t -> new TransactionDto(t.getReference(), t.getType().name(), t.getAccount().getId(),
                        t.getAmount(), t.getDescription(), t.getCreatedAt()))
                .sorted(Comparator.comparing((TransactionDto t) -> !t.type().equals("TRANSFER_OUT")))
                .toList();
        return new AccountResponse<>("Transfer completed", entries);
    }

    private static String hash(TransferRequest r) {
        String canonical = r.fromAccountId() + "|" + r.toAccountId() + "|" + r.amount().toPlainString()
                + "|" + (r.description() == null ? "" : r.description());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
