package danbank;

import danbank.account.dtos.MoneyRequest;
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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationIntegrationTests {

    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger(5_000_000);

    @Autowired private MockMvc mockMvc;
    @Autowired private TokenService tokenService;
    @Autowired private AccountServ accountService;
    @Autowired private AccountRepo accountRepository;
    @Autowired private AccountTransactionRepository transactionRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private NotificationRepository notificationRepository;

    private User alice;
    private User bob;
    private User admin;
    private long aliceAccountId;
    private long bobAccountId;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        transactionRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        accountRepository.deleteAll();
        addressRepository.deleteAll();
        userRepository.deleteAll();

        alice = createUser();
        bob = createUser();
        admin = createUser();
        jdbcTemplate.update("UPDATE users SET role = ? WHERE id = ?", Role.ADMIN.name(), admin.getId());

        aliceAccountId = accountService.createAccount(alice.getId(), Currency.NGN).data().id();
        bobAccountId = accountService.createAccount(bob.getId(), Currency.NGN).data().id();
        accountService.credit(aliceAccountId, new MoneyRequest(new BigDecimal("100.00"), "seed", null));
    }

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + aliceAccountId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerCanReadOwnAccountButNotSomeoneElses() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + aliceAccountId).header("Authorization", bearer(alice)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/accounts/" + bobAccountId).header("Authorization", bearer(alice)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/accounts/" + bobAccountId + "/transactions").header("Authorization", bearer(alice)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listingAccountsOnlyReturnsTheCallersAccounts() throws Exception {
        mockMvc.perform(get("/api/v1/accounts").header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(aliceAccountId));
    }

    @Test
    void onlyAdminsCanListEveryAccount() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/all").header("Authorization", bearer(alice)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/accounts/all").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    void adminCanReadAnyAccount() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/" + bobAccountId).header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void onlyAdminsCanCreditOrDebitDirectly() throws Exception {
        String body = "{\"amount\": 10.00, \"description\": \"free money\"}";

        mockMvc.perform(post("/api/v1/accounts/" + aliceAccountId + "/credit")
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/accounts/" + aliceAccountId + "/debit")
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/accounts/" + aliceAccountId + "/credit")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void cannotTransferOutOfSomeoneElsesAccount() throws Exception {
        String stealFromAlice = transferBody(aliceAccountId, bobAccountId);
        mockMvc.perform(post("/api/v1/accounts/transfer")
                        .header("Authorization", bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON).content(stealFromAlice))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/accounts/transfer")
                        .header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content(stealFromAlice))
                .andExpect(status().isCreated());
    }

    @Test
    void userCreatesAccountForThemselvesOnly() throws Exception {
        mockMvc.perform(post("/api/v1/accounts").header("Authorization", bearer(bob)))
                .andExpect(status().isCreated());
        org.junit.jupiter.api.Assertions.assertEquals(2, accountRepository.findByUser_Id(bob.getId()).size());
    }

    @Test
    void userCannotDeleteAnotherUsersAccount() throws Exception {
        mockMvc.perform(delete("/api/v1/accounts/" + bobAccountId).header("Authorization", bearer(alice)))
                .andExpect(status().isNotFound());
    }

    @Test
    void userEndpointsAreRestrictedToSelfOrAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/users").header("Authorization", bearer(alice)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/" + alice.getId()).header("Authorization", bearer(alice)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/" + bob.getId()).header("Authorization", bearer(alice)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users/" + bob.getId()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/users/" + bob.getId()).header("Authorization", bearer(alice)))
                .andExpect(status().isForbidden());
    }

    @Test
    void passwordCanOnlyBeChangedByTheOwner() throws Exception {
        String body = "{\"currentPassword\": \"x\", \"newPassword\": \"Passw0rd!x\"}";
        mockMvc.perform(patch("/api/v1/users/" + bob.getId() + "/password")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void addressEndpointsAreRestrictedToSelfOrAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + bob.getId() + "/address/history").header("Authorization", bearer(alice)))
                .andExpect(status().isForbidden());
    }

    private String transferBody(long from, long to) {
        return "{\"fromAccountId\": " + from + ", \"toAccountId\": " + to
                + ", \"amount\": 5.00, \"description\": \"test\"}";
    }

    private String bearer(User user) {
        return "Bearer " + tokenService.createAccessToken(user.getEmail());
    }

    private User createUser() {
        String unique = UUID.randomUUID().toString();
        return userRepository.save(User.builder()
                .firstName("Test")
                .lastName("User")
                .email(unique + "@example.com")
                .phoneNumber(String.format("0801%07d", PHONE_SEQUENCE.getAndIncrement()))
                .bvnHash(unique)
                .bvnLast4("1234")
                .dateOfBirth(LocalDate.of(2000, 1, 1))
                .passwordHash("not-used")
                .build());
    }
}
