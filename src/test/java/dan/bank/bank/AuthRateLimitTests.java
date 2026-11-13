package dan.bank.bank;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "bank.security.auth-requests-per-minute=3")
@AutoConfigureMockMvc
class AuthRateLimitTests {

    @Autowired MockMvc mvc;
    @Autowired dan.bank.bank.auth.LoginAttemptRepository attempts;

    @org.junit.jupiter.api.AfterEach
    void clean() { attempts.deleteAll(); }   // the database is shared with other test classes

    @Test
    void onlyTheFirstFewSignInRequestsPerMinuteGetThrough() throws Exception {
        String body = "{\"identifier\":\"nobody@example.com\",\"password\":\"x\"}";
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        // sign-up is limited too, and everything else is left alone
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isTooManyRequests());
        mvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
    }
}
