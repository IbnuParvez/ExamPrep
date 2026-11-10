package com.examprep.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Session-based security: BCrypt passwords, protected study pages, CSRF protection and
 * same-site session cookies. Uploaded documents are never served by a public URL.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/", "/register", "/login", "/privacy", "/error", "/error/**",
                                "/favicon.ico", "/css/**", "/js/**", "/images/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/dashboard", false)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID"))
                // No invalidSessionUrl: it also redirected visitors with a stale cookie away from public
                // pages such as /, /register and /privacy.
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.migrateSession()))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .exceptionHandling(exceptions -> exceptions
                        // API clients must get JSON 401 instead of a 302 whose HTML body they parse as {},
                        // while page requests go to the sign-in page.
                        .authenticationEntryPoint((request, response, authenticationException) -> {
                            if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                                response.getWriter().write("{\"message\":\"Please sign in to continue.\"}");
                            } else {
                                // Delegate to Spring's entry point so the redirect (and its absolute URL)
                                // matches the behaviour of form login.
                                new LoginUrlAuthenticationEntryPoint("/login")
                                        .commence(request, response, authenticationException);
                            }
                        })
                        .accessDeniedPage("/error/403"));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
