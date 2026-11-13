package dan.bank.bank.user.dto;

/** One password policy for sign-up and password changes. */
public final class PasswordRules {

    public static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9\\s])\\S{12,64}$";
    public static final String MESSAGE =
            "Password must be 12-64 characters with uppercase, lowercase, a number and a symbol";

    private PasswordRules() { }
}
