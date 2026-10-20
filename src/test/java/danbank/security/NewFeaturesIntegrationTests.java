package danbank.security;

import danbank.account.model.Currency;
import danbank.account.repository.AccountRepo;
import danbank.account.repository.AccountTransactionRepository;
import danbank.account.service.AccountServ;
import danbank.address.repository.AddressRepository;
import danbank.auth.TokenService;
import danbank.auth.repository.RefreshTokenRepository;
import danbank.notification.repository.NotificationRepository;
import danbank.user.model.Role;
import danbank.user.model.User;
import danbank.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the features added on top of the original fix pass: TOTP 2FA login,
 * idempotency keys, in-app notifications and the CSV statement export.
 * Lives in danbank.security only so it can call TotpService.generateCode,
 * which is package-private on purpose (no production code should need "give
 * me the current code for this secret").
 */
@SpringBootTest
@AutoConfigureMockMvc
class NewFeaturesIntegrationTests {

    private static final Pattern SECRET_PATTERN = Pattern.compile("\"secret\":\"([A-Z2-7]+)\"");

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private TotpService totpService;
    @Autowired private AccountServ accountService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AccountRepo accountRepository;
    @Autowired private AccountTransactionRepository transactionRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User alice;
    private User admin;
    private long aliceAccountId;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        transactionRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        accountRepository.deleteAll();
        addressRepository.deleteAll();
        userRepository.deleteAll();

        alice = createUser();
        admin = createUser();
        jdbcTemplate.update("UPDATE users SET role = ? WHERE id = ?", Role.ADMIN.name(), admin.getId());
        aliceAccountId = accountService.createAccount(alice.getId(), Currency.NGN).data().id();
    }

    @Test
    void creditingAnAccountCreatesANotificationForTheOwner() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/" + aliceAccountId + "/credit")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 25.00, \"description\": \"test\"}"))
                .andExpect(status().isCreated());

        // Notifications are delivered off-thread (see NotificationEventListener), so
        // the row may not exist the instant the HTTP response comes back.
        awaitCondition(() -> notificationRepository.findByUser_IdOrderByCreatedAtDesc(
                alice.getId(), org.springframework.data.domain.PageRequest.of(0, 10)
        ).getTotalElements() == 1);

        mockMvc.perform(get("/api/v1/users/" + alice.getId() + "/notifications")
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].type").value("TRANSACTION"));
    }

    private void awaitCondition(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition was not met within the timeout");
    }

    @Test
    void repeatingACreditWithTheSameIdempotencyKeyDoesNotDoubleCredit() throws Exception {
        String body = "{\"amount\": 40.00, \"description\": \"payroll\", \"idempotencyKey\": \"pay-2026-01\"}";

        mockMvc.perform(post("/api/v1/accounts/" + aliceAccountId + "/credit")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/accounts/" + aliceAccountId + "/credit")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reference").value("pay-2026-01"));

        assertEquals(new BigDecimal("40.00"), accountRepository.findById(aliceAccountId).orElseThrow().getBalance());
        assertEquals(1, transactionRepository.count());
    }

    @Test
    void statementCsvContainsTheHeaderAndEachTransaction() throws Exception {
        mockMvc.perform(post("/api/v1/accounts/" + aliceAccountId + "/credit")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 15.00, \"description\": \"deposit\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/accounts/" + aliceAccountId + "/transactions/statement.csv")
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(content -> {
                    String csv = content.getResponse().getContentAsString();
                    assertTrue(csv.startsWith("date,type,reference,transfer_group,amount,description"));
                    assertTrue(csv.contains("CREDIT"));
                    assertTrue(csv.contains("15.00"));
                });
    }

    @Test
    void loginRequiresTotpCodeOnceTwoFactorIsEnabledAndSucceedsWithTheRightOne() throws Exception {
        String secret = enableTwoFactorForAlice();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + alice.getEmail() + "\", \"password\": \"Str0ng@Pass\"}"))
                .andExpect(status().is(428));

        String validCode = totpService.generateCode(secret, Instant.now().getEpochSecond() / 30);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + alice.getEmail() + "\", \"password\": \"Str0ng@Pass\", \"totpCode\": \"" + validCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void loginFailsWithTheWrongTotpCode() throws Exception {
        enableTwoFactorForAlice();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + alice.getEmail() + "\", \"password\": \"Str0ng@Pass\", \"totpCode\": \"000000\"}"))
                .andExpect(status().isUnauthorized());
    }

    /** Drives the real setup -> enable flow through the HTTP API and returns the raw secret. */
    private String enableTwoFactorForAlice() throws Exception {
        String setupJson = mockMvc.perform(post("/api/v1/users/" + alice.getId() + "/2fa/setup")
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Matcher matcher = SECRET_PATTERN.matcher(setupJson);
        assertTrue(matcher.find(), "Expected a base32 secret in: " + setupJson);
        String secret = matcher.group(1);

        String code = totpService.generateCode(secret, Instant.now().getEpochSecond() / 30);
        mockMvc.perform(post("/api/v1/users/" + alice.getId() + "/2fa/enable")
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"" + code + "\"}"))
                .andExpect(status().isNoContent());

        return secret;
    }

    private String bearer(User user) {
        return "Bearer " + tokenService.createAccessToken(user.getEmail());
    }

    private User createUser() {
        String unique = UUID.randomUUID().toString();
        return userRepository.save(User.builder()
                .firstName("Alice")
                .lastName("Test")
                .email(unique + "@example.com")
                .phoneNumber(String.format("0801%07d", Math.abs(unique.hashCode()) % 10_000_000))
                .bvnHash(unique)
                .bvnLast4("1234")
                .dateOfBirth(LocalDate.of(2000, 1, 1))
                .passwordHash(passwordEncoder.encode("Str0ng@Pass"))
                .build());
    }
}
