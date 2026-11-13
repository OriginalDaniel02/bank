package dan.bank.bank.deposit;

import dan.bank.bank.account.dto.MoneyRequest;
import dan.bank.bank.account.repository.AccountRepository;
import dan.bank.bank.account.repository.AccountTransactionRepository;
import dan.bank.bank.account.service.AccountService;
import dan.bank.bank.auth.TokenService;
import dan.bank.bank.auth.repository.RefreshTokenRepository;
import dan.bank.bank.notification.NotificationRepository;
import dan.bank.bank.user.model.User;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "paystack.secret-key=" + DepositTests.SECRET)
@AutoConfigureMockMvc
class DepositTests {

    static final String SECRET = "sk_test_unit_test_secret";

    /** A stand-in for Paystack that records what we asked and answers what the test tells it to. */
    static class FakeGateway implements PaymentGateway {
        final List<String> initialized = new ArrayList<>();
        final Map<String, Long> amountsKobo = new HashMap<>();
        final Map<String, PaymentCheck> checks = new HashMap<>();
        boolean failInitialize = false;
        boolean failVerify = false;

        public boolean isConfigured() { return true; }

        public InitializedPayment initialize(String reference, String email, long kobo, String callbackUrl) {
            if (failInitialize) throw new GatewayException("down", null);
            initialized.add(reference);
            amountsKobo.put(reference, kobo);
            return new InitializedPayment("https://checkout.example/" + reference);
        }

        public PaymentCheck verify(String reference) {
            if (failVerify) throw new GatewayException("down", null);
            return checks.getOrDefault(reference, new PaymentCheck(Outcome.PENDING, 0, "NGN"));
        }
    }

    @TestConfiguration
    static class Config {
        @Bean @Primary FakeGateway fakeGateway() { return new FakeGateway(); }
    }

    @Autowired MockMvc mvc;
    @Autowired FakeGateway gateway;
    @Autowired DepositService depositService;
    @Autowired DepositRepository deposits;
    @Autowired AccountService accountService;
    @Autowired AccountRepository accountRepository;
    @Autowired AccountTransactionRepository transactionRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired UserRepository userRepository;
    @Autowired TokenService tokenService;

    private User alice;
    private User bob;
    private long aliceAccount;
    private long bobAccount;

    @BeforeEach
    void setUp() {
        clean();
        gateway.initialized.clear(); gateway.amountsKobo.clear(); gateway.checks.clear();
        gateway.failInitialize = false; gateway.failVerify = false;
        alice = newUser(); bob = newUser();
        aliceAccount = accountService.createAccount(alice.getId()).data().id();
        bobAccount = accountService.createAccount(bob.getId()).data().id();
    }

