package dan.bank.bank.kyc;

/**
 * Port for a face-verification vendor (liveness + match against the person's ID photo), e.g.
 * Smile ID, Youverify, Dojah or AWS Rekognition. Implementations must not keep the image.
 */
public interface FaceVerificationProvider {

    String name();

    /** Checks a selfie (and that the person is live) and records it as this user's enrolled face. */
    Result verify(long userId, FaceImages images);

    /** Checks a selfie against the face this user enrolled earlier, e.g. when they sign in on a new phone. */
    Result authenticate(long userId, FaceImages images);

    record Result(boolean approved, String providerReference, String reason) { }

    /** The vendor could not be reached or answered with something we cannot trust. Never means "approved". */
    class UnavailableException extends RuntimeException {
        public UnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
