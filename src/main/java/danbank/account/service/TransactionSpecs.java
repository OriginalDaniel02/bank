package danbank.account.service;

import danbank.account.model.AccountTransaction;
import danbank.account.model.TransactionType;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

final class TransactionSpecs {

    private TransactionSpecs() {
    }

    static Specification<AccountTransaction> matching(long accountId, TransactionType type, Instant from, Instant to) {
        return (root, query, cb) -> {
            var predicate = cb.equal(root.get("account").get("id"), accountId);
            if (type != null) {
                predicate = cb.and(predicate, cb.equal(root.get("type"), type));
            }
            if (from != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), from));
            }
            if (to != null) {
                predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.<Instant>get("createdAt"), to));
            }
            return predicate;
        };
    }
}
