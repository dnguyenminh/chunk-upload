package vn.com.fecredit.chunkedupload.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import vn.com.fecredit.chunkedupload.model.TenantAccountRepository;

import java.util.Arrays;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(withDefaults()) // Enable CORS and use the CorsConfigurationSource bean
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // Allow all CORS pre-flight OPTIONS requests
                .requestMatchers("/api/upload/users").permitAll() // **THE FIX IS HERE: Allow public access to the user list**
                .anyRequest().authenticated() // All other requests must be authenticated
            )
            .httpBasic(withDefaults())
            .exceptionHandling(ex -> ex.authenticationEntryPoint(customAuthenticationEntryPoint()));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("*"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public org.springframework.security.web.AuthenticationEntryPoint customAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            String message = "{\"error\":\"Unauthorized: " + authException.getMessage() + "\"}";
            response.getWriter().write(message);
        };
    }

    @Bean
    public UserDetailsService userDetailsService(TenantAccountRepository tenantAccountRepository) {
        return username -> {
            System.out.println("[DEBUG] Authentication attempt - Username: " + username);
            return tenantAccountRepository.findByUsername(username).map(account -> {
                System.out.println("[DEBUG] Found user in database:" +
                    "\n  Username: " + account.getUsername() +
                    "\n  Password hash: " + account.getPassword() +
                    "\n  Tenant ID: " + account.getTenantId());
                return org.springframework.security.core.userdetails.User.withUsername(account.getUsername()).password(account.getPassword()).roles("USER").build();
            }).orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("User not found: " + username));
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        java.util.Map<String, org.springframework.security.crypto.password.PasswordEncoder> encoders = new java.util.HashMap<>();
        encoders.put("bcrypt", new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder());
        org.springframework.security.crypto.password.PasswordEncoder delegating = new org.springframework.security.crypto.password.DelegatingPasswordEncoder("bcrypt", encoders);
        System.out.println("[DEBUG] Configured PasswordEncoder:" +
            "\n  Type: " + delegating.getClass().getSimpleName() +
            "\n  Default scheme: bcrypt" +
            "\n  Supported prefixes: " + encoders.keySet());
        return delegating;
    }
}
