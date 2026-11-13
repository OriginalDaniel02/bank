package dan.bank.bank.device;

import dan.bank.bank.account.repository.AccountRepository;
import dan.bank.bank.auth.LoginAttemptRepository;
import dan.bank.bank.auth.repository.RefreshTokenRepository;
import dan.bank.bank.notification.NotificationRepository;
import dan.bank.bank.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** With no face provider configured, a new device cannot be verified, so it is allowed but the owner is alerted. */
@SpringBootTest
@AutoConfigureMockMvc
class DevicesWithoutProviderTests {

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired LoginAttemptRepository attemptRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired TrustedDeviceRepository deviceRepository;
    @Autowired dan.bank.bank.account.repository.AccountTransactionRepository transactionRepository;

    @AfterEach
    void clean() {
        attemptRepository.deleteAll();
        notificationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        deviceRepository.deleteAll();
        transactionRepository.deleteAll();   // other test classes share this in-memory database
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void aSecondDeviceIsAllowedButTheOwnerIsAlerted() throws Exception {
        String email = "w" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(
                "{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"" + email + "\",\"phoneNumber\":\"08" + Math.abs(email.hashCode() % 1000000000)
                        + "\",\"password\":\"Str0ng-Passw0rd!\",\"termsAccepted\":true}"));
        String body = "{\"identifier\":\"" + email + "\",\"password\":\"Str0ng-Passw0rd!\"}";
        assertEquals(200, mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus());
        assertEquals(200, mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus());
        assertEquals(2, deviceRepository.count());
        assertEquals(1, notificationRepository.findAll().stream().filter(n -> n.getTitle().equals("New device signed in")).count());
    }

    @Test
    void theFaceCheckEndpointIsUnavailableWithoutAProvider() throws Exception {
        assertEquals(503, mvc.perform(multipart("/api/v1/auth/device/verify")
                .file(new MockMultipartFile("selfie", "s.png", "image/png", DeviceTests.PNG)).param("challengeToken", "x")).andReturn().getResponse().getStatus());
    }
}
