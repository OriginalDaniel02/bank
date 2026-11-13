package dan.bank.bank.device;

import dan.bank.bank.account.repository.AccountRepository;
import dan.bank.bank.account.repository.AccountTransactionRepository;
import dan.bank.bank.auth.LoginAttemptRepository;
import dan.bank.bank.auth.repository.RefreshTokenRepository;
import dan.bank.bank.kyc.FaceImages;
import dan.bank.bank.kyc.FaceVerificationProvider;
import dan.bank.bank.notification.NotificationRepository;
import dan.bank.bank.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** New-device sign-in with a face provider configured (a controllable fake). */
@SpringBootTest
@AutoConfigureMockMvc
class DeviceTests {

    static final String PASSWORD = "Str0ng-Passw0rd!";
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0};

    static class FakeFace implements FaceVerificationProvider {
        volatile boolean approve = true;
        volatile boolean unavailable = false;
        volatile int authenticateCalls = 0;
        volatile int lastLivenessFrames = -1;

        public String name() { return "fake"; }
        public Result verify(long userId, FaceImages images) { return new Result(true, "v", null); }
        public Result authenticate(long userId, FaceImages images) {
            authenticateCalls++;
            lastLivenessFrames = images.liveness().size();
            if (unavailable) throw new UnavailableException("down", null);
            return new Result(approve, "a", approve ? null : "no match");
        }
    }

    @TestConfiguration
    static class Config {
        @Bean @Primary FakeFace fakeFace() { return new FakeFace(); }
    }

    @Autowired MockMvc mvc;
    @Autowired FakeFace face;
    @Autowired UserRepository userRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired AccountTransactionRepository transactionRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired LoginAttemptRepository attemptRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired TrustedDeviceRepository deviceRepository;
    @Autowired DeviceChallengeRepository challengeRepository;

    @BeforeEach
    void reset() { face.approve = true; face.unavailable = false; face.authenticateCalls = 0; }

    @AfterEach
    void clean() {
        challengeRepository.deleteAll();
        attemptRepository.deleteAll();
        notificationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        deviceRepository.deleteAll();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ------------------------------------------------------------- helpers

    private String signUp() throws Exception {
        String email = "d" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(
                "{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"" + email + "\",\"phoneNumber\":\"08" + Math.abs(email.hashCode() % 1000000000)
                        + "\",\"password\":\"" + PASSWORD + "\",\"termsAccepted\":true}")).andExpect(status().isCreated());
        return email;
    }

    private MvcResult login(String email, String deviceToken, String password) throws Exception {
        var request = post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0) Chrome/120.0 Safari/537.36")
                .content("{\"identifier\":\"" + email + "\",\"password\":\"" + password + "\"}");
        if (deviceToken != null) request.header("X-Device-Token", deviceToken);
        return mvc.perform(request).andReturn();
    }

    private static String field(MvcResult result, String name) throws Exception {
        Matcher m = Pattern.compile("\"" + name + "\":\"([^\"]+)\"").matcher(result.getResponse().getContentAsString());
        return m.find() ? m.group(1) : null;
    }

    private MvcResult verify(String challenge, int livenessFrames, byte[] selfieBytes) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/auth/device/verify");
        request.file(new MockMultipartFile("selfie", "selfie.png", "image/png", selfieBytes));
        for (int i = 0; i < livenessFrames; i++) request.file(new MockMultipartFile("liveness", "f" + i + ".png", "image/png", PNG));
        return mvc.perform(request.param("challengeToken", challenge)).andReturn();
    }

    /** Signs up, signs in on a first device, and returns {email, deviceToken, accessToken}. */
    private String[] userWithFirstDevice() throws Exception {
        String email = signUp();
        MvcResult first = login(email, null, PASSWORD);
        assertEquals(200, first.getResponse().getStatus());
        return new String[]{email, field(first, "deviceToken"), field(first, "accessToken")};
    }

    /** Adds a second device through a passed face check; returns {deviceToken, accessToken, refreshToken}. */
    private String[] addSecondDevice(String email) throws Exception {
        MvcResult challenge = login(email, null, PASSWORD);
        MvcResult passed = verify(field(challenge, "challengeToken"), 7, PNG);
        assertEquals(200, passed.getResponse().getStatus());
        return new String[]{field(passed, "deviceToken"), field(passed, "accessToken"), field(passed, "refreshToken")};
    }

    // ------------------------------------------------------------------ tests

    @Test
    void theFirstDeviceIsTrustedAutomaticallyAndShowsAsCurrent() throws Exception {
        String[] user = userWithFirstDevice();
        assertNotNull(user[1]);
        mvc.perform(get("/api/v1/devices").header("Authorization", "Bearer " + user[2]).header("X-Device-Token", user[1]))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].current").value(true)).andExpect(jsonPath("$[0].label").value("Chrome on Windows"));
    }

    @Test
    void aTrustedDeviceSignsInAgainWithoutAnyChallenge() throws Exception {
        String[] user = userWithFirstDevice();
        MvcResult again = login(user[0], user[1], PASSWORD);
        assertEquals(200, again.getResponse().getStatus());
        assertNull(field(again, "deviceToken"));                   // nothing new to remember
        assertEquals(1, deviceRepository.count());
        assertEquals(0, face.authenticateCalls);
    }

    @Test
    void aNewDeviceCannotSignInOnAPasswordAloneAndTheOwnerIsAlerted() throws Exception {
        String[] user = userWithFirstDevice();
        MvcResult challenge = login(user[0], null, PASSWORD);
        assertEquals(202, challenge.getResponse().getStatus());
        assertEquals("DEVICE_VERIFICATION_REQUIRED", field(challenge, "status"));
        assertNotNull(field(challenge, "challengeToken"));
        assertNull(field(challenge, "accessToken"));               // no session until the face check passes
        assertNull(field(challenge, "refreshToken"));
        assertEquals(1, deviceRepository.count());
        assertEquals(1, notificationRepository.findAll().stream().filter(n -> n.getTitle().equals("New device needs verification")).count());
    }

    @Test
    void passingTheFaceCheckTrustsTheDeviceAndTheChallengeWorksOnlyOnce() throws Exception {
        String[] user = userWithFirstDevice();
        MvcResult challenge = login(user[0], null, PASSWORD);
        String secret = field(challenge, "challengeToken");

        MvcResult passed = verify(secret, 7, PNG);
        assertEquals(200, passed.getResponse().getStatus());
        assertNotNull(field(passed, "accessToken"));
        String newDevice = field(passed, "deviceToken");
        assertNotNull(newDevice);
        assertEquals(7, face.lastLivenessFrames);                  // the liveness frames reach the provider
        assertEquals(2, deviceRepository.count());

        assertEquals(401, verify(secret, 7, PNG).getResponse().getStatus());   // single use
        assertEquals(2, deviceRepository.count());
        assertEquals(200, login(user[0], newDevice, PASSWORD).getResponse().getStatus());   // now trusted: no challenge
    }

    @Test
    void aFailedFaceCheckIsRefusedAndTheChallengeRunsOutOfTries() throws Exception {
        String[] user = userWithFirstDevice();
        String secret = field(login(user[0], null, PASSWORD), "challengeToken");
        face.approve = false;
        for (int i = 0; i < 5; i++) assertEquals(403, verify(secret, 7, PNG).getResponse().getStatus());
        face.approve = true;                                       // even a perfect face is too late now
        assertEquals(401, verify(secret, 7, PNG).getResponse().getStatus());
        assertEquals(1, deviceRepository.count());
        assertEquals(5, face.authenticateCalls);
    }

    @Test
    void aFaceProviderOutageIsA503AndDoesNotUseUpTheTries() throws Exception {
        String[] user = userWithFirstDevice();
        String secret = field(login(user[0], null, PASSWORD), "challengeToken");
        face.unavailable = true;
        for (int i = 0; i < 8; i++) assertEquals(503, verify(secret, 7, PNG).getResponse().getStatus());   // more than the 5 tries
        face.unavailable = false;
        assertEquals(200, verify(secret, 7, PNG).getResponse().getStatus());
    }

    @Test
    void notAnImageIsRefusedWithoutCostingATry() throws Exception {
        String[] user = userWithFirstDevice();
        String secret = field(login(user[0], null, PASSWORD), "challengeToken");
        for (int i = 0; i < 7; i++) assertEquals(415, verify(secret, 7, "<script>alert(1)</script>".getBytes()).getResponse().getStatus());
        assertEquals(0, face.authenticateCalls);
        assertEquals(200, verify(secret, 7, PNG).getResponse().getStatus());
    }

    @Test
    void unknownAndExpiredChallengesAreRefused() throws Exception {
        String[] user = userWithFirstDevice();
        assertEquals(401, verify("not-a-real-challenge", 7, PNG).getResponse().getStatus());
        String secret = field(login(user[0], null, PASSWORD), "challengeToken");
        challengeRepository.findAll().forEach(c -> { c.setExpiresAt(Instant.now().minusSeconds(5)); challengeRepository.save(c); });
        assertEquals(401, verify(secret, 7, PNG).getResponse().getStatus());
        assertEquals(1, deviceRepository.count());
    }

    @Test
    void aWrongPasswordNeverRevealsThatTheAccountHasDevicesOrStartsAChallenge() throws Exception {
        String[] user = userWithFirstDevice();
        MvcResult wrong = login(user[0], null, "wrong-password");
        assertEquals(401, wrong.getResponse().getStatus());
        assertEquals(0, challengeRepository.count());
    }

    @Test
    void anotherPersonsDeviceTokenDoesNotMakeMyDeviceTrusted() throws Exception {
        String[] mine = userWithFirstDevice();
        String[] theirs = userWithFirstDevice();
        MvcResult attempt = login(mine[0], theirs[1], PASSWORD);   // my password, their device secret
        assertEquals(202, attempt.getResponse().getStatus());
    }

    @Test
    void removingADeviceSignsItOutAndUntrustsIt() throws Exception {
        String[] user = userWithFirstDevice();
        String[] second = addSecondDevice(user[0]);               // {deviceToken, accessToken, refreshToken}
        String listAuth = "Bearer " + user[2];

        mvc.perform(get("/api/v1/devices").header("Authorization", listAuth).header("X-Device-Token", user[1]))
                .andExpect(jsonPath("$", hasSize(2)));
        long secondId = deviceRepository.findAll().stream().filter(d -> !d.getTokenHash().equals(DeviceTokens.hash(user[1]))).findFirst().orElseThrow().getId();

        String stranger = "Bearer " + userWithFirstDevice()[2];
        mvc.perform(delete("/api/v1/devices/" + secondId).header("Authorization", stranger)).andExpect(status().isNotFound());   // not theirs
        mvc.perform(delete("/api/v1/devices/" + secondId).header("Authorization", listAuth)).andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + second[2] + "\"}")).andExpect(status().isUnauthorized());        // signed out
        assertEquals(202, login(user[0], second[0], PASSWORD).getResponse().getStatus());                           // untrusted again
        mvc.perform(delete("/api/v1/devices/" + secondId).header("Authorization", listAuth)).andExpect(status().isNotFound());
    }

    @Test
    void changingThePasswordKeepsThisDeviceSignedInAndTrustedAndAlertsTheOwner() throws Exception {
        String[] user = userWithFirstDevice();
        MvcResult changed = mvc.perform(post("/api/v1/users/me/password").header("Authorization", "Bearer " + user[2]).header("X-Device-Token", user[1])
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"An0ther-Str0ng-Pass!\"}")).andExpect(status().isOk()).andReturn();
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + field(changed, "refreshToken") + "\"}")).andExpect(status().isOk());
        assertEquals(200, login(user[0], user[1], "An0ther-Str0ng-Pass!").getResponse().getStatus());   // still a trusted device
        assertEquals(1, notificationRepository.findAll().stream().filter(n -> n.getTitle().equals("Password changed")).count());
    }
}
