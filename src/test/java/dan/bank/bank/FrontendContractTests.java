package dan.bank.bank;

import dan.bank.bank.account.dto.MoneyRequest;
import dan.bank.bank.account.dto.TransferRequest;
import dan.bank.bank.account.repository.AccountRepository;
import dan.bank.bank.account.repository.AccountTransactionRepository;
import dan.bank.bank.account.service.AccountService;
import dan.bank.bank.auth.repository.RefreshTokenRepository;
import dan.bank.bank.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The request/response shapes the React frontend depends on. */
@SpringBootTest
@AutoConfigureMockMvc
class FrontendContractTests {

    private static final String PASSWORD = "Str0ng-Passw0rd!";

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired AccountTransactionRepository transactionRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired AccountService accountService;

    @BeforeEach
    void clean() {
        transactionRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String signUpJson(String email, String phone, String password, boolean terms) {
        return "{\"firstName\":\"Ada\",\"lastName\":\"Obi\",\"email\":\"" + email + "\",\"phoneNumber\":\"" + phone
                + "\",\"password\":\"" + password + "\",\"termsAccepted\":" + terms + "}";
    }

    private void signUp(String email, String phone) throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(signUpJson(email, phone, PASSWORD, true))).andExpect(status().isCreated());
    }

    private String login(String identifier) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + identifier + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        Matcher m = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(result.getResponse().getContentAsString());
        if (!m.find()) throw new AssertionError("no token");
        return "Bearer " + m.group(1);
    }

    private long accountIdOf(String email) {
        return accountRepository.findByUser_Id(userRepository.findByEmail(email).orElseThrow().getId()).get(0).getId();
    }

    @Test
    void signUpCreatesUserAndFirstSavingsAccountAndNeverLeaksSecrets() throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signUpJson("ada@example.com", "08031234567", PASSWORD, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.phoneNumber").value("08031234567"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.transactionPinHash").doesNotExist());
        assertEquals(1, accountRepository.count());
        assertEquals("SAVINGS", accountRepository.findAll().get(0).getType().name());
    }

    @Test
    void loginWorksWithEmailPhoneAndTheOldEmailField_andReturnsUserAndExpiry() throws Exception {
        signUp("ada@example.com", "08031234567");
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"ada@example.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andExpect(jsonPath("$.user.firstName").value("Ada"))
                .andExpect(jsonPath("$.user.password").doesNotExist());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"0803 123 4567\",\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ada@example.com\",\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isOk());
    }

    @Test
    void errorsAlwaysHaveMessageAndCode() throws Exception {
        signUp("ada@example.com", "08031234567");
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"ada@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signUpJson("ada@example.com", "08039999999", PASSWORD, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(get("/api/v1/accounts/999999").header("Authorization", login("ada@example.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void validationErrorsNameTheFields() throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signUpJson("not-an-email", "123", "weak", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.phoneNumber").exists())
                .andExpect(jsonPath("$.fields.password").exists())
                .andExpect(jsonPath("$.fields.termsAccepted").exists());
        assertEquals(0, userRepository.count());
    }

    @Test
    void dashboardShowsAccountsMonthlyTotalsRecentActivityAndWeek() throws Exception {
        signUp("ada@example.com", "08031234567");
        signUp("bob@example.com", "08037654321");
        long ada = accountIdOf("ada@example.com");
        long bob = accountIdOf("bob@example.com");
        accountService.credit(ada, new MoneyRequest(new BigDecimal("1000.00"), "Salary"));
        accountService.transfer(new TransferRequest(ada, bob, new BigDecimal("250.00"), "Rent"));

        mvc.perform(get("/api/v1/dashboard").header("Authorization", login("ada@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts", hasSize(1)))
                .andExpect(jsonPath("$.accounts[0].type").value("SAVINGS"))
                .andExpect(jsonPath("$.accounts[0].availableBalance").value(750.0))
                .andExpect(jsonPath("$.selectedAccountId").value(ada))
                .andExpect(jsonPath("$.monthlySummary.moneyIn").value(1000.0))
                .andExpect(jsonPath("$.monthlySummary.moneyOut").value(250.0))
                .andExpect(jsonPath("$.recentTransactions", hasSize(2)))
                .andExpect(jsonPath("$.recentTransactions[0].direction").value("DEBIT"))
                .andExpect(jsonPath("$.recentTransactions[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.dailySpending", hasSize(7)))
                .andExpect(jsonPath("$.dailySpending[6].amount").value(250.0));
    }

    @Test
    void dashboardRefusesSomeoneElsesAccountAndWorksForABrandNewUser() throws Exception {
        signUp("ada@example.com", "08031234567");
        signUp("bob@example.com", "08037654321");
        long bob = accountIdOf("bob@example.com");
        mvc.perform(get("/api/v1/dashboard?accountId=" + bob).header("Authorization", login("ada@example.com")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/dashboard").header("Authorization", login("ada@example.com")))
                .andExpect(jsonPath("$.monthlySummary.moneyIn").value(0))
                .andExpect(jsonPath("$.recentTransactions", hasSize(0)))
                .andExpect(content().string(not(containsString("bob@example.com"))));
    }

}
