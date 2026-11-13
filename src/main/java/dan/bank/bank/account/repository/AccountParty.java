package dan.bank.bank.account.repository;

/** Read-only view of who owns an account; lets callers avoid loading entities (and lazy proxies). */
public interface AccountParty {
    Long getId();
    Long getUserId();
    String getAccountName();
    String getAccountNumber();
}
