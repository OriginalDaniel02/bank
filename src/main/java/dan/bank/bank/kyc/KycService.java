package dan.bank.bank.kyc;

import dan.bank.bank.user.model.User;
import dan.bank.bank.user.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import dan.bank.bank.notification.NotificationService;
import dan.bank.bank.notification.NotificationType;

@Service
public class KycService {

    private static final int MAX_BYTES = 5 * 1024 * 1024;

    private final ObjectProvider<FaceVerificationProvider> providerHolder;
    private final KycVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final NotificationService notifications;

    public KycService(ObjectProvider<FaceVerificationProvider> providerHolder,
                      KycVerificationRepository verificationRepository,
                      UserRepository userRepository,
                      NotificationService notifications) {
        this.providerHolder = providerHolder;
        this.verificationRepository = verificationRepository;
        this.userRepository = userRepository;
        this.notifications = notifications;
    }

    @Transactional
    public KycStatusView verifyFace(long userId, FaceImages images) {
        FaceVerificationProvider provider = providerHolder.getIfAvailable();
        if (provider == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Face verification is not configured");
        }
        validateImages(images);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        FaceVerificationProvider.Result result;
        try {
            result = provider.verify(userId, images);
        } catch (FaceVerificationProvider.UnavailableException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Identity verification isn't available right now. Please try again later");
        }

        KycStatus status = result.approved() ? KycStatus.APPROVED : KycStatus.REJECTED;
        verificationRepository.save(new KycVerification(
                userId, status, provider.name(), result.providerReference(), result.reason()));
        if (result.approved() && user.getKycTier() < 1) {
            user.setKycTier(1);
        }
        notifications.notify(userId, NotificationType.KYC,
                result.approved() ? "Identity verified" : "Verification failed",
                result.approved() ? "Your identity check passed. Your transfer limits have been raised."
                        : "We could not verify your identity. Please try again with a clearer photo.");
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
    /** Checks every captured image really is a JPEG or PNG of a sensible size. */
    public static void validateImages(FaceImages images) {
        validateImage(images.selfie());
        images.liveness().forEach(KycService::validateImage);
    }

    public static void validateImage(byte[] data) {
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
