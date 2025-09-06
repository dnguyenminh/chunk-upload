package vn.com.fecredit.chunkedupload.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import vn.com.fecredit.chunkedupload.model.TenantAccountRepository;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Security configuration for the chunked upload service.
 *
 * <p>Configures HTTP Basic authentication and disables CSRF for API endpoints.
 * It wires a {@link vn.com.fecredit.chunkedupload.model.TenantAccountRepository} backed
 * {@link org.springframework.security.core.userdetails.UserDetailsService} so tenant accounts
 * stored in the database are used for authentication.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configures the security filter chain for HTTP Basic authentication.
     *
     * @param http The HttpSecurity object to configure.
     * @return The configured SecurityFilterChain.
     * @throws Exception If an error occurs during configuration.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable).authorizeHttpRequests(authz -> authz.anyRequest().authenticated()).httpBasic(withDefaults()).exceptionHandling(ex -> ex.authenticationEntryPoint(customAuthenticationEntryPoint()));
        return http.build();
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

    /**
     * Provides a UserDetailsService backed by {@link TenantAccountRepository}.
     * This allows tenant users stored in the database to authenticate via HTTP Basic.
     *
     * @param tenantAccountRepository repository for tenant accounts
     * @return UserDetailsService implementation
     */
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

    /**
     * Password encoder bean used by Spring Security. Uses a DelegatingPasswordEncoder
     * with bcrypt as the default algorithm.
     */
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
