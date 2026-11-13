package dan.bank.bank.notification;

import dan.bank.bank.account.dto.TransferRequest;
import dan.bank.bank.account.repository.AccountParty;
import dan.bank.bank.account.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;

/**
 * Sends debit/credit alerts after money has moved.
 *
 * <p>Deliberately NOT transactional and never throws: it runs after the transfer has committed, so a
 * notification problem must not turn a successful transfer into an error or undo its idempotency
 * record. Each notification is saved in its own transaction by {@link NotificationService}.
 */
@Component
public class TransferNotifier {

    private static final Logger log = LoggerFactory.getLogger(TransferNotifier.class);
    private static final String CURRENCY = "NGN";

    private final AccountRepository accountRepository;
    private final NotificationService notifications;

    public TransferNotifier(AccountRepository accountRepository, NotificationService notifications) {
        this.accountRepository = accountRepository;
        this.notifications = notifications;
    }

    public void transferCompleted(TransferRequest request, BigDecimal amount) {
        try {
            AccountParty from = accountRepository.findPartyById(request.fromAccountId()).orElse(null);
            AccountParty to = accountRepository.findPartyById(request.toAccountId()).orElse(null);
            if (from == null || to == null) return;
            String money = money(amount);
            String note = request.description() == null || request.description().isBlank()
                    ? "" : " (" + request.description() + ")";
            send(from.getUserId(), NotificationType.DEBIT, "Debit alert",
                    money + " sent to " + to.getAccountName() + " " + mask(to.getAccountNumber()) + note);
            send(to.getUserId(), NotificationType.CREDIT, "Credit alert",
                    money + " received from " + from.getAccountName() + " " + mask(from.getAccountNumber()) + note);
        } catch (RuntimeException e) {
            log.warn("Could not create transfer notifications", e);
        }
    }

    public void adminFunding(long accountId, BigDecimal amount, boolean credit) {
        try {
            AccountParty account = accountRepository.findPartyById(accountId).orElse(null);
            if (account == null) return;
            send(account.getUserId(), credit ? NotificationType.CREDIT : NotificationType.DEBIT,
                    credit ? "Credit alert" : "Debit alert",
                    money(amount) + (credit ? " credited to " : " debited from ") + mask(account.getAccountNumber()));
        } catch (RuntimeException e) {
            log.warn("Could not create funding notification", e);
        }
    }

    public void depositCompleted(long accountId, BigDecimal amount) {
        try {
            AccountParty account = accountRepository.findPartyById(accountId).orElse(null);
            if (account == null) return;
            send(account.getUserId(), NotificationType.CREDIT, "Deposit successful",
                    money(amount) + " was added to " + mask(account.getAccountNumber()) + " by card");
        } catch (RuntimeException e) {
            log.warn("Could not create deposit notification", e);
        }
    }

    /** One failed notification must not stop the other. */
    private void send(Long userId, NotificationType type, String title, String message) {
        try {
            notifications.notify(userId, type, title, message);
        } catch (RuntimeException e) {
            log.warn("Could not save {} notification for user {}", type, userId, e);
        }
    }

    static String money(BigDecimal amount) {
        return CURRENCY + " " + new DecimalFormat("#,##0.00").format(amount);
    }

    static String mask(String accountNumber) {
        return "(••••" + accountNumber.substring(Math.max(0, accountNumber.length() - 4)) + ")";
    }
}
