package dan.bank.bank;

import dan.bank.bank.account.dto.MoneyRequest;
import dan.bank.bank.account.model.AccountStatus;
import dan.bank.bank.account.repository.AccountRepository;
import dan.bank.bank.account.repository.AccountTransactionRepository;
import dan.bank.bank.account.service.AccountService;
import dan.bank.bank.auth.LoginAttemptRepository;
import dan.bank.bank.auth.TokenService;
import dan.bank.bank.auth.repository.RefreshTokenRepository;
import dan.bank.bank.user.model.User;
import dan.bank.bank.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HardeningTests {

    private static final String PASSWORD = "Str0ng-Passw0rd!";

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired AccountTransactionRepository transactionRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired LoginAttemptRepository attemptRepository;
    @Autowired AccountService accountService;
    @Autowired TokenService tokenService;

    @AfterEach
    void clean() {
        attemptRepository.deleteAll();
        transactionRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String signUp() throws Exception {
        String email = "h" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(
                "{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"" + email + "\",\"phoneNumber\":\"08" + Math.abs(email.hashCode() % 1000000000) + "\",\"password\":\"" + PASSWORD + "\",\"termsAccepted\":true}"))
                .andExpect(status().isCreated());
        return email;
    }

    private MvcResult attempt(String identifier, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + identifier + "\",\"password\":\"" + password + "\"}")).andReturn();
    }

    // ===================================================== sign-in lockout

    @Test
    void fiveWrongPasswordsLockSignInEvenForTheRightPassword() throws Exception {
        String email = signUp();
        for (int i = 0; i < 5; i++) assertEquals(401, attempt(email, "wrong-" + i).getResponse().getStatus());
        MvcResult locked = attempt(email, PASSWORD);
        assertEquals(429, locked.getResponse().getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(locked.getResponse().getContentAsString().contains("Please try again in"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void aSuccessfulSignInForgivesEarlierMistakes() throws Exception {
        String email = signUp();
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 4; i++) assertEquals(401, attempt(email, "wrong-" + i).getResponse().getStatus());
            assertEquals(200, attempt(email, PASSWORD).getResponse().getStatus());   // resets the count
        }
    }

    @Test
    void theLockEndsAndOtherAccountsAreNeverAffected() throws Exception {
        String victim = signUp();
        String bystander = signUp();
        for (int i = 0; i < 5; i++) attempt(victim, "wrong-" + i);
        assertEquals(429, attempt(victim, PASSWORD).getResponse().getStatus());
        assertEquals(200, attempt(bystander, PASSWORD).getResponse().getStatus());

        attemptRepository.findAll().forEach(a -> { a.setLockedUntil(Instant.now().minusSeconds(5)); attemptRepository.save(a); });
        assertEquals(200, attempt(victim, PASSWORD).getResponse().getStatus());
    }

    @Test
    void unknownAccountsLookAndBehaveExactlyLikeRealOnes() throws Exception {
        String real = signUp();
        String ghost = "nobody" + UUID.randomUUID() + "@example.com";
        String realBody = attempt(real, "wrong").getResponse().getContentAsString();
        String ghostBody = attempt(ghost, "wrong").getResponse().getContentAsString();
        assertEquals(realBody, ghostBody);                                          // same message, same shape
        for (int i = 0; i < 4; i++) attempt(ghost, "wrong");
        assertEquals(429, attempt(ghost, "wrong").getResponse().getStatus());       // and the same lockout
    }

    @Test
    void failedAttemptsAreStoredHashedNeverAsTheEmailOrPhone() throws Exception {
        attemptRepository.deleteAll();            // other test classes share this in-memory database
        String email = signUp();
        for (int i = 0; i < 5; i++) attempt(email, "wrong-" + i);
        assertEquals(1, attemptRepository.count());
        attemptRepository.findAll().forEach(a -> {
            assertEquals(64, a.getKey().length());
            org.junit.jupiter.api.Assertions.assertTrue(a.getKey().matches("[0-9a-f]{64}"));
            assertNotEquals(email, a.getKey());
        });
    }

    // ================================================== closing an account

    private record Ctx(User user, long accountId, String bearer) { }

    private Ctx userWithAccount() {
        String id = UUID.randomUUID().toString();
        User user = userRepository.save(User.builder().firstName("T").lastName("U").email(id + "@example.com")
                .phoneNumber("0" + Math.abs(id.hashCode())).password("x").build());
        long account = accountService.createAccount(user.getId()).data().id();
        return new Ctx(user, account, "Bearer " + tokenService.createAccessToken(user.getEmail()));
    }

    @Test
    void anAccountWithHistoryCanBeClosedAndItsHistoryIsKept() throws Exception {
        Ctx c = userWithAccount();
        accountService.credit(c.accountId(), new MoneyRequest(new BigDecimal("500.00"), "in"));
        mvc.perform(delete("/api/v1/accounts/" + c.accountId() + "/user/" + c.user().getId()).header("Authorization", c.bearer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(containsString("remaining balance")));
        accountService.debit(c.accountId(), new MoneyRequest(new BigDecimal("500.00"), "out"));

        mvc.perform(delete("/api/v1/accounts/" + c.accountId() + "/user/" + c.user().getId()).header("Authorization", c.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Account closed")).andExpect(jsonPath("$.data.status").value("CLOSED"));
        assertEquals(AccountStatus.CLOSED, accountRepository.findById(c.accountId()).orElseThrow().getStatus());
        assertEquals(2, transactionRepository.count());                              // the record is never destroyed

        mvc.perform(delete("/api/v1/accounts/" + c.accountId() + "/user/" + c.user().getId()).header("Authorization", c.bearer()))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/accounts").header("Authorization", c.bearer())).andExpect(jsonPath("$.data", hasSize(0)));
        mvc.perform(get("/api/v1/dashboard").header("Authorization", c.bearer())).andExpect(jsonPath("$.accounts", hasSize(0)));
        mvc.perform(get("/api/v1/accounts/" + c.accountId() + "/transactions").header("Authorization", c.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void aClosedAccountCannotSendReceiveOrBeReopened() throws Exception {
        Ctx closed = userWithAccount();
        Ctx other = userWithAccount();
        accountService.credit(other.accountId(), new MoneyRequest(new BigDecimal("1000.00"), "seed"));
        mvc.perform(delete("/api/v1/accounts/" + closed.accountId() + "/user/" + closed.user().getId()).header("Authorization", closed.bearer()))
                .andExpect(status().isOk());

        String number = accountRepository.findById(closed.accountId()).orElseThrow().getAccountNumber();
        mvc.perform(get("/api/v1/accounts/lookup/" + number).header("Authorization", other.bearer())).andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class, () ->
                accountService.transfer(new dan.bank.bank.account.dto.TransferRequest(other.accountId(), closed.accountId(), new BigDecimal("10.00"), null)));
        mvc.perform(put("/api/v1/accounts/" + closed.accountId() + "/user/" + closed.user().getId()).header("Authorization", closed.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}")).andExpect(status().isConflict());
        assertEquals(AccountStatus.CLOSED, accountRepository.findById(closed.accountId()).orElseThrow().getStatus());
    }
}
