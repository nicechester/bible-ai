package io.github.nicechester.bibleai.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration with optional authentication.
 * 
 * Enable authentication by setting:
 *   AUTH_ENABLED=true
 *   AUTH_USERNAME=your-username
 *   AUTH_PASSWORD=your-password
 */
@Log4j2
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${auth.enabled:false}")
    private boolean authEnabled;

    @Value("${auth.username:admin}")
    private String username;

    @Value("${auth.password:admin}")
    private String password;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        if (authEnabled) {
            log.info("Authentication ENABLED - login required");
            http
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/login", "/css/**", "/js/**").permitAll()
                    .anyRequest().authenticated()
                )
                .formLogin(form -> form
                    .loginPage("/login")
                    .defaultSuccessUrl("/", true)
                    .permitAll()
                )
                .logout(logout -> logout
                    .logoutSuccessUrl("/login?logout")
                    .permitAll()
                )
                .csrf(csrf -> csrf
                    .ignoringRequestMatchers("/api/**")
                );
        } else {
            log.info("Authentication DISABLED - open access");
            http
                .authorizeHttpRequests(auth -> auth
                    .anyRequest().permitAll()
                )
                .csrf(csrf -> csrf.disable());
        }
        
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "auth.enabled", havingValue = "true")
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        log.info("Creating user: {}", username);
        UserDetails user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
