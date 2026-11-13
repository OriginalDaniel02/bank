package dan.bank.bank.deposit;

/** The card-payment provider (Paystack). Kept as an interface so the rest of the code never depends on one vendor. */
public interface PaymentGateway {

    /** False until a secret key is configured; deposits are switched off while it is false. */
    boolean isConfigured();

    /** Starts a payment and returns the page the customer must be sent to. Amount is in kobo. */
    InitializedPayment initialize(String reference, String customerEmail, long amountKobo, String callbackUrl);

    /** Asks the provider what really happened to a payment. Never trust the browser for this. */
    PaymentCheck verify(String reference);

    record InitializedPayment(String authorizationUrl) { }

    enum Outcome { SUCCESS, FAILED, PENDING }

    record PaymentCheck(Outcome outcome, long amountKobo, String currency) { }

    class GatewayException extends RuntimeException {
        public GatewayException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
