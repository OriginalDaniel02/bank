package dan.bank.bank.deposit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Paystack signs every webhook: x-paystack-signature = HMAC-SHA512(secret key, raw request body), hex. */
final class PaystackSignature {

    private PaystackSignature() { }

    static String sign(String secretKey, byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(rawBody));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA512 is not available", e);
        }
    }

    /** Constant-time comparison, so the check does not leak how much of a forged signature was right. */
    static boolean isValid(String secretKey, byte[] rawBody, String signatureHeader) {
        if (secretKey == null || secretKey.isBlank() || signatureHeader == null) return false;
        byte[] expected = sign(secretKey, rawBody).getBytes(StandardCharsets.UTF_8);
        byte[] given = signatureHeader.strip().toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, given);
    }
}
