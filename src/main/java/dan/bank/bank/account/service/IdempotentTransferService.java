package dan.bank.bank.account.service;

import dan.bank.bank.account.dto.AccountResponse;
import dan.bank.bank.account.dto.TransactionDto;
import dan.bank.bank.account.dto.TransferRequest;
import dan.bank.bank.account.model.IdempotencyKey;
import dan.bank.bank.account.model.TransactionType;
import dan.bank.bank.account.repository.AccountTransactionRepository;
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
import dan.bank.bank.notification.TransferNotifier;

/**
 * Makes transfers safe to retry. A client sends the same Idempotency-Key with the same body and
 * gets the original result back instead of moving money twice.
 */
@Service
public class IdempotentTransferService {

    private final AccountService accountService;
    private final IdempotencyStore store;
    private final AccountTransactionRepository transactionRepository;
    private final TransferNotifier notifier;

    public IdempotentTransferService(AccountService accountService, IdempotencyStore store,
                                     AccountTransactionRepository transactionRepository,
                                     TransferNotifier notifier) {
        this.accountService = accountService;
        this.store = store;
        this.transactionRepository = transactionRepository;
        this.notifier = notifier;
    }

    public AccountResponse<List<TransactionDto>> transfer(long userId, String key, TransferRequest request) {
        if (key == null || key.isBlank()) {
            AccountResponse<List<TransactionDto>> response = accountService.transfer(request);
            notifier.transferCompleted(request, response.data().get(0).amount());
            return response;
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

        AccountResponse<List<TransactionDto>> response;
        try {
            response = accountService.transfer(request);
        } catch (RuntimeException failure) {
            store.release(userId, key); // nothing moved, so the same key may be retried
            throw failure;
        }
        // From here on the money has moved. Nothing below may release the key, or a retry would
        // transfer again. If recording the result fails, the key stays claimed (retries get 409).
        String reference = response.data().get(0).reference();
        store.complete(userId, key, reference.substring(0, reference.lastIndexOf('-')));
        notifier.transferCompleted(request, response.data().get(0).amount());
        return response;
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
