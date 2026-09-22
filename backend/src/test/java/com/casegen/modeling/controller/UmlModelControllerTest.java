package com.casegen.modeling.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.casegen.modeling.command.CreateUmlClassifierCommand;
import com.casegen.modeling.command.CreateUmlModelCommand;
import com.casegen.modeling.command.CreateUmlOperationCommand;
import com.casegen.modeling.command.CreateUmlPackageCommand;
import com.casegen.modeling.command.CreateUmlParameterCommand;
import com.casegen.modeling.command.CreateUmlPropertyCommand;
import com.casegen.modeling.domain.ClassifierKind;
import com.casegen.modeling.domain.ParameterDirection;
import com.casegen.modeling.domain.PrimitiveType;
import com.casegen.modeling.domain.TypeKind;
import com.casegen.modeling.domain.UmlElementType;
import com.casegen.modeling.domain.UmlModelStatus;
import com.casegen.modeling.domain.Visibility;
import com.casegen.modeling.dto.UmlClassifierResponse;
import com.casegen.modeling.dto.UmlModelResponse;
import com.casegen.modeling.dto.UmlOperationResponse;
import com.casegen.modeling.dto.UmlPackageResponse;
import com.casegen.modeling.dto.UmlParameterResponse;
import com.casegen.modeling.dto.UmlPropertyResponse;
import com.casegen.modeling.exception.SemanticExceptionHandler;
import com.casegen.modeling.service.UmlModelService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class UmlModelControllerTest {

    private UmlModelService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(UmlModelService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UmlModelController(service))
                .setControllerAdvice(new SemanticExceptionHandler())
                .build();
    }

    @Test
    void createsModelWith201() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-08T00:00:00Z");
        when(service.createModel(eq(projectId), any(CreateUmlModelCommand.class)))
                .thenReturn(new UmlModelResponse(modelId, projectId, "Sales", null, "2.5.1",
                        0, 0, UmlModelStatus.EDITABLE, null, now, now));

        mockMvc.perform(post("/api/projects/{projectId}/uml-models", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sales\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/uml-models/" + modelId))
                .andExpect(jsonPath("$.revision").value(0));
    }

    @Test
    void rejectsBlankModelName() throws Exception {
        mockMvc.perform(post("/api/projects/{projectId}/uml-models", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createsPackageAndReturnsRevision() throws Exception {
        UUID modelId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-08T00:00:00Z");
        when(service.createPackage(eq(modelId), any(CreateUmlPackageCommand.class)))
                .thenReturn(new UmlPackageResponse(packageId, modelId, UmlElementType.PACKAGE,
                        "domain", null, null, 1, now, now));

        mockMvc.perform(post("/api/uml-models/{modelId}/packages", modelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"domain\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.elementType").value("PACKAGE"))
                .andExpect(jsonPath("$.modelRevision").value(1));
    }

    @Test
    void createsClassifierAndReturnsRevision() throws Exception {
        UUID modelId = UUID.randomUUID();
        UUID classifierId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-08T00:00:00Z");
        when(service.createClassifier(eq(modelId), any(CreateUmlClassifierCommand.class)))
                .thenReturn(new UmlClassifierResponse(classifierId, modelId,
                        UmlElementType.CLASSIFIER, "Customer", null, null,
                        ClassifierKind.CLASS, Visibility.PUBLIC, false, false, 2, now, now));

        mockMvc.perform(post("/api/uml-models/{modelId}/classifiers", modelId)
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
                .andExpect(jsonPath("$.kind").value("CLASS"))
                .andExpect(jsonPath("$.modelRevision").value(2));
    }

    @Test
    void exposesReadEndpoints() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-08T00:00:00Z");
        when(service.listModels(projectId)).thenReturn(List.of());
        when(service.listPackages(modelId)).thenReturn(List.of());
        when(service.listClassifiers(modelId)).thenReturn(List.of());
        when(service.getModel(modelId)).thenReturn(new UmlModelResponse(
                modelId, projectId, "Domain", null, "2.5.1", 3, 3,
                UmlModelStatus.EDITABLE, null, now, now));

        mockMvc.perform(get("/api/projects/{projectId}/uml-models", projectId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/uml-models/{modelId}", modelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(3));
        mockMvc.perform(get("/api/uml-models/{modelId}/packages", modelId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/uml-models/{modelId}/classifiers", modelId))
                .andExpect(status().isOk());
    }

    @Test
    void exposesUmlFeatureCreateAndReadEndpoints() throws Exception {
        UUID modelId = UUID.randomUUID();
        UUID classifierId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID parameterId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-09T00:00:00Z");
        when(service.createProperty(eq(modelId), eq(classifierId), any(CreateUmlPropertyCommand.class)))
                .thenReturn(new UmlPropertyResponse(
                        propertyId, modelId, UmlElementType.PROPERTY, "email", null,
                        classifierId, Visibility.PRIVATE, TypeKind.PRIMITIVE, PrimitiveType.STRING,
                        null, 1, 1, false, false, false, 1, now, now));
        when(service.createOperation(eq(modelId), eq(classifierId), any(CreateUmlOperationCommand.class)))
                .thenReturn(new UmlOperationResponse(
                        operationId, modelId, UmlElementType.OPERATION, "register", null,
                        classifierId, Visibility.PUBLIC, false, false, false, 2, now, now));
        when(service.createParameter(eq(modelId), eq(operationId), any(CreateUmlParameterCommand.class)))
                .thenReturn(new UmlParameterResponse(
                        parameterId, modelId, UmlElementType.PARAMETER, "email", null,
                        operationId, ParameterDirection.IN, TypeKind.PRIMITIVE, PrimitiveType.STRING,
                        null, 1, 1, 0, 3, now, now));
        when(service.listProperties(modelId, classifierId)).thenReturn(List.of());
        when(service.listOperations(modelId, classifierId)).thenReturn(List.of());
        when(service.listParameters(modelId, operationId)).thenReturn(List.of());

        mockMvc.perform(post("/api/uml-models/{modelId}/classifiers/{classifierId}/properties",
                        modelId, classifierId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "email",
                                  "typeKind": "PRIMITIVE",
                                  "primitiveType": "STRING",
                                  "lowerBound": 1,
                                  "upperBound": 1
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.elementType").value("PROPERTY"));
        mockMvc.perform(get("/api/uml-models/{modelId}/classifiers/{classifierId}/properties",
                        modelId, classifierId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/uml-models/{modelId}/classifiers/{classifierId}/operations",
                        modelId, classifierId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"register\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.elementType").value("OPERATION"));
        mockMvc.perform(get("/api/uml-models/{modelId}/classifiers/{classifierId}/operations",
                        modelId, classifierId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/uml-models/{modelId}/operations/{operationId}/parameters",
                        modelId, operationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "email",
                                  "direction": "IN",
                                  "typeKind": "PRIMITIVE",
                                  "primitiveType": "STRING",
                                  "position": 0
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("IN"));
        mockMvc.perform(get("/api/uml-models/{modelId}/operations/{operationId}/parameters",
                        modelId, operationId))
                .andExpect(status().isOk());
    }
}
