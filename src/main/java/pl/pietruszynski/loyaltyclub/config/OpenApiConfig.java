package pl.pietruszynski.loyaltyclub.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Opis kontraktu API w formacie OpenAPI 3.
 *
 * <p>Do tej pory kontrakt istnial wylacznie w kodzie kontrolerow, a rozjazd miedzy
 * nim a biblioteka SDK ujawnial sie dopiero w czasie dzialania. Specyfikacja
 * generowana z kodu jest zrodlem dla generatorow klientow i dla testow kontraktowych.
 *
 * <p>API jest podzielone na grupy odpowiadajace kanalom integracji, dzieki czemu
 * z jednej aplikacji da sie wygenerowac osobne biblioteki dla kasy i dla sklepu
 * internetowego, bez czesci administracyjnej.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI loyaltyClubOpenApi(@Value("${app.openapi.server-url:http://localhost:8089}") String serverUrl) {
        return new OpenAPI()
                .info(new Info()
                        .title("LoyaltyClub API")
                        .version("1.1.0")
                        .description("""
                                API programu lojalnosciowego LoyaltyClub.

                                Przestrzenie:
                                * `/api/admin` -- panel administracyjny (role ADMIN i TECHNICAL),
                                * `/api/store` -- kasa sklepu stacjonarnego (rola STORE),
                                * `/api/ecom`  -- integracja sklepu internetowego (rola ECOM),
                                * `/api/coupon` -- wymiana i walidacja kuponow (rola ECOM).

                                Uwierzytelnianie: token JWT (`bearerAuth`) pozyskiwany z punktu
                                `/login` wlasciwej przestrzeni. Metoda HTTP Basic pozostaje
                                obslugiwana dla zgodnosci wstecz.

                                Operacje zmieniajace saldo punktow wymagaja naglowka
                                `Idempotency-Key` -- powtorzone zadanie z tym samym kluczem
                                zwraca pierwotny wynik zamiast wykonywac operacje drugi raz.
                                """)
                        .contact(new Contact().name("LoyaltyClub"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(new Server().url(serverUrl).description("Backend LoyaltyClub")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSecuritySchemes("basicAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder().group("admin").pathsToMatch("/api/admin/**").build();
    }

    @Bean
    public GroupedOpenApi storeApi() {
        return GroupedOpenApi.builder().group("store").pathsToMatch("/api/store/**").build();
    }

    @Bean
    public GroupedOpenApi ecomApi() {
        return GroupedOpenApi.builder().group("ecom").pathsToMatch("/api/ecom/**", "/api/coupon/**").build();
    }
}
