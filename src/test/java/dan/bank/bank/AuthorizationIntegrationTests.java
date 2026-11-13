package dan.bank.bank;

import dan.bank.bank.account.dto.MoneyRequest;
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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationIntegrationTests {

    @Autowired MockMvc mvc;
    @Autowired TokenService tokenService;
    @Autowired UserRepository userRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired AccountTransactionRepository transactionRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired AccountService accountService;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;

    private User alice;
    private User bob;
    private long aliceAccount;
    private long bobAccount;

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
        bobAccount = accountService.createAccount(bob.getId()).data().id();
        accountService.credit(aliceAccount, new MoneyRequest(new BigDecimal("50.00"), "seed"));
    }

    private User newUser() {
        String id = UUID.randomUUID().toString();
        return userRepository.save(User.builder().firstName("Test").lastName("User")
                .email(id + "@example.com").phoneNumber("0" + Math.abs(id.hashCode()))
                .password("x").build());
    }

    private String bearer(User user) {
        return "Bearer " + tokenService.createAccessToken(user.getEmail());
    }

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        mvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
    }

    @Test
    void ownerCanReadOwnAccountButOthersGetNotFound() throws Exception {
        mvc.perform(get("/api/v1/accounts/" + aliceAccount).header("Authorization", bearer(alice)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/accounts/" + aliceAccount).header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotCreditDebitOrListTransactionsOfSomeoneElsesAccount() throws Exception {
        String body = "{\"amount\":10.00}";
        for (String path : new String[]{"/credit", "/debit"}) {
            mvc.perform(post("/api/v1/accounts/" + aliceAccount + path)
                            .header("Authorization", bearer(bob))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/accounts/" + aliceAccount + "/transactions").header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());
        assertEquals(new BigDecimal("50.00"), accountRepository.findById(aliceAccount).orElseThrow().getBalance());
    }

    @Test
    void cannotTransferOutOfSomeoneElsesAccountButCanSendToThem() throws Exception {
        String steal = "{\"fromAccountId\":" + aliceAccount + ",\"toAccountId\":" + bobAccount + ",\"amount\":10.00}";
        mvc.perform(post("/api/v1/accounts/transfer").header("Authorization", bearer(bob))
                .contentType(MediaType.APPLICATION_JSON).content(steal)).andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/accounts/transfer").header("Authorization", bearer(alice))
                .header("X-Transaction-Pin", "1234")
                .contentType(MediaType.APPLICATION_JSON).content(steal)).andExpect(status().isCreated());
        assertEquals(new BigDecimal("10.00"), accountRepository.findById(bobAccount).orElseThrow().getBalance());
    }

    @Test
    void userIdInUrlMustBeTheCaller() throws Exception {
        mvc.perform(post("/api/v1/accounts/user/" + alice.getId()).header("Authorization", bearer(bob)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/id/" + alice.getId()).header("Authorization", bearer(bob)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/users/deleteUser/" + alice.getId()).header("Authorization", bearer(bob)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/accounts/" + aliceAccount + "/user/" + alice.getId()).header("Authorization", bearer(bob)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletingUserWithAccountsIsConflictNotServerError() throws Exception {
        mvc.perform(delete("/api/v1/users/deleteUser/" + alice.getId()).header("Authorization", bearer(alice)))
                .andExpect(status().isConflict());
    }

    @Test
    void tokenOfDeletedUserIsUnauthorized() throws Exception {
        String token = bearer(bob);
        accountRepository.deleteById(bobAccount);
        userRepository.deleteById(bob.getId());
        mvc.perform(get("/api/v1/accounts").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    void transferNeedsCorrectPinAndLocksAfterFiveWrongAttempts() throws Exception {
        String body = "{\"fromAccountId\":" + aliceAccount + ",\"toAccountId\":" + bobAccount + ",\"amount\":1.00}";
        mvc.perform(post("/api/v1/accounts/transfer").header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/v1/accounts/transfer").header("Authorization", bearer(alice))
                    .header("X-Transaction-Pin", "0000")
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        }
        // Locked now, even the right PIN is refused.
        mvc.perform(post("/api/v1/accounts/transfer").header("Authorization", bearer(alice))
                .header("X-Transaction-Pin", "1234")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isLocked());
        assertEquals(new BigDecimal("50.00"), accountRepository.findById(aliceAccount).orElseThrow().getBalance());
    }

    @Test
    void nameLookupAndTransferByAccountNumber() throws Exception {
        String bobNumber = accountRepository.findById(bobAccount).orElseThrow().getAccountNumber();
        mvc.perform(get("/api/v1/accounts/lookup/" + bobNumber).header("Authorization", bearer(alice)))
                .andExpect(status().isOk());
        String body = "{\"fromAccountId\":" + aliceAccount + ",\"toAccountNumber\":\"" + bobNumber + "\",\"amount\":5.00}";
        mvc.perform(post("/api/v1/accounts/transfer/by-number").header("Authorization", bearer(alice))
                .header("X-Transaction-Pin", "1234")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        assertEquals(new BigDecimal("5.00"), accountRepository.findById(bobAccount).orElseThrow().getBalance());
    }

    @Test
    void onlyAdminCanCredit() throws Exception {
        alice.setRole(dan.bank.bank.user.model.Role.ADMIN);
        userRepository.save(alice);
        mvc.perform(post("/api/v1/accounts/" + bobAccount + "/credit").header("Authorization", bearer(alice))
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":20.00}")).andExpect(status().isCreated());
        assertEquals(new BigDecimal("20.00"), accountRepository.findById(bobAccount).orElseThrow().getBalance());
    }
}
