package dan.bank.bank.Account.service;

import dan.bank.bank.Account.model.IdempotencyKey;
import dan.bank.bank.Account.repository.IdempotencyKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Each call commits on its own, so a claim is visible to concurrent retries immediately. */
@Service
public class IdempotencyStore {

    private final IdempotencyKeyRepository repository;

    public IdempotencyStore(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    /** Throws DataIntegrityViolationException if (user, key) is already claimed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void claim(long userId, String key, String requestHash) {
        repository.saveAndFlush(new IdempotencyKey(userId, key, requestHash));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(long userId, String key, String transferId) {
        repository.findByUserIdAndKeyValue(userId, key).ifPresent(k -> k.setTransferId(transferId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(long userId, String key) {
        repository.findByUserIdAndKeyValue(userId, key).ifPresent(repository::delete);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<IdempotencyKey> find(long userId, String key) {
        return repository.findByUserIdAndKeyValue(userId, key);
    }
}
