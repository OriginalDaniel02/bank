package dan.bank.bank.device;

import dan.bank.bank.kyc.FaceImages;
import dan.bank.bank.kyc.FaceVerificationProvider;
import dan.bank.bank.kyc.KycService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Completes a new-device sign-in. Not transactional on purpose: it calls the face provider over the network, and
 * that must never happen while a database transaction is open.
 */
@Service
public class DeviceVerification {

    public record Passed(Long userId, DeviceService.Enrollment enrollment) { }

    private final DeviceChallengeStore store;
    private final ObjectProvider<FaceVerificationProvider> faceProvider;

    public DeviceVerification(DeviceChallengeStore store, ObjectProvider<FaceVerificationProvider> faceProvider) {
        this.store = store;
        this.faceProvider = faceProvider;
    }

    public Passed verify(String challengeSecret, FaceImages images) {
        FaceVerificationProvider provider = faceProvider.getIfAvailable();
        if (provider == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Face verification is not available right now");
        }
        KycService.validateImages(images);
        DeviceChallengeStore.Begun begun = store.begin(challengeSecret);
        FaceVerificationProvider.Result result;
        try {
            result = provider.authenticate(begun.userId(), images);
        } catch (FaceVerificationProvider.UnavailableException e) {
            store.refund(begun.challengeId());          // an outage on their side must not use up the person's tries
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Face verification isn't available right now. Please try again in a moment");
        }
        if (!result.approved()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "We couldn't match your face. Please try again in good light.");
        }
        return new Passed(begun.userId(), store.finish(begun.challengeId(), begun.userId(), begun.label()));
    }
}
