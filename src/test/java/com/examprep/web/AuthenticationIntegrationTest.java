package com.examprep.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.examprep.security.AppUserPrincipal;
import com.examprep.support.IntegrationTestBase;
import com.examprep.user.domain.UserAccount;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthenticationIntegrationTest extends IntegrationTestBase {

    @Test
    void publicPagesRender() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ExamPrep")));
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"));
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"));
    }

    @Test
    void protectedPagesRedirectAnonymousVisitorsToLogin() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/api/study/" + UUID.randomUUID() + "/status"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void registrationCreatesAnAccountWithABcryptPassword() throws Exception {
        String email = "ada-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "Ada Lovelace")
                        .param("email", email)
                        .param("password", "secret123")
                        .param("confirmPassword", "secret123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        UserAccount account = userAccountRepository.findByEmail(email).orElseThrow();
        assertThat(account.getPasswordHash()).isNotEqualTo("secret123");
        assertThat(account.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches("secret123", account.getPasswordHash())).isTrue();
        assertThat(new BCryptPasswordEncoder().matches("secret123", account.getPasswordHash())).isTrue();
    }

    @Test
    void registrationRejectsMismatchedPasswords() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "Grace Hopper")
                        .param("email", "grace-" + UUID.randomUUID() + "@example.com")
                        .param("password", "secret123")
                        .param("confirmPassword", "different123"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeHasFieldErrors("form", "confirmPassword"));
    }

    @Test
    void registrationRejectsWeakPasswordsAndDuplicateEmails() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "Weak Password")
                        .param("email", "weak-" + UUID.randomUUID() + "@example.com")
                        .param("password", "short")
                        .param("confirmPassword", "short"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "password"));

        String email = "duplicate-" + UUID.randomUUID() + "@example.com";
        createStudent(email);
        mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "Second Student")
                        .param("email", email)
                        .param("password", "secret123")
                        .param("confirmPassword", "secret123"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(model().attributeHasFieldErrors("form", "email"));
    }

    @Test
    void loginSucceedsWithValidCredentialsAndFailsOtherwise() throws Exception {
        String email = "grace-" + UUID.randomUUID() + "@example.com";
        createStudent(email);

        mockMvc.perform(formLogin("/login").user("email", email).password("password", "password123"))
                .andExpect(authenticated());

        mockMvc.perform(formLogin("/login").user("email", email).password("password", "wrong-password"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void logoutRequiresPostWithCsrfAndRedirects() throws Exception {
        AppUserPrincipal student = createStudent("logout-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(post("/logout").with(user(student)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));

        mockMvc.perform(post("/logout").with(user(student)))
                .andExpect(status().isForbidden());
    }

    @Test
    void jsonEndpointsRejectRequestsWithoutACsrfToken() throws Exception {
        AppUserPrincipal student = createStudent("csrf-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(post("/api/study/" + UUID.randomUUID() + "/tutor")
                        .with(user(student))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is this?\"}"))
                .andExpect(status().isForbidden());
    }
}
