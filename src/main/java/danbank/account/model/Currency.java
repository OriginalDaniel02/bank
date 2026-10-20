package danbank.account.model;

/**
 * Currencies an account can be opened in. Transfers are only allowed between
 * accounts that share a currency - this codebase does not fabricate FX rates,
 * so cross-currency conversion is deliberately out of scope.
 */
public enum Currency {
    NGN,
    USD,
    GBP,
    EUR
}
