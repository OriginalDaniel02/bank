package dan.bank.bank.kyc;

/**
 * Port for a face-verification vendor (liveness + match against the person's ID photo), e.g.
 * Smile ID, Youverify, Dojah or AWS Rekognition. Implementations must not keep the image.
 */
public interface FaceVerificationProvider {

    String name();

    Result verify(long userId, byte[] selfieImage);

    record Result(boolean approved, String providerReference, String reason) { }
}
