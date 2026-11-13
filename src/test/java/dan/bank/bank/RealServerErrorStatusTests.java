package dan.bank.bank;

import dan.bank.bank.auth.TokenService;
import dan.bank.bank.user.model.User;
import dan.bank.bank.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MockMvc skips the servlet container's error dispatch, so it cannot catch error responses being
 * rewritten to 401. These tests use a real HTTP server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealServerErrorStatusTests {

    @LocalServerPort int port;
    @Autowired TokenService tokenService;
    @Autowired UserRepository userRepository;

    private int get(String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) request.header("Authorization", "Bearer " + token);
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private String tokenForNewUser() {
        String id = UUID.randomUUID().toString();
        User user = userRepository.save(User.builder().firstName("T").lastName("U")
                .email(id + "@example.com").phoneNumber("0" + Math.abs(id.hashCode())).password("x").build());
        return tokenService.createAccessToken(user.getEmail());
    }

    @Test
    void missingResourceIs404NotUnauthorized() throws Exception {
        assertEquals(404, get("/api/v1/accounts/999999", tokenForNewUser()));
    }

    @Test
    void someoneElsesUserIs403NotUnauthorized() throws Exception {
        assertEquals(403, get("/api/v1/users/id/1", tokenForNewUser()));
    }

    @Test
    void noTokenIsStillUnauthorized() throws Exception {
        assertEquals(401, get("/api/v1/accounts", null));
    }

    @Test
    void garbageTokenIsStillUnauthorized() throws Exception {
        assertEquals(401, get("/api/v1/accounts", "not-a-jwt"));
    }

    @Test
    void swaggerDocsAreOpenAndOfferBearerAuthorization() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        org.junit.jupiter.api.Assertions.assertTrue(response.body().contains("bearerAuth"));
        org.junit.jupiter.api.Assertions.assertTrue(response.body().contains("X-Transaction-Pin"));
        org.junit.jupiter.api.Assertions.assertTrue(response.body().contains("Idempotency-Key"));
    }

    @Test
    void swaggerUiPageIsPublic() throws Exception {
        assertEquals(200, get("/swagger-ui/index.html", null));
        int shortcut = get("/swagger-ui.html", null);
        org.junit.jupiter.api.Assertions.assertTrue(shortcut == 200 || shortcut == 302, "unexpected status " + shortcut);
    }

    @Test
    void unauthorizedBodyUsesTheStandardErrorShape() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/accounts")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
        org.junit.jupiter.api.Assertions.assertTrue(response.body().contains("\"code\":\"UNAUTHORIZED\""), response.body());
        org.junit.jupiter.api.Assertions.assertTrue(response.body().contains("\"message\""), response.body());
    }
}
