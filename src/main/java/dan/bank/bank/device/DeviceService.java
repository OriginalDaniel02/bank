package dan.bank.bank.device;

import dan.bank.bank.auth.repository.RefreshTokenRepository;
import dan.bank.bank.kyc.FaceVerificationProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Which devices may sign in to an account.
 * <ul>
 *   <li>The first device ever used is trusted automatically (there is nothing to compare against yet).</li>
 *   <li>Any later, unknown device must pass a face check first, when a face provider is configured.</li>
 *   <li>Without a face provider the new device is allowed but the owner is alerted (weaker, and documented).</li>
 * </ul>
 */
@Service
public class DeviceService {

    public static final Duration CHALLENGE_LIFETIME = Duration.ofMinutes(10);

    public enum Decision { TRUSTED, ENROLL_FIRST, CHALLENGE_REQUIRED, UNVERIFIED_ALLOWED }

    public record Check(Decision decision, Long deviceId) { }

    public record Enrollment(Long deviceId, String deviceToken) { }

    public record View(Long id, String label, java.time.Instant createdAt, java.time.Instant lastUsedAt, boolean current) { }

    private final TrustedDeviceRepository devices;
    private final DeviceChallengeRepository challenges;
    private final RefreshTokenRepository refreshTokens;
    private final ObjectProvider<FaceVerificationProvider> faceProvider;

    public DeviceService(TrustedDeviceRepository devices, DeviceChallengeRepository challenges,
                         RefreshTokenRepository refreshTokens, ObjectProvider<FaceVerificationProvider> faceProvider) {
        this.devices = devices;
        this.challenges = challenges;
        this.refreshTokens = refreshTokens;
        this.faceProvider = faceProvider;
    }

    public boolean faceCheckAvailable() {
        return faceProvider.getIfAvailable() != null;
    }

    @Transactional
    public Check check(long userId, String deviceToken) {
        if (deviceToken != null && !deviceToken.isBlank()) {
            var known = devices.findByTokenHashAndUserIdAndRevokedFalse(DeviceTokens.hash(deviceToken), userId);
            if (known.isPresent()) {
                known.get().setLastUsedAt(Instant.now());
                return new Check(Decision.TRUSTED, known.get().getId());
            }
        }
        if (devices.countByUserIdAndRevokedFalse(userId) == 0) return new Check(Decision.ENROLL_FIRST, null);
        return new Check(faceCheckAvailable() ? Decision.CHALLENGE_REQUIRED : Decision.UNVERIFIED_ALLOWED, null);
    }

    @Transactional
    public Enrollment enroll(long userId, String label) {
        String secret = DeviceTokens.newSecret();
        TrustedDevice device = devices.save(new TrustedDevice(userId, DeviceTokens.hash(secret), label));
        return new Enrollment(device.getId(), secret);
    }

    /** Returns the secret to hand to the client; it is the only time it exists in the clear. */
    @Transactional
    public String createChallenge(long userId, String label) {
        String secret = DeviceTokens.newSecret();
        challenges.save(new DeviceChallenge(userId, DeviceTokens.hash(secret), label, Instant.now().plus(CHALLENGE_LIFETIME)));
        return secret;
    }

    @Transactional(readOnly = true)
    public Long activeDeviceId(long userId, String deviceToken) {
        if (deviceToken == null || deviceToken.isBlank()) return null;
        return devices.findByTokenHashAndUserIdAndRevokedFalse(DeviceTokens.hash(deviceToken), userId).map(TrustedDevice::getId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<View> list(long userId, String currentToken) {
        Long currentId = activeDeviceId(userId, currentToken);
        return devices.findByUserIdAndRevokedFalseOrderByLastUsedAtDesc(userId).stream()
                .map(d -> new View(d.getId(), d.getLabel(), d.getCreatedAt(), d.getLastUsedAt(), d.getId().equals(currentId))).toList();
    }

    /** Removes a device and signs it out: every session issued to it stops working. */
    @Transactional
    public void revoke(long userId, long deviceId) {
        TrustedDevice device = devices.findByIdAndUserId(deviceId, userId)
                .filter(d -> !d.isRevoked())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));
        device.setRevoked(true);
        refreshTokens.revokeAllForDevice(deviceId);
    }
}
