package dan.bank.bank.Kyc;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * DEVELOPMENT ONLY. Approves any valid image without checking a face or liveness.
 * Active only with kyc.face.provider=stub. Never enable in production.
 */
@Component
@ConditionalOnProperty(name = "kyc.face.provider", havingValue = "stub")
public class StubFaceVerificationProvider implements FaceVerificationProvider {

    @Override
    public String name() {
        return "stub";
    }

    @Override
    public Result verify(long userId, byte[] selfieImage) {
        return new Result(true, "stub-" + userId, "Stub provider: no real face check performed");
    }
}
