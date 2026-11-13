package dan.bank.bank;

import dan.bank.bank.account.dto.MoneyRequest;
import dan.bank.bank.account.dto.TransferRequest;
import dan.bank.bank.account.repository.AccountRepository;
import dan.bank.bank.account.repository.AccountTransactionRepository;
import dan.bank.bank.account.service.AccountService;
import dan.bank.bank.auth.TokenService;
import dan.bank.bank.auth.repository.RefreshTokenRepository;
import dan.bank.bank.user.model.User;
import dan.bank.bank.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BeneficiaryNotificationStatementTests {

    @Autowired MockMvc mvc;
    @Autowired TokenService tokenService;
    @Autowired UserRepository userRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired AccountTransactionRepository transactionRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired AccountService accountService;
    @Autowired PasswordEncoder encoder;

    private User alice;
    private User bob;
    private long aliceAccount;
    private long bobAccount;
    private String bobNumber;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
        alice = newUser();
        alice.setTransactionPinHash(encoder.encode("1234"));
        alice = userRepository.save(alice);
        bob = newUser();
        aliceAccount = accountService.createAccount(alice.getId()).data().id();
        var bobDto = accountService.createAccount(bob.getId()).data();
        bobAccount = bobDto.id();
        bobNumber = bobDto.accountNumber();
    }

    private User newUser() {
        String id = UUID.randomUUID().toString();
        return userRepository.save(User.builder().firstName("Test").lastName(id.substring(0, 6))
                .email(id + "@example.com").phoneNumber("0" + Math.abs(id.hashCode())).password("x").build());
    }

    private String bearer(User u) { return "Bearer " + tokenService.createAccessToken(u.getEmail()); }

    private String transferJson(BigDecimal amount) {
        return "{\"fromAccountId\":" + aliceAccount + ",\"toAccountId\":" + bobAccount + ",\"amount\":" + amount + "}";
    }

    private static long idOf(String json) {
        Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(json);
        if (!m.find()) throw new AssertionError("no id in " + json);
        return Long.parseLong(m.group(1));
    }

    // ---------------- beneficiaries ----------------

    @Test
    void beneficiaryLifecycleAndIsolation() throws Exception {
        String add = "{\"accountNumber\":\"" + bobNumber + "\",\"nickname\":\"Bob\"}";
        mvc.perform(post("/api/v1/beneficiaries").header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content(add)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/beneficiaries").header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content(add)).andExpect(status().isConflict());

        String list = mvc.perform(get("/api/v1/beneficiaries").header("Authorization", bearer(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].accountNumber").value(bobNumber)).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/v1/beneficiaries").header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$.length()").value(0));

        long id = idOf(list);
        mvc.perform(delete("/api/v1/beneficiaries/" + id).header("Authorization", bearer(bob))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/beneficiaries/" + id).header("Authorization", bearer(alice))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/beneficiaries").header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void beneficiaryValidation() throws Exception {
        String ownNumber = accountRepository.findById(aliceAccount).orElseThrow().getAccountNumber();
        mvc.perform(post("/api/v1/beneficiaries").header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content("{\"accountNumber\":\"" + ownNumber + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/beneficiaries").header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content("{\"accountNumber\":\"12\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/beneficiaries").header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content("{\"accountNumber\":\"0000000000\"}")).andExpect(status().isNotFound());
    }

    // ---------------- notifications ----------------

    @Test
    void transferNotifiesBothSidesOnceEvenWhenRetried() throws Exception {
        accountService.credit(aliceAccount, new MoneyRequest(new BigDecimal("500.00"), "seed"));
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/v1/accounts/transfer").header("Authorization", bearer(alice))
                    .header("X-Transaction-Pin", "1234").header("Idempotency-Key", "n-1")
                    .contentType(MediaType.APPLICATION_JSON).content(transferJson(new BigDecimal("1500.00").subtract(new BigDecimal("1000.00")))))
                    .andExpect(status().isCreated());
        }
        mvc.perform(get("/api/v1/notifications").header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("DEBIT"))
                .andExpect(jsonPath("$.content[0].message").value(containsString("NGN 500.00")));
        mvc.perform(get("/api/v1/notifications").header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("CREDIT"));
        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$.unread").value(1));
    }

    @Test
    void markingReadIsPerUser() throws Exception {
        accountService.credit(aliceAccount, new MoneyRequest(new BigDecimal("100.00"), "seed"));
        mvc.perform(post("/api/v1/accounts/transfer").header("Authorization", bearer(alice)).header("X-Transaction-Pin", "1234")
                .contentType(MediaType.APPLICATION_JSON).content(transferJson(new BigDecimal("10.00")))).andExpect(status().isCreated());
        String body = mvc.perform(get("/api/v1/notifications").header("Authorization", bearer(bob)))
                .andReturn().getResponse().getContentAsString();
        long id = idOf(body);

        mvc.perform(post("/api/v1/notifications/" + id + "/read").header("Authorization", bearer(alice))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$.unread").value(1));
        mvc.perform(post("/api/v1/notifications/" + id + "/read").header("Authorization", bearer(bob))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$.unread").value(0));
        mvc.perform(get("/api/v1/notifications?unread=true").header("Authorization", bearer(bob)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(post("/api/v1/notifications/read-all").header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$.updated").value(1));
    }

    // ---------------- statements ----------------

    private LocalDate today() { return LocalDate.now(ZoneId.of("Africa/Lagos")); }

    private void seedLedger() {
        accountService.credit(aliceAccount, new MoneyRequest(new BigDecimal("100.00"), "=HYPERLINK(\"http://evil\")"));
        accountService.transfer(new TransferRequest(aliceAccount, bobAccount, new BigDecimal("30.00"), "rent"));
    }

    @Test
    void statementHasCorrectBalancesAndRunningTotals() throws Exception {
        seedLedger();
        mvc.perform(get("/api/v1/accounts/" + aliceAccount + "/statement?from=" + today().minusDays(1) + "&to=" + today())
                        .header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingBalance").value(0.0))
                .andExpect(jsonPath("$.closingBalance").value(70.0))
                .andExpect(jsonPath("$.totalCredits").value(100.0))
                .andExpect(jsonPath("$.totalDebits").value(30.0))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].balance").value(100.0))
                .andExpect(jsonPath("$.lines[1].direction").value("DEBIT"))
                .andExpect(jsonPath("$.lines[1].balance").value(70.0));
        assertEquals(new BigDecimal("70.00"), accountRepository.findById(aliceAccount).orElseThrow().getBalance());
    }

    @Test
    void typeFilterOnlyHidesRowsNotBalances() throws Exception {
        seedLedger();
        mvc.perform(get("/api/v1/accounts/" + aliceAccount + "/statement?type=TRANSFER_OUT").header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.openingBalance").value(0.0))
                .andExpect(jsonPath("$.closingBalance").value(70.0));
    }

    @Test
    void periodBeforeAnyActivityIsEmptyWithZeroBalances() throws Exception {
        seedLedger();
        mvc.perform(get("/api/v1/accounts/" + aliceAccount + "/statement?from=" + today().minusDays(10) + "&to=" + today().minusDays(5))
                        .header("Authorization", bearer(alice)))
                .andExpect(jsonPath("$.lines.length()").value(0))
                .andExpect(jsonPath("$.openingBalance").value(0.0))
                .andExpect(jsonPath("$.closingBalance").value(0.0));
    }

    @Test
    void csvExportNeutralisesFormulasAndIsOwnerOnly() throws Exception {
        seedLedger();
        mvc.perform(get("/api/v1/accounts/" + aliceAccount + "/statement?format=csv").header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", startsWith("attachment; filename=\"statement-")))
                .andExpect(content().string(containsString("Date,Reference,Type,Direction,Amount,Balance,Description")))
                .andExpect(content().string(containsString("'=HYPERLINK")));
        mvc.perform(get("/api/v1/accounts/" + aliceAccount + "/statement").header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidRangesAreRejected() throws Exception {
        mvc.perform(get("/api/v1/accounts/" + aliceAccount + "/statement?from=" + today() + "&to=" + today().minusDays(1))
                .header("Authorization", bearer(alice))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/accounts/" + aliceAccount + "/statement?from=" + today().minusDays(400) + "&to=" + today())
                .header("Authorization", bearer(alice))).andExpect(status().isBadRequest());
    }
}
