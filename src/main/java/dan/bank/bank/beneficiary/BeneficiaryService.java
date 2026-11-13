package dan.bank.bank.beneficiary;

import dan.bank.bank.account.model.Account;
import dan.bank.bank.account.model.AccountStatus;
import dan.bank.bank.account.repository.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class BeneficiaryService {

    private static final int MAX_BENEFICIARIES = 100;

    private final BeneficiaryRepository repository;
    private final AccountRepository accountRepository;

    public BeneficiaryService(BeneficiaryRepository repository, AccountRepository accountRepository) {
        this.repository = repository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public BeneficiaryDto add(long userId, String accountNumber, String nickname) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account is not active");
        }
        if (account.getUser().getId() == userId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot save your own account as a beneficiary");
        }
        if (repository.existsByUserIdAndAccountNumber(userId, accountNumber)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Beneficiary already saved");
        }
        if (repository.findByUserIdOrderByCreatedAtDesc(userId).size() >= MAX_BENEFICIARIES) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Beneficiary limit reached");
        }
        String cleanNickname = nickname == null || nickname.isBlank() ? null : nickname.strip();
        return toDto(repository.save(new Beneficiary(userId, accountNumber, account.getAccountName(), cleanNickname)));
    }

    @Transactional(readOnly = true)
    public List<BeneficiaryDto> list(long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toDto).toList();
    }

    @Transactional
    public void delete(long userId, long beneficiaryId) {
        Beneficiary beneficiary = repository.findByIdAndUserId(beneficiaryId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Beneficiary not found"));
        repository.delete(beneficiary);
    }

    private BeneficiaryDto toDto(Beneficiary b) {
        return new BeneficiaryDto(b.getId(), b.getAccountNumber(), b.getAccountName(), b.getNickname());
    }

    public record BeneficiaryDto(Long id, String accountNumber, String accountName, String nickname) { }
}
