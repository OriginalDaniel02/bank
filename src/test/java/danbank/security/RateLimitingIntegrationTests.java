package danbank.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uses its own low limit (rather than the generous one in test application.properties)
 * so the 429 path is actually reachable in a handful of requests.
 */
@SpringBootTest(properties = "bank.rate-limit.requests-per-minute=3")
@AutoConfigureMockMvc
class RateLimitingIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void tooManyLoginAttemptsFromOneClientAreRateLimited() throws Exception {
        String body = "{\"email\": \"nobody@example.com\", \"password\": \"wrong\"}";

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(429));
    }
}
