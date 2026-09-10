package com.casegen.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.casegen.auth.domain.SystemBootstrap;
import com.casegen.auth.domain.SystemRole;
import com.casegen.auth.domain.User;
import com.casegen.auth.dto.RegisterRequest;
import com.casegen.auth.repository.SystemBootstrapRepository;
import com.casegen.auth.repository.UserRepository;
import com.casegen.auth.service.AuthService;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:postgresql:.*")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SuperuserBootstrapIntegrationTest {

    private static final String PASSWORD = "BootstrapPassword123!";

    private final MockMvc mockMvc;
    private final AuthService authService;
    private final UserRepository userRepository;
    private final SystemBootstrapRepository bootstrapRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    SuperuserBootstrapIntegrationTest(
            MockMvc mockMvc,
            AuthService authService,
            UserRepository userRepository,
            SystemBootstrapRepository bootstrapRepository,
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate) {
        this.mockMvc = mockMvc;
        this.authService = authService;
        this.userRepository = userRepository;
        this.bootstrapRepository = bootstrapRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    @Test
    @Transactional
    void firstRegistrationCreatesSuperuserAndLaterRegistrationCannotChooseRole() throws Exception {
        assertEquals(0, userRepository.countBySystemRole(SystemRole.SUPERUSER));
        mockMvc.perform(get("/api/auth/bootstrap-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.superuserCreated").value(false));

        String firstEmail = uniqueEmail("first");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(firstEmail, false)))
                .andExpect(status().isCreated());
        assertEquals(SystemRole.SUPERUSER,
                userRepository.findByEmailIgnoreCase(firstEmail).orElseThrow().getSystemRole());

        String secondEmail = uniqueEmail("second");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(secondEmail, true)))
                .andExpect(status().isCreated());
        assertEquals(SystemRole.USER,
                userRepository.findByEmailIgnoreCase(secondEmail).orElseThrow().getSystemRole());
        assertEquals(1, userRepository.countBySystemRole(SystemRole.SUPERUSER));

        SystemBootstrap bootstrap = bootstrapRepository.findById(SystemBootstrap.SINGLETON_ID).orElseThrow();
        assertTrue(bootstrap.isSuperuserCreated());
        assertEquals(firstEmail, bootstrap.getSuperuserUser().getEmail());
        mockMvc.perform(get("/api/auth/bootstrap-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.superuserCreated").value(true));
    }

    @Test
    void concurrentFirstRegistrationsCreateExactlyOneSuperuser() throws Exception {
        String prefix = "bootstrap-concurrent-" + UUID.randomUUID();
        String firstEmail = prefix + "-1@example.com";
        String secondEmail = prefix + "-2@example.com";
        assertBootstrapNotCompleted();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<SystemRole> first = executor.submit(
                    () -> registerConcurrently(firstEmail, ready, start));
            Future<SystemRole> second = executor.submit(
                    () -> registerConcurrently(secondEmail, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            assertEquals(EnumSet.of(SystemRole.SUPERUSER, SystemRole.USER),
                    EnumSet.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)));
            assertEquals(1, userRepository.countBySystemRole(SystemRole.SUPERUSER));
        } finally {
            executor.shutdownNow();
            cleanupConcurrentUsers(firstEmail, secondEmail);
        }
    }

    private SystemRole registerConcurrently(
            String email, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        authService.register(new RegisterRequest(email, PASSWORD, "Concurrent bootstrap"));
        return userRepository.findByEmailIgnoreCase(email).orElseThrow().getSystemRole();
    }

    private void assertBootstrapNotCompleted() {
        SystemBootstrap bootstrap = bootstrapRepository.findById(SystemBootstrap.SINGLETON_ID).orElseThrow();
        assertFalse(bootstrap.isSuperuserCreated(),
                "Concurrent bootstrap test requires an installation without a SUPERUSER");
        assertEquals(0, userRepository.countBySystemRole(SystemRole.SUPERUSER));
    }

    private void cleanupConcurrentUsers(String firstEmail, String secondEmail) {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    UPDATE system_bootstrap
                    SET superuser_created = FALSE, superuser_user_id = NULL, completed_at = NULL
                    WHERE id = 1
                      AND superuser_user_id IN (
                          SELECT id FROM users WHERE email IN (?, ?)
                      )
                    """, firstEmail, secondEmail);
            jdbcTemplate.update("""
                    DELETE FROM auth_refresh_tokens
                    WHERE user_id IN (SELECT id FROM users WHERE email IN (?, ?))
                    """, firstEmail, secondEmail);
            jdbcTemplate.update("DELETE FROM users WHERE email IN (?, ?)", firstEmail, secondEmail);
        });
    }

    private String uniqueEmail(String prefix) {
        return "bootstrap-" + prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private String registerJson(String email, boolean requestSuperuser) {
        String requestedRole = requestSuperuser ? ",\"systemRole\":\"SUPERUSER\"" : "";
        return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"displayName\":\"Bootstrap test\"" + requestedRole + "}";
    }
}
