package dan.bank.bank.deposit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * Paystack REST API (https://paystack.com/docs/api/). Uses the documented endpoints:
 * POST /transaction/initialize and GET /transaction/verify/:reference, authenticated with the secret key.
 * The secret key is read from configuration and is never logged.
 */
@Component
public class PaystackGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(PaystackGateway.class);

    private final String secretKey;
    private final RestClient client;
    private final JsonMapper json;

    public PaystackGateway(@Value("${paystack.secret-key:}") String secretKey,
                           @Value("${paystack.base-url:https://api.paystack.co}") String baseUrl,
                           JsonMapper json) {
        this.secretKey = secretKey;
        this.json = json;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }

    @Override
    public InitializedPayment initialize(String reference, String customerEmail, long amountKobo, String callbackUrl) {
        try {
            String body = client.post().uri("/transaction/initialize")
                    .header("Authorization", "Bearer " + secretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", customerEmail, "amount", amountKobo, "currency", "NGN",
                            "reference", reference, "callback_url", callbackUrl))
                    .retrieve().body(String.class);
            JsonNode root = json.readTree(body);
            String url = root.path("data").path("authorization_url").asString("");
            if (!root.path("status").asBoolean(false) || url.isBlank()) {
                throw new GatewayException("Paystack did not return a payment page", null);
            }
            return new InitializedPayment(url);
        } catch (RestClientException | tools.jackson.core.JacksonException e) {
            log.warn("Paystack initialize failed for {}: {}", reference, e.getClass().getSimpleName());
            throw new GatewayException("Paystack initialize failed", e);
        }
    }

    @Override
    public PaymentCheck verify(String reference) {
        try {
            String body = client.get().uri("/transaction/verify/{reference}", reference)
                    .header("Authorization", "Bearer " + secretKey)
                    .retrieve().body(String.class);
            JsonNode data = json.readTree(body).path("data");
            Outcome outcome = switch (data.path("status").asString("")) {
                case "success" -> Outcome.SUCCESS;
                case "failed", "abandoned", "reversed" -> Outcome.FAILED;
                default -> Outcome.PENDING;
            };
            return new PaymentCheck(outcome, data.path("amount").asLong(0), data.path("currency").asString(""));
        } catch (RestClientException | tools.jackson.core.JacksonException e) {
            log.warn("Paystack verify failed for {}: {}", reference, e.getClass().getSimpleName());
            throw new GatewayException("Paystack verify failed", e);
        }
    }
}
