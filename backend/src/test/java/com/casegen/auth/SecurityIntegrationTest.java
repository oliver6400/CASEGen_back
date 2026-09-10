package com.casegen.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.casegen.auth.domain.RefreshToken;
import com.casegen.auth.domain.SystemRole;
import com.casegen.auth.domain.SystemBootstrap;
import com.casegen.auth.domain.User;
import com.casegen.auth.repository.RefreshTokenRepository;
import com.casegen.auth.repository.UserRepository;
import com.casegen.auth.repository.SystemBootstrapRepository;
import com.casegen.auth.service.TokenService;
import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.repository.UmlModelRepository;
import com.casegen.project.domain.Project;
import com.casegen.project.domain.ProjectMember;
import com.casegen.project.domain.ProjectMemberRole;
import com.casegen.project.repository.ProjectMemberRepository;
import com.casegen.project.repository.ProjectRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:postgresql:.*")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SecurityIntegrationTest {

    private static final String PASSWORD = "StrongPassword123!";

    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UmlModelRepository umlModelRepository;
    private final PasswordEncoder passwordEncoder;
    private final SystemBootstrapRepository bootstrapRepository;
    private final JwtDecoder jwtDecoder;
    private final TokenService tokenService;

    SecurityIntegrationTest(
            MockMvc mockMvc,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            UmlModelRepository umlModelRepository,
            SystemBootstrapRepository bootstrapRepository,
            PasswordEncoder passwordEncoder,
            JwtDecoder jwtDecoder,
            TokenService tokenService) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.umlModelRepository = umlModelRepository;
        this.bootstrapRepository = bootstrapRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtDecoder = jwtDecoder;
        this.tokenService = tokenService;
    }

    @Test
    void registersLogsInHashesPasswordAndIssuesValidJwt() throws Exception {
        completeBootstrapForRegularRegistration();
        String email = uniqueEmail("register");
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email).replace(
                                "}", ",\"systemRole\":\"SUPERUSER\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertEquals(SystemRole.USER, user.getSystemRole());
        assertNotEquals(PASSWORD, user.getPasswordHash());
        assertTrue(passwordEncoder.matches(PASSWORD, user.getPasswordHash()));

        String accessToken = jsonValue(registration, "$.accessToken");
        String refreshToken = jsonValue(registration, "$.refreshToken");
        assertEquals(user.getId().toString(), jwtDecoder.decode(accessToken).getSubject());
        RefreshToken storedRefreshToken = refreshTokenRepository
                .findByTokenHash(tokenService.hash(refreshToken)).orElseThrow();
        assertNotEquals(refreshToken, storedRefreshToken.getTokenHash());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rotatesRefreshTokenAndLogoutRevokesReplacement() throws Exception {
        MvcResult registration = register(uniqueEmail("refresh"));
        String first = jsonValue(registration, "$.refreshToken");

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(first)))
                .andExpect(status().isOk())
                .andReturn();
        String replacement = jsonValue(refreshed, "$.refreshToken");
        assertNotEquals(first, replacement);

        RefreshToken storedFirst = refreshTokenRepository.findByTokenHash(tokenService.hash(first)).orElseThrow();
        assertNotNull(storedFirst.getRevokedAt());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(first)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(replacement)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson(replacement)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void viewerCannotModifyModel() throws Exception {
        User viewer = persistUser("viewer", SystemRole.USER);
        Project project = persistProject("Viewer project");
        addMember(project, viewer, ProjectMemberRole.VIEWER);
        UmlModel model = umlModelRepository.saveAndFlush(new UmlModel(project, "Domain", null, viewer));

        mockMvc.perform(post("/api/uml-models/{modelId}/packages", model.getId())
                        .header("Authorization", bearer(login(viewer.getEmail())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"forbidden\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void collaboratorCanCreateClass() throws Exception {
        User collaborator = persistUser("collaborator", SystemRole.USER);
        Project project = persistProject("Collaborator project");
        addMember(project, collaborator, ProjectMemberRole.COLLABORATOR);
        UmlModel model = umlModelRepository.saveAndFlush(new UmlModel(project, "Domain", null, collaborator));

        mockMvc.perform(post("/api/uml-models/{modelId}/classifiers", model.getId())
                        .header("Authorization", bearer(login(collaborator.getEmail())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Customer",
                                  "kind": "CLASS",
                                  "visibility": "PUBLIC",
                                  "isAbstract": false,
                                  "isActive": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("CLASS"));
    }

    @Test
    void ownerCanAdministerProject() throws Exception {
        User owner = persistUser("owner", SystemRole.USER);
        Project project = persistProject("Owned project");
        addMember(project, owner, ProjectMemberRole.OWNER);

        mockMvc.perform(put("/api/v1/projects/{projectId}", project.getId())
                        .header("Authorization", bearer(login(owner.getEmail())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed project\",\"description\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed project"));
    }

    @Test
    void outsiderReceivesForbidden() throws Exception {
        User outsider = persistUser("outsider", SystemRole.USER);
        Project project = persistProject("Private project");

        mockMvc.perform(get("/api/v1/projects/{projectId}", project.getId())
                        .header("Authorization", bearer(login(outsider.getEmail()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void superuserHasAccessWithoutMembership() throws Exception {
        User superuser = persistUser("superuser", SystemRole.SUPERUSER);
        Project project = persistProject("Superuser project");

        mockMvc.perform(get("/api/v1/projects/{projectId}", project.getId())
                        .header("Authorization", bearer(login(superuser.getEmail()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(project.getId().toString()));
    }

    @Test
    void creatingProjectMakesAuthenticatedUserTheOnlyActiveOwner() throws Exception {
        User user = persistUser("creator", SystemRole.USER);

        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(login(user.getEmail())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Secure project\",\"description\":null}"))
                .andExpect(status().isCreated())
                .andReturn();

        UUID projectId = UUID.fromString(jsonValue(result, "$.id"));
        ProjectMember membership = projectMemberRepository
                .findByProjectIdAndUserId(projectId, user.getId()).orElseThrow();
        assertEquals(ProjectMemberRole.OWNER, membership.getRole());
        assertNotNull(membership.getJoinedAt());
        assertFalse(projectMemberRepository.findByProjectId(projectId).isEmpty());
    }

    private MvcResult register(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email)))
                .andExpect(status().isOk())
                .andReturn();
        return jsonValue(result, "$.accessToken");
    }

    private User persistUser(String prefix, SystemRole role) {
        return userRepository.saveAndFlush(new User(
                uniqueEmail(prefix), passwordEncoder.encode(PASSWORD), prefix, role));
    }

    private Project persistProject(String name) {
        return projectRepository.saveAndFlush(new Project(name + " " + UUID.randomUUID(), null));
    }

    private void addMember(Project project, User user, ProjectMemberRole role) {
        projectMemberRepository.saveAndFlush(new ProjectMember(project, user, role));
    }

    private String uniqueEmail(String prefix) {
        return prefix + "." + UUID.randomUUID() + "@example.com";
    }

    private String registerJson(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"displayName\":\"Security test\"}";
    }

    private String loginJson(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
    }

    private String refreshJson(String token) {
        return "{\"refreshToken\":\"" + token + "\"}";
    }

    private String jsonValue(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private void completeBootstrapForRegularRegistration() {
        User bootstrapUser = persistUser("bootstrap", SystemRole.SUPERUSER);
        SystemBootstrap bootstrap = bootstrapRepository
                .findByIdForUpdate(SystemBootstrap.SINGLETON_ID).orElseThrow();
        bootstrap.completeWith(bootstrapUser, java.time.Instant.now());
        bootstrapRepository.saveAndFlush(bootstrap);
    }
}
