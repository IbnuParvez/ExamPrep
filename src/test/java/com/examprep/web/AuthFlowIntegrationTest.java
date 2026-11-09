package com.examprep.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import com.examprep.api.UserRegistrationService;
import com.examprep.api.dto.RegisterUserRequest;
import com.examprep.support.AbstractIntegrationTest;
import com.examprep.user.domain.UserAccount;
import com.examprep.user.repository.UserAccountRepository;

class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRegistrationService registrationService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Test
    void landingPageIsPublic() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ExamPrep")))
                .andExpect(content().string(containsString("Turn your lecture notes")));
    }

    @Test
    void loginAndRegisterPagesArePublic() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign in")));
        mockMvc.perform(get("/register")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Create your account")));
    }

    @Test
    void protectedPagesRedirectAnonymousVisitorsToLogin() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/documents"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/api/study/" + UUID.randomUUID() + "/status"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void registrationStoresABcryptHashAndSignsTheStudentIn() throws Exception {
        String email = "ada-" + UUID.randomUUID() + "@example.com";

        MvcResult result = mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "Ada Lovelace")
                        .param("email", email)
                        .param("password", "correct-horse")
                        .param("passwordConfirm", "correct-horse"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ada Lovelace")));

        UserAccount account = userAccountRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(account.getPasswordHash()).startsWith("$2");
        assertThat(account.getPasswordHash()).isNotEqualTo("correct-horse");
    }

    @Test
    void duplicateEmailIsRejectedWithAVisibleMessage() throws Exception {
        String email = "grace-" + UUID.randomUUID() + "@example.com";
        registrationService.register(new RegisterUserRequest("Grace Hopper", email, "compiler-1952"));

        mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "Another Grace")
                        .param("email", email)
                        .param("password", "another-password")
                        .param("passwordConfirm", "another-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("already exists")));
    }

    @Test
    void mismatchedPasswordsAreRejected() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("fullName", "Alan Turing")
                        .param("email", "alan-" + UUID.randomUUID() + "@example.com")
                        .param("password", "enigma-machine")
                        .param("passwordConfirm", "different-machine"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Passwords do not match")));
    }

    @Test
    void validCredentialsAuthenticateAndLogoutEndsTheSession() throws Exception {
        String email = register("Katherine Johnson", "orbital-mechanics");

        MvcResult login = mockMvc.perform(formLogin("/login")
                        .user("email", email)
                        .password("password", "orbital-mechanics"))
                .andExpect(authenticated().withUsername(email))
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mockMvc.perform(get("/dashboard").session(session)).andExpect(status().isOk());

        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));

        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void incorrectPasswordDoesNotAuthenticate() throws Exception {
        String email = register("Margaret Hamilton", "apollo-guidance");

        mockMvc.perform(formLogin("/login")
                        .user("email", email)
                        .password("password", "wrong-password"))
                .andExpect(unauthenticated());
    }

    private String register(String name, String password) {
        String email = name.toLowerCase().replace(' ', '.') + "-" + UUID.randomUUID() + "@example.com";
        registrationService.register(new RegisterUserRequest(name, email, password));
        return email;
    }
}
