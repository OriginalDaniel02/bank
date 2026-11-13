package dan.bank.bank.device;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/** Each step commits on its own, so a counted try stays counted even when the face check then fails. */
@Component
class DeviceChallengeStore {

    static final int MAX_ATTEMPTS = 5;

    record Begun(Long challengeId, Long userId, String label) { }

    private final DeviceChallengeRepository challenges;
    private final TrustedDeviceRepository devices;

    DeviceChallengeStore(DeviceChallengeRepository challenges, TrustedDeviceRepository devices) {
        this.challenges = challenges;
        this.devices = devices;
    }

    /** Counts one try against the challenge. Unknown, used, expired and exhausted challenges all look the same. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Begun begin(String secret) {
        DeviceChallenge challenge = challenges.findWithLockByTokenHash(DeviceTokens.hash(secret)).orElseThrow(DeviceChallengeStore::expired);
        if (challenge.isUsed() || challenge.getExpiresAt().isBefore(Instant.now()) || challenge.getAttempts() >= MAX_ATTEMPTS) {
            throw expired();
        }
        challenge.setAttempts(challenge.getAttempts() + 1);
        return new Begun(challenge.getId(), challenge.getUserId(), challenge.getLabel());
    }

    /** Gives a try back, for when the face provider (not the person) failed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refund(long challengeId) {
        challenges.findWithLockById(challengeId).ifPresent(c -> c.setAttempts(Math.max(0, c.getAttempts() - 1)));
    }

    /** Uses the challenge up and trusts the device, in one step, so one passed check can only ever add one device. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeviceService.Enrollment finish(long challengeId, long userId, String label) {
        DeviceChallenge challenge = challenges.findWithLockById(challengeId).orElseThrow(DeviceChallengeStore::expired);
        if (challenge.isUsed()) throw expired();
        challenge.setUsed(true);
        String secret = DeviceTokens.newSecret();
        TrustedDevice device = devices.save(new TrustedDevice(userId, DeviceTokens.hash(secret), label));
        return new DeviceService.Enrollment(device.getId(), secret);
    }

    static ResponseStatusException expired() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "This verification has expired. Please sign in again.");
    }
}
