package dan.bank.bank.Kyc;

import dan.bank.bank.User.model.User;
import dan.bank.bank.User.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class KycService {

    private static final int MAX_BYTES = 5 * 1024 * 1024;

    private final ObjectProvider<FaceVerificationProvider> providerHolder;
    private final KycVerificationRepository verificationRepository;
    private final UserRepository userRepository;

    public KycService(ObjectProvider<FaceVerificationProvider> providerHolder,
                      KycVerificationRepository verificationRepository,
                      UserRepository userRepository) {
        this.providerHolder = providerHolder;
        this.verificationRepository = verificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public KycStatusView verifyFace(long userId, byte[] selfie) {
        FaceVerificationProvider provider = providerHolder.getIfAvailable();
        if (provider == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Face verification is not configured");
        }
        validateImage(selfie);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        FaceVerificationProvider.Result result = provider.verify(userId, selfie);

        KycStatus status = result.approved() ? KycStatus.APPROVED : KycStatus.REJECTED;
        verificationRepository.save(new KycVerification(
                userId, status, provider.name(), result.providerReference(), result.reason()));
        if (result.approved() && user.getKycTier() < 1) {
            user.setKycTier(1);
        }
        return new KycStatusView(user.getKycTier(), status.name(), result.reason());
    }

    @Transactional(readOnly = true)
    public KycStatusView status(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return verificationRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(v -> new KycStatusView(user.getKycTier(), v.getStatus().name(), v.getReason()))
                .orElse(new KycStatusView(user.getKycTier(), "NOT_STARTED", null));
    }

    /** Accepts only real JPEG/PNG data (checked by magic bytes, not the client-supplied type). */
    static void validateImage(byte[] data) {
        if (data == null || data.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selfie image is required");
        }
        if (data.length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Image must be 5 MB or smaller");
        }
        boolean jpeg = data.length > 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF;
        boolean png = data.length > 8 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G';
        if (!jpeg && !png) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only JPEG or PNG images are accepted");
        }
    }

    public record KycStatusView(int tier, String faceStatus, String reason) { }
}
