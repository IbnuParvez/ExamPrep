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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
        // API clients must receive JSON 401, not a redirect whose HTML body they parse as an empty object.
        mockMvc.perform(get("/api/study/" + UUID.randomUUID() + "/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
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

    @Test
    void profileUpdateChangesTheNameEverywhereAndPersists() throws Exception {
        String email = register("Mary Jackson", "wind-tunnel-1958");
        MockHttpSession session = signIn(email, "wind-tunnel-1958");
        String newEmail = "mary.w-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/profile").session(session).with(csrf())
                        .param("fullName", "Mary W. Jackson")
                        .param("email", newEmail)
                        .param("currentPassword", "")
                        .param("newPassword", "")
                        .param("newPasswordConfirm", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile?updated"));

        // The same session must show the edited name everywhere without signing in again.
        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mary W. Jackson")));

        UserAccount account = userAccountRepository.findByEmailIgnoreCase(newEmail).orElseThrow();
        assertThat(account.getFullName()).isEqualTo("Mary W. Jackson");
        assertThat(userAccountRepository.findByEmailIgnoreCase(email)).isEmpty();
    }

    @Test
    void profileUpdateRejectsAnEmailThatBelongsToAnotherAccount() throws Exception {
        String taken = register("Taken Address", "taken-password");
        String email = register("Katherine G. Johnson", "orbital-mechanics-2");
        MockHttpSession session = signIn(email, "orbital-mechanics-2");

        mockMvc.perform(post("/profile").session(session).with(csrf())
                        .param("fullName", "Katherine G. Johnson")
                        .param("email", taken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("already uses that email address")));

        assertThat(userAccountRepository.findByEmailIgnoreCase(taken).orElseThrow().getFullName())
                .isEqualTo("Taken Address");
    }

    @Test
    void profileUpdateChangesThePasswordAfterProvingTheCurrentOne() throws Exception {
        String email = register("Dorothy Vaughan", "fortran-1961");
        MockHttpSession session = signIn(email, "fortran-1961");

        // A wrong current password must not change anything.
        mockMvc.perform(post("/profile").session(session).with(csrf())
                        .param("fullName", "Dorothy Vaughan")
                        .param("email", email)
                        .param("currentPassword", "not-my-password")
                        .param("newPassword", "fortran-1962")
                        .param("newPasswordConfirm", "fortran-1962"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("current password is not correct")));

        mockMvc.perform(post("/profile").session(session).with(csrf())
                        .param("fullName", "Dorothy Vaughan")
                        .param("email", email)
                        .param("currentPassword", "fortran-1961")
                        .param("newPassword", "fortran-1962")
                        .param("newPasswordConfirm", "fortran-1962"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile?updated"));

        mockMvc.perform(formLogin("/login").user("email", email).password("password", "fortran-1962"))
                .andExpect(authenticated().withUsername(email));
        mockMvc.perform(formLogin("/login").user("email", email).password("password", "fortran-1961"))
                .andExpect(unauthenticated());
    }

    @Test
    void deletingTheAccountRemovesItAndSignsTheStudentOut() throws Exception {
        String email = register("Annie Easley", "centaur-1955");
        MockHttpSession session = signIn(email, "centaur-1955");
        UUID accountId = userAccountRepository.findByEmailIgnoreCase(email).orElseThrow().getId();

        // The wrong password keeps the account and the session intact.
        mockMvc.perform(post("/profile/delete").session(session).with(csrf())
                        .param("currentPassword", "wrong-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"));
        mockMvc.perform(get("/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("has not been deleted")));
        assertThat(userAccountRepository.findById(accountId)).isPresent();

        mockMvc.perform(post("/profile/delete").session(session).with(csrf())
                        .param("currentPassword", "centaur-1955"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?deleted"));

        assertThat(userAccountRepository.findById(accountId)).isEmpty();
        // The session that pointed at the deleted account must no longer be authenticated.
        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(formLogin("/login")
                        .user("email", email)
                        .password("password", password))
                .andExpect(authenticated().withUsername(email))
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private String register(String name, String password) {
        String email = name.toLowerCase().replace(' ', '.') + "-" + UUID.randomUUID() + "@example.com";
        registrationService.register(new RegisterUserRequest(name, email, password));
        return email;
    }
}
