package dan.bank.bank.deposit;

import dan.bank.bank.account.repository.AccountRepository;
import dan.bank.bank.account.service.AccountService;
import dan.bank.bank.auth.TokenService;
import dan.bank.bank.user.model.User;
import dan.bank.bank.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With no Paystack key configured, deposits must be off, and the open webhook must refuse everything. */
@SpringBootTest
@AutoConfigureMockMvc
class DepositsDisabledTests {

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired AccountService accountService;
    @Autowired TokenService tokenService;

    @AfterEach
    void clean() {
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void startingADepositIsUnavailableWithoutAKey() throws Exception {
        String id = UUID.randomUUID().toString();
        User user = userRepository.save(User.builder().firstName("T").lastName("U").email(id + "@example.com")
                .phoneNumber("0" + Math.abs(id.hashCode())).password("x").build());
        long account = accountService.createAccount(user.getId()).data().id();
        mvc.perform(post("/api/v1/deposits").header("Authorization", "Bearer " + tokenService.createAccessToken(user.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accountId\":" + account + ",\"amount\":500}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void theWebhookRefusesEverythingWithoutAKey() throws Exception {
        // even a signature made with an empty key must not be accepted
        mvc.perform(post("/api/v1/webhooks/paystack").contentType(MediaType.APPLICATION_JSON)
                        .header("x-paystack-signature", "anything").content("{\"event\":\"charge.success\"}"))
                .andExpect(status().isServiceUnavailable());
    }
}