    @AfterEach
    void clean() {
        // deposits point at accounts, so they must go first or other test classes cannot clear the shared database
        deposits.deleteAll();
        notificationRepository.deleteAll();
        transactionRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User newUser() {
        String id = UUID.randomUUID().toString();
        return userRepository.save(User.builder().firstName("T").lastName("U").email(id + "@example.com")
                .phoneNumber("0" + Math.abs(id.hashCode())).password("x").build());
    }

    private String bearer(User u) { return "Bearer " + tokenService.createAccessToken(u.getEmail()); }

    private BigDecimal balance(long accountId) { return accountRepository.findById(accountId).orElseThrow().getBalance(); }

    private String start(User user, long accountId, String amount) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/deposits").header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accountId\":" + accountId + ",\"amount\":" + amount + "}"))
                .andExpect(status().isCreated()).andReturn();
        Matcher m = Pattern.compile("\"reference\":\"([^\"]+)\"").matcher(result.getResponse().getContentAsString());
        if (!m.find()) throw new AssertionError("no reference");
        return m.group(1);
    }

    private static String eventBody(String reference, long kobo, String currency) {
        return "{\"event\":\"charge.success\",\"data\":{\"reference\":\"" + reference + "\",\"amount\":" + kobo
                + ",\"currency\":\"" + currency + "\",\"status\":\"success\"}}";
    }

    private int webhook(String body, String signature) throws Exception {
        var request = post("/api/v1/webhooks/paystack").contentType(MediaType.APPLICATION_JSON).content(body);
        if (signature != null) request.header("x-paystack-signature", signature);
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private int signedWebhook(String body) throws Exception {
        return webhook(body, PaystackSignature.sign(SECRET, body.getBytes(StandardCharsets.UTF_8)));
    }

    // ------------------------------------------------------------ signature

    @Test
    void signatureMatchesTheStandardHmacSha512TestVector() {
        // RFC 4231, test case 2
        assertEquals("164b7a7bfcf819e2e395fbe73b56e0a387bd64222e831fd610270cd7ea2505549758bf75c05a994a6d034f65f8f0e6fdcaeab1a34d4a6b4b636e070a38bce737",
                PaystackSignature.sign("Jefe", "what do ya want for nothing?".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void signatureCheckRejectsWrongMissingAndUppercaseNothingElse() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String good = PaystackSignature.sign(SECRET, body);
        assertTrue(PaystackSignature.isValid(SECRET, body, good));
        assertTrue(PaystackSignature.isValid(SECRET, body, good.toUpperCase()));
        assertFalse(PaystackSignature.isValid(SECRET, body, good.substring(1)));
        assertFalse(PaystackSignature.isValid(SECRET, body, null));
        assertFalse(PaystackSignature.isValid(SECRET, "{ }".getBytes(StandardCharsets.UTF_8), good));
        assertFalse(PaystackSignature.isValid("", body, good));
    }

    // ---------------------------------------------------------------- start

    @Test
    void startCreatesAPendingDepositAndSendsTheAmountInKobo() throws Exception {
        String reference = start(alice, aliceAccount, "1500.00");
        assertEquals(1500_00L, gateway.amountsKobo.get(reference));
        Deposit deposit = deposits.findByReference(reference).orElseThrow();
        assertEquals(DepositStatus.PENDING, deposit.getStatus());
        assertEquals(new BigDecimal("1500.00"), deposit.getAmount());
        assertEquals(new BigDecimal("0.00"), balance(aliceAccount));      // nothing is credited just because someone asked
    }

    @Test
    void startRefusesBadAmountsAndOtherPeoplesAccounts() throws Exception {
        for (String body : new String[]{"{\"accountId\":%d,\"amount\":99.99}", "{\"accountId\":%d,\"amount\":10.005}",
                "{\"accountId\":%d,\"amount\":60000}"}) {
            mvc.perform(post("/api/v1/deposits").header("Authorization", bearer(alice))
                    .contentType(MediaType.APPLICATION_JSON).content(body.formatted(aliceAccount)))
                    .andExpect(result -> assertTrue(result.getResponse().getStatus() == 400 || result.getResponse().getStatus() == 422));
        }
        mvc.perform(post("/api/v1/deposits").header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content("{\"accountId\":" + bobAccount + ",\"amount\":500}"))
                .andExpect(status().isNotFound());
        assertEquals(0, deposits.count());
    }

    @Test
    void verifiedUsersMayAddMoreThanUnverifiedOnes() throws Exception {
        bob.setKycTier(1);
        userRepository.save(bob);
        start(bob, bobAccount, "60000.00");
    }

    @Test
    void aGatewayOutageFailsTheDepositCleanlyWith502() throws Exception {
        gateway.failInitialize = true;
        mvc.perform(post("/api/v1/deposits").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accountId\":" + aliceAccount + ",\"amount\":500}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("BAD_GATEWAY"));
        assertEquals(DepositStatus.FAILED, deposits.findAll().get(0).getStatus());
    }

    // -------------------------------------------------------------- webhook

    @Test
    void aSignedWebhookCreditsTheAccountOnceEvenWhenPaystackRetries() throws Exception {
        String reference = start(alice, aliceAccount, "2500.00");
        String body = eventBody(reference, 2500_00, "NGN");
        assertEquals(200, signedWebhook(body));
        assertEquals(200, signedWebhook(body));
        assertEquals(200, signedWebhook(body));

        assertEquals(new BigDecimal("2500.00"), balance(aliceAccount));
        assertEquals(DepositStatus.SUCCESS, deposits.findByReference(reference).orElseThrow().getStatus());
        assertEquals(1, transactionRepository.findByReferenceStartingWith("DEP-" + reference).size());
        assertEquals(1, notificationRepository.count());
    }

    @Test
    void forgedUnsignedAndTamperedWebhooksCreditNothing() throws Exception {
        String reference = start(alice, aliceAccount, "2500.00");
        String body = eventBody(reference, 2500_00, "NGN");
        assertEquals(401, webhook(body, null));
        assertEquals(401, webhook(body, "deadbeef"));
        assertEquals(401, webhook(body, PaystackSignature.sign("some-other-secret", body.getBytes(StandardCharsets.UTF_8))));
        // a genuine signature for a SMALL payment, replayed on a body claiming a big one
        String real = PaystackSignature.sign(SECRET, eventBody(reference, 100, "NGN").getBytes(StandardCharsets.UTF_8));
        assertEquals(401, webhook(eventBody(reference, 250000_00, "NGN"), real));
        assertEquals(new BigDecimal("0.00"), balance(aliceAccount));
        assertEquals(DepositStatus.PENDING, deposits.findByReference(reference).orElseThrow().getStatus());
    }

    @Test
    void anAmountOrCurrencyMismatchIsNeverCredited() throws Exception {
        String wrongAmount = start(alice, aliceAccount, "2500.00");
        assertEquals(200, signedWebhook(eventBody(wrongAmount, 100, "NGN")));
        String wrongCurrency = start(alice, aliceAccount, "1000.00");
        assertEquals(200, signedWebhook(eventBody(wrongCurrency, 1000_00, "USD")));

        assertEquals(new BigDecimal("0.00"), balance(aliceAccount));
        assertEquals(DepositStatus.FAILED, deposits.findByReference(wrongAmount).orElseThrow().getStatus());
        assertEquals(DepositStatus.FAILED, deposits.findByReference(wrongCurrency).orElseThrow().getStatus());
    }

    @Test
    void otherEventsAndUnknownReferencesAreAcceptedAndIgnored() throws Exception {
        assertEquals(200, signedWebhook("{\"event\":\"transfer.success\",\"data\":{}}"));
        assertEquals(200, signedWebhook(eventBody("DBDdoesnotexist", 100_00, "NGN")));
        assertEquals(200, signedWebhook("{\"event\":\"charge.success\",\"data\":{\"reference\":\"x\",\"amount\":100,\"currency\":\"NGN\",\"status\":\"failed\"}}"));
        assertEquals(400, signedWebhook("not json"));
        assertEquals(0, transactionRepository.count());
    }

    @Test
    void manyConcurrentConfirmationsStillCreditExactlyOnce() throws Exception {
        String reference = start(alice, aliceAccount, "4000.00");
        byte[] body = eventBody(reference, 4000_00, "NGN").getBytes(StandardCharsets.UTF_8);
        String signature = PaystackSignature.sign(SECRET, body);
        int workers = 12;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            results.add(pool.submit(() -> { go.await(); return depositService.handleWebhook(body, signature); }));
        }
        go.countDown();
        for (Future<Boolean> result : results) assertTrue(result.get());
        pool.shutdown();

        assertEquals(new BigDecimal("4000.00"), balance(aliceAccount));
        assertEquals(1, transactionRepository.findByReferenceStartingWith("DEP-" + reference).size());
    }

    // --------------------------------------------------------------- status

    @Test
    void checkingAPendingDepositAsksPaystackAndCreditsOnlyOnce() throws Exception {
        String reference = start(alice, aliceAccount, "1000.00");
        mvc.perform(get("/api/v1/deposits/" + reference).header("Authorization", bearer(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
        assertEquals(new BigDecimal("0.00"), balance(aliceAccount));

        gateway.checks.put(reference, new PaymentGateway.PaymentCheck(PaymentGateway.Outcome.SUCCESS, 1000_00, "NGN"));
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/v1/deposits/" + reference).header("Authorization", bearer(alice)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        }
        assertEquals(new BigDecimal("1000.00"), balance(aliceAccount));
    }

    @Test
    void anAbandonedPaymentBecomesFailedAndAProviderOutageLeavesItPending() throws Exception {
        String abandoned = start(alice, aliceAccount, "1000.00");
        gateway.checks.put(abandoned, new PaymentGateway.PaymentCheck(PaymentGateway.Outcome.FAILED, 0, "NGN"));
        mvc.perform(get("/api/v1/deposits/" + abandoned).header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$.status").value("FAILED"));

        String waiting = start(alice, aliceAccount, "1000.00");
        gateway.failVerify = true;
        mvc.perform(get("/api/v1/deposits/" + waiting).header("Authorization", bearer(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void youCannotLookAtSomeoneElsesDeposit() throws Exception {
        String reference = start(alice, aliceAccount, "1000.00");
        mvc.perform(get("/api/v1/deposits/" + reference).header("Authorization", bearer(bob))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/deposits/" + reference)).andExpect(status().isUnauthorized());
    }

    @Test
    void aDepositCreditShowsInTheLedgerAsACredit() throws Exception {
        String reference = start(alice, aliceAccount, "700.00");
        assertEquals(200, signedWebhook(eventBody(reference, 700_00, "NGN")));
        accountService.credit(aliceAccount, new MoneyRequest(new BigDecimal("1.00"), "other"));
        mvc.perform(get("/api/v1/accounts/" + aliceAccount + "/transactions").header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }
}
