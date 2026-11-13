package dan.bank.bank;

import dan.bank.bank.account.dto.MoneyRequest;
import dan.bank.bank.account.dto.TransferRequest;
import dan.bank.bank.account.repository.AccountRepository;
import dan.bank.bank.account.repository.AccountTransactionRepository;
import dan.bank.bank.account.service.AccountService;
import dan.bank.bank.auth.TokenService;
import dan.bank.bank.auth.repository.RefreshTokenRepository;
import dan.bank.bank.savings.SavingsGoalRepository;
import dan.bank.bank.savings.SavingsService;
import dan.bank.bank.savings.SavingsType;
import dan.bank.bank.user.model.User;
import dan.bank.bank.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SavingsInsightsPasswordTests {

    private static final String OLD_PASSWORD = "Str0ng-Passw0rd!";
    private static final String NEW_PASSWORD = "An0ther-Str0ng-Pass!";

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired AccountTransactionRepository transactionRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired SavingsGoalRepository goalRepository;
    @Autowired AccountService accountService;
    @Autowired SavingsService savingsService;
    @Autowired TokenService tokenService;
    @Autowired PasswordEncoder encoder;

    private User alice;
    private User bob;
    private long aliceAccount;
    private long bobAccount;

    @BeforeEach
    void setUp() {
        clean();
        alice = newUser(true);
        bob = newUser(false);
        aliceAccount = accountService.createAccount(alice.getId()).data().id();
        bobAccount = accountService.createAccount(bob.getId()).data().id();
        accountService.credit(aliceAccount, new MoneyRequest(new BigDecimal("10000.00"), "seed"));
    }

    @AfterEach
    void clean() {
        goalRepository.deleteAll();
        transactionRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User newUser(boolean withPin) {
        String id = UUID.randomUUID().toString();
        User user = User.builder().firstName("T").lastName("U").email(id + "@example.com")
                .phoneNumber("0" + Math.abs(id.hashCode())).password(encoder.encode(OLD_PASSWORD)).build();
        if (withPin) user.setTransactionPinHash(encoder.encode("1234"));
        return userRepository.save(user);
    }

    private String bearer(User u) { return "Bearer " + tokenService.createAccessToken(u.getEmail()); }
    private BigDecimal balance(long id) { return accountRepository.findById(id).orElseThrow().getBalance(); }
    private LocalDate today() { return LocalDate.now(ZoneId.of("Africa/Lagos")); }

    private long goal(User user, String type, String deadline) throws Exception {
        String body = "{\"name\":\"Rainy day\",\"type\":\"" + type + "\",\"targetAmount\":5000" + (deadline == null ? "" : ",\"deadline\":\"" + deadline + "\"") + "}";
        MvcResult r = mvc.perform(post("/api/v1/savings").header("Authorization", bearer(user)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(r.getResponse().getContentAsString());
        if (!m.find()) throw new AssertionError("no id");
        return Long.parseLong(m.group(1));
    }

    private String move(long accountId, String amount) { return "{\"accountId\":" + accountId + ",\"amount\":" + amount + "}"; }

    // ============================================================== password

    private String signUp(String email, String phone) throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(
                "{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"" + email + "\",\"phoneNumber\":\"" + phone
                        + "\",\"password\":\"" + OLD_PASSWORD + "\",\"termsAccepted\":true}")).andExpect(status().isCreated());
        return loginBody(email, OLD_PASSWORD);
    }

    private String loginBody(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + email + "\",\"password\":\"" + password + "\"}")).andReturn().getResponse().getContentAsString();
    }

    private static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\":\"([^\"]+)\"").matcher(json);
        if (!m.find()) throw new AssertionError("missing " + name + " in " + json);
        return m.group(1);
    }

    @Test
    void changingPasswordChecksTheOldOneSignsOutOtherDevicesAndKeepsThisOneIn() throws Exception {
        String email = "pw" + UUID.randomUUID() + "@example.com";
        String first = signUp(email, "0802" + Math.abs(email.hashCode() % 10000000));
        String otherDeviceRefresh = field(loginBody(email, OLD_PASSWORD), "refreshToken");
        String token = "Bearer " + field(first, "accessToken");

        mvc.perform(post("/api/v1/users/me/password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"" + NEW_PASSWORD + "\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users/me/password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + OLD_PASSWORD + "\",\"newPassword\":\"weak\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.newPassword").exists());
        mvc.perform(post("/api/v1/users/me/password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + OLD_PASSWORD + "\",\"newPassword\":\"" + OLD_PASSWORD + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/users/me/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"x\",\"newPassword\":\"" + NEW_PASSWORD + "\"}")).andExpect(status().isUnauthorized());

        MvcResult changed = mvc.perform(post("/api/v1/users/me/password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + OLD_PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty()).andReturn();

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + email + "\",\"password\":\"" + OLD_PASSWORD + "\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + email + "\",\"password\":\"" + NEW_PASSWORD + "\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + otherDeviceRefresh + "\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + field(changed.getResponse().getContentAsString(), "refreshToken") + "\"}")).andExpect(status().isOk());
    }

    // =============================================================== savings

    @Test
    void goalValidation() throws Exception {
        String[] bad = {
                "{\"name\":\" \",\"type\":\"FLEXIBLE\",\"targetAmount\":5000}",
                "{\"name\":\"x\",\"type\":\"FLEXIBLE\",\"targetAmount\":500}",
                "{\"name\":\"x\",\"type\":\"LOCKED\",\"targetAmount\":5000}",
                "{\"name\":\"x\",\"type\":\"LOCKED\",\"targetAmount\":5000,\"deadline\":\"" + today().minusDays(1) + "\"}",
                "{\"name\":\"x\",\"type\":\"FLEXIBLE\",\"targetAmount\":5000.005}"};
        for (String body : bad) {
            mvc.perform(post("/api/v1/savings").header("Authorization", bearer(alice)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, goalRepository.count());
    }

    @Test
    void savingMovesMoneyOutOfTheAccountAndNeverCreatesOrLosesAny() throws Exception {
        long goalId = goal(alice, "FLEXIBLE", null);
        mvc.perform(post("/api/v1/savings/" + goalId + "/deposit").header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content(move(aliceAccount, "2500.00")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.savedAmount").value(2500.0)).andExpect(jsonPath("$.progressPercent").value(50));
        assertEquals(new BigDecimal("7500.00"), balance(aliceAccount));

        mvc.perform(post("/api/v1/savings/" + goalId + "/deposit").header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content(move(aliceAccount, "99999.00"))).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/savings/" + goalId + "/deposit").header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content(move(aliceAccount, "50.00"))).andExpect(status().isBadRequest());
        assertEquals(new BigDecimal("7500.00"), balance(aliceAccount));

        mvc.perform(get("/api/v1/savings").header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$.totalSaved").value(2500.0)).andExpect(jsonPath("$.goals", hasSize(1)));
    }

    @Test
    void nobodyElseCanTouchMyGoalOrSpendFromMyAccount() throws Exception {
        long goalId = goal(alice, "FLEXIBLE", null);
        mvc.perform(post("/api/v1/savings/" + goalId + "/deposit").header("Authorization", bearer(bob))
                .contentType(MediaType.APPLICATION_JSON).content(move(bobAccount, "100.00"))).andExpect(status().isNotFound());
        long bobGoal = goal(bob, "FLEXIBLE", null);
        mvc.perform(post("/api/v1/savings/" + bobGoal + "/deposit").header("Authorization", bearer(bob))
                .contentType(MediaType.APPLICATION_JSON).content(move(aliceAccount, "100.00"))).andExpect(status().isNotFound());
        assertEquals(new BigDecimal("10000.00"), balance(aliceAccount));
    }

    @Test
    void withdrawalsNeedThePinAndOnlyWhatIsSaved() throws Exception {
        long goalId = goal(alice, "FLEXIBLE", null);
        savingsService.deposit(alice.getId(), goalId, aliceAccount, new BigDecimal("1000.00"));
        String body = move(aliceAccount, "400.00");
        mvc.perform(post("/api/v1/savings/" + goalId + "/withdraw").header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/savings/" + goalId + "/withdraw").header("Authorization", bearer(alice)).header("X-Transaction-Pin", "0000")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/savings/" + goalId + "/withdraw").header("Authorization", bearer(alice)).header("X-Transaction-Pin", "1234")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.savedAmount").value(600.0));
        mvc.perform(post("/api/v1/savings/" + goalId + "/withdraw").header("Authorization", bearer(alice)).header("X-Transaction-Pin", "1234")
                .contentType(MediaType.APPLICATION_JSON).content(move(aliceAccount, "700.00"))).andExpect(status().isConflict());
        assertEquals(new BigDecimal("9400.00"), balance(aliceAccount));
    }

    @Test
    void aLockedGoalCannotBeEmptiedBeforeItsDate() throws Exception {
        long goalId = goal(alice, "LOCKED", today().plusDays(30).toString());
        savingsService.deposit(alice.getId(), goalId, aliceAccount, new BigDecimal("1000.00"));
        mvc.perform(get("/api/v1/savings").header("Authorization", bearer(alice))).andExpect(jsonPath("$.goals[0].withdrawalsLocked").value(true));
        mvc.perform(post("/api/v1/savings/" + goalId + "/withdraw").header("Authorization", bearer(alice)).header("X-Transaction-Pin", "1234")
                .contentType(MediaType.APPLICATION_JSON).content(move(aliceAccount, "100.00"))).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/savings/" + goalId + "/close").header("Authorization", bearer(alice)).header("X-Transaction-Pin", "1234")
                .contentType(MediaType.APPLICATION_JSON).content("{\"accountId\":" + aliceAccount + "}")).andExpect(status().isConflict());
        assertEquals(new BigDecimal("9000.00"), balance(aliceAccount));
    }

    @Test
    void closingPaysOutWhatIsSavedAndEndsTheGoal() throws Exception {
        long goalId = goal(alice, "FLEXIBLE", null);
        savingsService.deposit(alice.getId(), goalId, aliceAccount, new BigDecimal("1500.00"));
        mvc.perform(post("/api/v1/savings/" + goalId + "/close").header("Authorization", bearer(alice)).header("X-Transaction-Pin", "1234")
                .contentType(MediaType.APPLICATION_JSON).content("{\"accountId\":" + aliceAccount + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED")).andExpect(jsonPath("$.savedAmount").value(0));
        assertEquals(new BigDecimal("10000.00"), balance(aliceAccount));
        mvc.perform(get("/api/v1/savings").header("Authorization", bearer(alice))).andExpect(jsonPath("$.goals", hasSize(0)));
        mvc.perform(post("/api/v1/savings/" + goalId + "/deposit").header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content(move(aliceAccount, "100.00"))).andExpect(status().isConflict());
        mvc.perform(put("/api/v1/savings/" + goalId + "/target").header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content("{\"targetAmount\":9000}")).andExpect(status().isConflict());
    }

    @Test
    void simultaneousWithdrawalsCannotSpendTheSameSavedMoneyTwice() throws Exception {
        long goalId = goal(alice, "FLEXIBLE", null);
        savingsService.deposit(alice.getId(), goalId, aliceAccount, new BigDecimal("1000.00"));
        int workers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            results.add(pool.submit(() -> {
                go.await();
                try { savingsService.withdraw(alice.getId(), goalId, aliceAccount, new BigDecimal("1000.00")); return true; }
                catch (ResponseStatusException refused) { return false; }
            }));
        }
        go.countDown();
        int succeeded = 0;
        for (Future<Boolean> r : results) if (r.get()) succeeded++;
        pool.shutdown();

        assertEquals(1, succeeded);
        assertEquals(new BigDecimal("10000.00"), balance(aliceAccount));   // exactly the money we started with
        assertEquals(new BigDecimal("0.00"), goalRepository.findById(goalId).orElseThrow().getSavedAmount());
    }

    @Test
    void savingsMovesAreNotCountedAsSpendingOnTheDashboardButAreOnTheStatement() throws Exception {
        long goalId = goal(alice, "FLEXIBLE", null);
        savingsService.deposit(alice.getId(), goalId, aliceAccount, new BigDecimal("2000.00"));
        mvc.perform(get("/api/v1/dashboard").header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$.monthlySummary.moneyOut").value(0)).andExpect(jsonPath("$.accounts[0].availableBalance").value(8000.0));
        mvc.perform(get("/api/v1/accounts/" + aliceAccount + "/statement").header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$.closingBalance").value(8000.0)).andExpect(jsonPath("$.totalDebits").value(2000.0));
    }

    // ============================================================== insights

    @Test
    void insightsSummariseRealMovementsAndKeepSavingsSeparate() throws Exception {
        accountService.transfer(new TransferRequest(aliceAccount, bobAccount, new BigDecimal("300.00"), "Rent share"));
        long goalId = goal(alice, "FLEXIBLE", null);
        savingsService.deposit(alice.getId(), goalId, aliceAccount, new BigDecimal("200.00"));

        mvc.perform(get("/api/v1/insights").header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months", hasSize(6)))
                .andExpect(jsonPath("$.thisMonth.moneyIn").value(10000.0))
                .andExpect(jsonPath("$.thisMonth.moneyOut").value(300.0))
                .andExpect(jsonPath("$.outflow[0].label").value("Transfers sent"))
                .andExpect(jsonPath("$.outflow[0].amount").value(300.0))
                .andExpect(jsonPath("$.savedThisMonth").value(200.0))
                .andExpect(jsonPath("$.largestExpense.description").value("Rent share"))
                .andExpect(jsonPath("$.lastMonth.moneyOut").value(0));
        mvc.perform(get("/api/v1/insights?accountId=" + bobAccount).header("Authorization", bearer(alice))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/insights")).andExpect(status().isUnauthorized());
    }

    @Test
    void insightsForAUserWithNoAccountsAreEmptyNotAnError() throws Exception {
        User loner = newUser(false);
        mvc.perform(get("/api/v1/insights").header("Authorization", bearer(loner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.months", hasSize(0))).andExpect(jsonPath("$.largestExpense").doesNotExist());
        assertTrue(true);
        assertThrows(ResponseStatusException.class, () -> savingsService.deposit(loner.getId(), 999999L, 1L, new BigDecimal("100.00")));
    }
}
