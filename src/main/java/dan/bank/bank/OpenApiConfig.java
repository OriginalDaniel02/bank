package dan.bank.bank;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Adds the "Authorize" button to Swagger UI so a login token can be used on protected endpoints. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI bankOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Bank API")
                        .version("v1")
                        .description("Wallet and banking API. Log in via /api/v1/auth/login, then click Authorize "
                                + "and paste the accessToken (without the word 'Bearer')."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
