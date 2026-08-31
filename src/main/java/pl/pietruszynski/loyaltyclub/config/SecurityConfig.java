package pl.pietruszynski.loyaltyclub.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import pl.pietruszynski.loyaltyclub.api.admin.security.AdminUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.admin.security.JwtAuthFilter;
import pl.pietruszynski.loyaltyclub.api.ecom.security.EcomUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.store.security.StoreUserDetailsService;

import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** Sciezki dokumentacji OpenAPI; udostepniane, bo z nich generowane sa biblioteki SDK. */
    private static final String[] OPENAPI_PATHS = {
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    private final JwtAuthFilter jwtAuthFilter;
    private final AdminUserDetailsService adminUserDetailsService;
    private final StoreUserDetailsService storeUserDetailsService;
    private final EcomUserDetailsService ecomUserDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource corsConfigurationSource,
                                                   @Value("${app.openapi.expose-docs:true}") boolean exposeOpenApiDocs) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> response.setStatus(401))
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(auth -> {
                    // Zapytanie wstepne CORS nie niesie poswiadczen, wiec musi byc
                    // dopuszczone niezaleznie od regul dla wlasciwego zadania.
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                    if (exposeOpenApiDocs) {
                        auth.requestMatchers(OPENAPI_PATHS).permitAll();
                    }

                    auth.requestMatchers(
                                    "/error",
                                    "/api/admin/auth/login",
                                    "/api/store/auth/login",
                                    "/api/ecom/auth/login"
                            ).permitAll()
                            // Wylogowanie musi dzialac takze dla tokenu, ktory zdazyl wygasnac.
                            .requestMatchers(
                                    "/api/admin/auth/logout",
                                    "/api/store/auth/logout",
                                    "/api/ecom/auth/logout"
                            ).permitAll()
                            .requestMatchers("/api/admin/auth/refresh", "/api/admin/auth/change-password")
                            .hasAnyRole("ADMIN", "TECHNICAL")
                            .requestMatchers("/api/store/auth/refresh").hasRole("STORE")
                            .requestMatchers("/api/ecom/auth/refresh").hasRole("ECOM")
                            .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "TECHNICAL")
                            .requestMatchers("/api/store/**").hasRole("STORE")
                            .requestMatchers("/api/ecom/**").hasRole("ECOM")
                            .requestMatchers("/api/coupon/**").hasRole("ECOM")
                            .anyRequest().denyAll();
                })
                .authenticationProvider(adminAuthenticationProvider())
                .authenticationProvider(storeAuthenticationProvider())
                .authenticationProvider(ecomAuthenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Originy frontendu dopuszczone do wywolan cross-origin. Frontend jest osobnym
     * repozytorium i wdrozeniem, wiec wariant z rozdzielonymi adresami musi byc
     * realnie obslugiwany, a nie tylko mozliwy do wlaczenia.
     *
     * <p>Pusta lista (domyslnie) oznacza brak zarejestrowanych mapowan, czyli
     * wariant z jednym reverse proxy przed SPA i API.
     *
     * <p>Wpisy moga zawierac wzorzec ({@code https://*.example.com}), dlatego
     * uzywamy {@code allowedOriginPatterns}. Naglowek {@code Content-Disposition}
     * jest jawnie wystawiony -- bez tego przegladarka nie odczyta nazwy pliku
     * przy eksporcie raportow z innego origin. Poswiadczenia sa wylaczone,
     * bo token jest przesylany naglowkiem {@code Authorization}, a nie ciasteczkiem.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:}") List<String> allowedOrigins) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (origins.isEmpty()) {
            return source;
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION, HttpHeaders.LOCATION));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public DaoAuthenticationProvider adminAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(adminUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public DaoAuthenticationProvider storeAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(storeUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public DaoAuthenticationProvider ecomAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(ecomUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(List.of(
                adminAuthenticationProvider(),
                storeAuthenticationProvider(),
                ecomAuthenticationProvider()
        ));
    }
}
