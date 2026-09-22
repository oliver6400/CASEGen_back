package com.casegen.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.UmlClassifier;
import com.casegen.modeling.domain.UmlElement;
import com.casegen.modeling.domain.UmlElementType;
import com.casegen.modeling.domain.Visibility;
import com.casegen.modeling.repository.UmlClassifierRepository;
import com.casegen.modeling.repository.UmlElementRepository;
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
    private final UmlElementRepository umlElementRepository;
    private final UmlClassifierRepository umlClassifierRepository;
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
            UmlElementRepository umlElementRepository,
            UmlClassifierRepository umlClassifierRepository,
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
        this.umlElementRepository = umlElementRepository;
        this.umlClassifierRepository = umlClassifierRepository;
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
    void allowsConfiguredWebOriginPreflight() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
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
    void collaboratorCanCreatePropertyWhileViewerCanOnlyReadIt() throws Exception {
        User collaborator = persistUser("feature-collaborator", SystemRole.USER);
        User viewer = persistUser("feature-viewer", SystemRole.USER);
        Project project = persistProject("Feature permissions project");
        addMember(project, collaborator, ProjectMemberRole.COLLABORATOR);
        addMember(project, viewer, ProjectMemberRole.VIEWER);
        UmlModel model = umlModelRepository.saveAndFlush(new UmlModel(project, "Domain", null, collaborator));
        UmlElement classifierElement = umlElementRepository.save(new UmlElement(
                model, UmlElementType.CLASSIFIER, "Customer", null));
        UmlClassifier classifier = umlClassifierRepository.saveAndFlush(new UmlClassifier(
                classifierElement, null, ClassifierKind.CLASS, Visibility.PUBLIC, false, false));
        String propertyJson = """
                {
                  "name": "email",
                  "typeKind": "PRIMITIVE",
                  "primitiveType": "STRING",
                  "lowerBound": 1,
                  "upperBound": 1
                }
                """;

        mockMvc.perform(post("/api/uml-models/{modelId}/classifiers/{classifierId}/properties",
                        model.getId(), classifier.getElementId())
                        .header("Authorization", bearer(login(collaborator.getEmail())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propertyJson))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/uml-models/{modelId}/classifiers/{classifierId}/properties",
                        model.getId(), classifier.getElementId())
                        .header("Authorization", bearer(login(viewer.getEmail()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("email"));

        mockMvc.perform(post("/api/uml-models/{modelId}/classifiers/{classifierId}/properties",
                        model.getId(), classifier.getElementId())
                        .header("Authorization", bearer(login(viewer.getEmail())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(propertyJson.replace("email", "forbidden")))
                .andExpect(status().isForbidden());
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
        User superuser = userRepository.findFirstBySystemRole(SystemRole.SUPERUSER)
                .orElseGet(() -> persistUser("superuser", SystemRole.SUPERUSER));
        Project project = persistProject("Superuser project");

        mockMvc.perform(get("/api/v1/projects/{projectId}", project.getId())
                        .header("Authorization", bearer(tokenService.issueTokenPair(superuser).accessToken())))
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

        mockMvc.perform(get("/api/projects/{projectId}/access", projectId)
                        .header("Authorization", bearer(tokenService.issueTokenPair(user).accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectRole").value("OWNER"))
                .andExpect(jsonPath("$.canEdit").value(true));
    }

    @Test
    void projectAccessReportsOwnerCollaboratorViewerAndSuperuserCapabilities() throws Exception {
        Project project = persistProject("Access capabilities");
        User owner = persistUser("access-owner", SystemRole.USER);
        User collaborator = persistUser("access-collaborator", SystemRole.USER);
        User viewer = persistUser("access-viewer", SystemRole.USER);
        User superuser = userRepository.findFirstBySystemRole(SystemRole.SUPERUSER)
                .orElseGet(() -> persistUser("access-superuser", SystemRole.SUPERUSER));
        addMember(project, owner, ProjectMemberRole.OWNER);
        addMember(project, collaborator, ProjectMemberRole.COLLABORATOR);
        addMember(project, viewer, ProjectMemberRole.VIEWER);

        mockMvc.perform(get("/api/projects/{projectId}/access", project.getId())
                        .header("Authorization", bearer(tokenService.issueTokenPair(owner).accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectRole").value("OWNER"))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.canManageMembers").value(true));

        mockMvc.perform(get("/api/projects/{projectId}/access", project.getId())
                        .header("Authorization", bearer(tokenService.issueTokenPair(collaborator).accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectRole").value("COLLABORATOR"))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.canManageMembers").value(false));

        mockMvc.perform(get("/api/projects/{projectId}/access", project.getId())
                        .header("Authorization", bearer(tokenService.issueTokenPair(viewer).accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectRole").value("VIEWER"))
                .andExpect(jsonPath("$.canView").value(true))
                .andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.canGenerate").value(false));

        mockMvc.perform(get("/api/projects/{projectId}/access", project.getId())
                        .header("Authorization", bearer(tokenService.issueTokenPair(superuser).accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemRole").value("SUPERUSER"))
                .andExpect(jsonPath("$.projectRole").doesNotExist())
                .andExpect(jsonPath("$.canEdit").value(true));
    }

    @Test
    void projectAccessRejectsOutsider() throws Exception {
        Project project = persistProject("Private access");
        User outsider = persistUser("access-outsider", SystemRole.USER);

        mockMvc.perform(get("/api/projects/{projectId}/access", project.getId())
                        .header("Authorization", bearer(tokenService.issueTokenPair(outsider).accessToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlySuperuserCanListAdminDashboardData() throws Exception {
        User superuser = userRepository.findFirstBySystemRole(SystemRole.SUPERUSER)
                .orElseGet(() -> persistUser("admin-dashboard", SystemRole.SUPERUSER));
        User regularUser = persistUser("admin-denied", SystemRole.USER);
        Project project = persistProject("Admin dashboard project");
        addMember(project, regularUser, ProjectMemberRole.OWNER);
        String superuserToken = bearer(tokenService.issueTokenPair(superuser).accessToken());

        mockMvc.perform(get("/api/admin/users").header("Authorization", superuserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        mockMvc.perform(get("/api/admin/projects").header("Authorization", superuserToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/project-memberships").header("Authorization", superuserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.projectId == '%s')]", project.getId()).exists());

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", bearer(tokenService.issueTokenPair(regularUser).accessToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void classifierNamesAreCaseInsensitiveWithinPackageButReusableAcrossPackages() throws Exception {
        User superuser = userRepository.findFirstBySystemRole(SystemRole.SUPERUSER)
                .orElseGet(() -> persistUser("classifier-name", SystemRole.SUPERUSER));
        Project project = persistProject("Classifier names");
        UmlModel model = umlModelRepository.saveAndFlush(new UmlModel(project, "Domain", null, superuser));
        String authorization = bearer(tokenService.issueTokenPair(superuser).accessToken());

        UUID salesId = UUID.fromString(jsonValue(mockMvc.perform(post(
                        "/api/uml-models/{modelId}/packages", model.getId())
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"sales\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.id"));
        UUID crmId = UUID.fromString(jsonValue(mockMvc.perform(post(
                        "/api/uml-models/{modelId}/packages", model.getId())
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"crm\"}"))
                .andExpect(status().isCreated()).andReturn(), "$.id"));

        mockMvc.perform(post("/api/uml-models/{modelId}/classifiers", model.getId())
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(classifierJson("Customer", salesId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/uml-models/{modelId}/classifiers", model.getId())
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(classifierJson("customer", salesId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLASSIFIER_NAME_ALREADY_EXISTS"));
        UUID accountId = UUID.fromString(jsonValue(mockMvc.perform(post(
                        "/api/uml-models/{modelId}/classifiers", model.getId())
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(classifierJson("Account", salesId)))
                .andExpect(status().isCreated()).andReturn(), "$.id"));
        mockMvc.perform(put("/api/uml-models/{modelId}/classifiers/{classifierId}",
                        model.getId(), accountId)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(classifierJson("CUSTOMER", salesId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLASSIFIER_NAME_ALREADY_EXISTS"));
        mockMvc.perform(post("/api/uml-models/{modelId}/classifiers", model.getId())
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(classifierJson("Customer", crmId)))
                .andExpect(status().isCreated());
    }

    @Test
    void viewerCannotCreateUmlRelationship() throws Exception {
        User viewer = persistUser("relationship-viewer", SystemRole.USER);
        Project project = persistProject("Relationship permissions");
        addMember(project, viewer, ProjectMemberRole.VIEWER);
        UmlModel model = umlModelRepository.saveAndFlush(new UmlModel(project, "Domain", null, viewer));
        UmlClassifier first = persistClassifier(model, "Customer", ClassifierKind.CLASS);
        UmlClassifier second = persistClassifier(model, "Order", ClassifierKind.CLASS);

        mockMvc.perform(post("/api/uml-models/{modelId}/associations", model.getId())
                        .header("Authorization", bearer(tokenService.issueTokenPair(viewer).accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstEnd": {"classifierId":"%s","lowerBound":1,"upperBound":1},
                                  "secondEnd": {"classifierId":"%s","lowerBound":0,"upperBound":null}
                                }
                                """.formatted(first.getElementId(), second.getElementId())))
                .andExpect(status().isForbidden());
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

    private UmlClassifier persistClassifier(UmlModel model, String name, ClassifierKind kind) {
        UmlElement element = umlElementRepository.saveAndFlush(
                new UmlElement(model, UmlElementType.CLASSIFIER, name, null));
        return umlClassifierRepository.saveAndFlush(
                new UmlClassifier(element, null, kind, Visibility.PUBLIC, false, false));
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

    private String classifierJson(String name, UUID packageId) {
        return "{\"name\":\"" + name + "\",\"packageId\":\"" + packageId
                + "\",\"kind\":\"CLASS\",\"visibility\":\"PUBLIC\","
                + "\"isAbstract\":false,\"isActive\":false}";
    }

    private String jsonValue(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private void completeBootstrapForRegularRegistration() {
        SystemBootstrap bootstrap = bootstrapRepository
                .findByIdForUpdate(SystemBootstrap.SINGLETON_ID).orElseThrow();
        if (!bootstrap.isSuperuserCreated()) {
            User bootstrapUser = userRepository.findFirstBySystemRole(SystemRole.SUPERUSER)
                    .orElseGet(() -> persistUser("bootstrap", SystemRole.SUPERUSER));
            bootstrap.completeWith(bootstrapUser, java.time.Instant.now());
            bootstrapRepository.saveAndFlush(bootstrap);
        }
    }
}
