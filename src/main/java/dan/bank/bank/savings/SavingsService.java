package dan.bank.bank.savings;

import dan.bank.bank.account.model.TransactionType;
import dan.bank.bank.account.service.AccountService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Savings goals. Saved money is taken out of the account (a SAVINGS_OUT ledger entry) and put back on withdrawal
 * (SAVINGS_IN), so balances and statements always add up. Locks are always taken goal first, then account.
 */
@Service
public class SavingsService {

    private static final int MAX_ACTIVE_GOALS = 20;
    private static final BigDecimal MIN_TARGET = new BigDecimal("1000.00");
    private static final BigDecimal MIN_MOVE = new BigDecimal("100.00");

    private final SavingsGoalRepository goals;
    private final AccountService accounts;
    private final ZoneId zone;

    public SavingsService(SavingsGoalRepository goals, AccountService accounts,
                          @Value("${bank.limits.zone:Africa/Lagos}") String zone) {
        this.goals = goals;
        this.accounts = accounts;
        this.zone = ZoneId.of(zone);
    }

    public record GoalView(Long id, String name, String type, BigDecimal targetAmount, BigDecimal savedAmount,
                           LocalDate deadline, int progressPercent, boolean withdrawalsLocked, String status, Instant createdAt) { }

    public record Summary(List<GoalView> goals, BigDecimal totalSaved) { }

    @Transactional
    public GoalView create(long userId, String name, SavingsType type, BigDecimal target, LocalDate deadline) {
        String cleanName = name == null ? "" : name.strip();
        if (cleanName.isEmpty()) throw bad("Give your goal a name");
        BigDecimal targetAmount = money(target);
        if (targetAmount.compareTo(MIN_TARGET) < 0) throw bad("The target must be at least " + MIN_TARGET.toPlainString());
        if (type == SavingsType.LOCKED && (deadline == null || !deadline.isAfter(today()))) {
            throw bad("A locked goal needs a target date in the future");
        }
        if (deadline != null && !deadline.isAfter(today())) throw bad("The target date must be in the future");
        if (goals.countByUserIdAndStatus(userId, SavingsStatus.ACTIVE) >= MAX_ACTIVE_GOALS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You can have up to " + MAX_ACTIVE_GOALS + " active goals");
        }
        return view(goals.save(new SavingsGoal(userId, cleanName, type, targetAmount, deadline)));
    }

    @Transactional(readOnly = true)
    public Summary list(long userId) {
        List<GoalView> views = goals.findByUserIdAndStatusOrderByCreatedAtDesc(userId, SavingsStatus.ACTIVE)
                .stream().map(this::view).toList();
        BigDecimal total = views.stream().map(GoalView::savedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Summary(views, total);
    }

    @Transactional
    public GoalView deposit(long userId, long goalId, long accountId, BigDecimal amount) {
        BigDecimal value = moveAmount(amount);
        SavingsGoal goal = lockActive(userId, goalId);
        accounts.requireOwnedBy(userId, accountId);
        accounts.moveOut(accountId, value, TransactionType.SAVINGS_OUT, "Savings: " + goal.getName(), "SAV-" + UUID.randomUUID());
        goal.setSavedAmount(goal.getSavedAmount().add(value));
        return view(goal);
    }

    /** PIN must already have been verified by the caller. */
    @Transactional
    public GoalView withdraw(long userId, long goalId, long accountId, BigDecimal amount) {
        BigDecimal value = moveAmount(amount);
        SavingsGoal goal = lockActive(userId, goalId);
        requireUnlocked(goal);
        if (value.compareTo(goal.getSavedAmount()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You only have " + goal.getSavedAmount().toPlainString() + " saved in this goal");
        }
        accounts.requireOwnedBy(userId, accountId);
        accounts.moveIn(accountId, value, TransactionType.SAVINGS_IN, "From savings: " + goal.getName(), "SAV-" + UUID.randomUUID());
        goal.setSavedAmount(goal.getSavedAmount().subtract(value));
        return view(goal);
    }

    /** Pays out whatever is saved to the chosen account, then closes the goal. PIN must already be verified. */
    @Transactional
    public GoalView close(long userId, long goalId, long accountId) {
        SavingsGoal goal = lockActive(userId, goalId);
        if (goal.getSavedAmount().signum() > 0) {
            requireUnlocked(goal);
            accounts.requireOwnedBy(userId, accountId);
            accounts.moveIn(accountId, goal.getSavedAmount(), TransactionType.SAVINGS_IN,
                    "Closed savings: " + goal.getName(), "SAV-" + UUID.randomUUID());
            goal.setSavedAmount(BigDecimal.ZERO.setScale(2));
        }
        goal.setStatus(SavingsStatus.CLOSED);
        goal.setClosedAt(Instant.now());
        return view(goal);
    }

    @Transactional
    public GoalView changeTarget(long userId, long goalId, BigDecimal target) {
        BigDecimal targetAmount = money(target);
        if (targetAmount.compareTo(MIN_TARGET) < 0) throw bad("The target must be at least " + MIN_TARGET.toPlainString());
        SavingsGoal goal = lockActive(userId, goalId);
        goal.setTargetAmount(targetAmount);
        return view(goal);
    }

    // ---------------------------------------------------------------- helpers

    private SavingsGoal lockActive(long userId, long goalId) {
        SavingsGoal goal = goals.findWithLockByIdAndUserId(goalId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Savings goal not found"));
        if (goal.getStatus() != SavingsStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This savings goal is closed");
        }
        return goal;
    }

    private boolean isLocked(SavingsGoal goal) {
        return goal.getType() == SavingsType.LOCKED && goal.getDeadline() != null && goal.getDeadline().isAfter(today());
    }

    private void requireUnlocked(SavingsGoal goal) {
        if (isLocked(goal)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This goal is locked until " + goal.getDeadline());
        }
    }

    private LocalDate today() {
        return LocalDate.now(zone);
    }

    private GoalView view(SavingsGoal g) {
        int percent = g.getTargetAmount().signum() == 0 ? 0
                : Math.min(100, g.getSavedAmount().multiply(BigDecimal.valueOf(100)).divide(g.getTargetAmount(), 0, RoundingMode.DOWN).intValue());
        return new GoalView(g.getId(), g.getName(), g.getType().name(), g.getTargetAmount(), g.getSavedAmount(),
                g.getDeadline(), percent, isLocked(g), g.getStatus().name(), g.getCreatedAt());
    }

    private BigDecimal moveAmount(BigDecimal amount) {
        BigDecimal value = money(amount);
        if (value.compareTo(MIN_MOVE) < 0) throw bad("The least you can move is " + MIN_MOVE.toPlainString());
        return value;
    }

    private static BigDecimal money(BigDecimal amount) {
        if (amount == null) throw bad("Enter an amount");
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException tooManyDecimals) {
            throw bad("Amount can have at most 2 decimal places");
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
