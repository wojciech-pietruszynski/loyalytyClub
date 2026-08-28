package pl.pietruszynski.loyaltyclub.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    private final JwtAuthFilter jwtAuthFilter;
    private final AdminUserDetailsService adminUserDetailsService;
    private final StoreUserDetailsService storeUserDetailsService;
    private final EcomUserDetailsService ecomUserDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> response.setStatus(401))
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/error",
                                "/api/admin/auth/login",
                                "/api/store/auth/login"
                        ).permitAll()
                        .requestMatchers("/api/admin/auth/refresh").hasAnyRole("ADMIN", "TECHNICAL")
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "TECHNICAL")
                        .requestMatchers("/api/store/**").hasRole("STORE")
                        .requestMatchers("/api/ecom/**").hasRole("ECOM")
                        .requestMatchers("/api/coupon/**").hasRole("ECOM")
                        .anyRequest().denyAll()
                )
                .authenticationProvider(adminAuthenticationProvider())
                .authenticationProvider(storeAuthenticationProvider())
                .authenticationProvider(ecomAuthenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Originy frontendu dopuszczone do wywolan cross-origin. Pusta lista (domyslnie)
     * oznacza brak zarejestrowanych mapowan, czyli CORS wylaczony — wariant,
     * w ktorym SPA i API stoja za jednym reverse proxy.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:}") List<String> allowedOrigins) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (allowedOrigins.isEmpty()) {
            return source;
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
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

