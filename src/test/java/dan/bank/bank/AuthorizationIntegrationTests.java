package dan.bank.bank;

import dan.bank.bank.Account.dtos.MoneyRequest;
import dan.bank.bank.Account.repository.AccountRepo;
import dan.bank.bank.Account.repository.AccountTransactionRepository;
import dan.bank.bank.Account.service.AccountServ;
import dan.bank.bank.Auth.TokenService;
import dan.bank.bank.Auth.repository.RefreshTokenRepository;
import dan.bank.bank.User.model.User;
import dan.bank.bank.User.repository.UserRepository;
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
    @Autowired AccountRepo accountRepository;
    @Autowired AccountTransactionRepository transactionRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired AccountServ accountService;

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
                    .andExpect(status().isNotFound());
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
}
