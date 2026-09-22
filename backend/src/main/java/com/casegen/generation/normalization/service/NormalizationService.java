package com.casegen.generation.normalization.service;

import com.casegen.generation.logical.service.LogicalModelService;
import com.casegen.generation.normalization.dto.NormalizationDtos.FormAssessment;
import com.casegen.generation.normalization.dto.NormalizationDtos.Response;
import com.casegen.generation.normalization.repository.NormalizationRepository;
import com.casegen.project.service.ProjectAuthorizationService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NormalizationService {
    private final NormalizationRepository repository;
    private final LogicalModelService logicalModels;
    private final ProjectAuthorizationService authorization;
    private final NormalizationAnalyzer analyzer;
    private final tools.jackson.databind.json.JsonMapper json = tools.jackson.databind.json.JsonMapper.builder().build();

    public NormalizationService(NormalizationRepository repository, LogicalModelService logicalModels,
            ProjectAuthorizationService authorization, NormalizationAnalyzer analyzer) {
        this.repository = repository; this.logicalModels = logicalModels;
        this.authorization = authorization; this.analyzer = analyzer;
    }

    @Transactional
    public Response analyze(UUID logicalModelId) {
        var source = repository.sourceByLogicalModel(logicalModelId)
                .orElseThrow(() -> notFound("LogicalModel not found"));
        UUID actor = authorization.requireGenerate(source.projectId()).getId();
        var logicalModel = logicalModels.get(logicalModelId);
        var result = analyzer.analyze(logicalModel.tables());
        UUID runId = UUID.randomUUID();
        var references = repository.references(logicalModelId);
        repository.append(runId, logicalModelId, result.status(), result.highestNormalForm(), actor,
                enrichSourceMetadata(result.issues()), references);
        return get(runId);
    }

    @Transactional(readOnly = true)
    public List<Response> list(UUID logicalModelId) {
        var source = repository.sourceByLogicalModel(logicalModelId)
                .orElseThrow(() -> notFound("LogicalModel not found"));
        authorization.requireRead(source.projectId());
        return repository.list(logicalModelId).stream().map(this::assess).toList();
    }

    @Transactional(readOnly = true)
    public Response get(UUID runId) {
        var source = repository.sourceByRun(runId).orElseThrow(() -> notFound("NormalizationRun not found"));
        authorization.requireRead(source.projectId());
        return assess(repository.find(runId).orElseThrow(() -> notFound("NormalizationRun not found")));
    }

    private List<NormalizationAnalyzer.Issue> enrichSourceMetadata(List<NormalizationAnalyzer.Issue> issues) {
        return issues.stream().map(issue -> {
            var fields = new java.util.LinkedHashMap<String, Object>();
            issue.metadata().properties().forEach(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.put("columns", issue.columns());
            if (issue.sourceElementId() != null) fields.put("sourceElementId", issue.sourceElementId());
            return new NormalizationAnalyzer.Issue(issue.tableName(), issue.columnName(), issue.columns(),
                    issue.normalForm(), issue.severity(), issue.code(), issue.message(), issue.recommendation(),
                    issue.sourceElementId(), json.valueToTree(fields));
        }).toList();
    }

    private Response assess(Response response) {
        List<FormAssessment> forms = List.of(assessment("1NF", response), assessment("2NF", response), assessment("3NF", response));
        return new Response(response.id(), response.logicalModelId(), response.status(), response.highestNormalForm(),
                response.createdBy(), response.createdAt(), forms, response.issues());
    }

    private FormAssessment assessment(String form, Response response) {
        var issues = response.issues().stream().filter(issue -> issue.normalForm().equals(form)).toList();
        String status = issues.stream().anyMatch(issue -> issue.severity().equals("ERROR")) ? "FAIL"
                : issues.isEmpty() ? "PASS" : "WARNING";
        String statement = form.equals("3NF") && status.equals("PASS")
                ? "Cumple 3FN respecto a las dependencias conocidas por CASEGen."
                : status.equals("PASS") ? "Sin problemas detectados por CASEGen para " + form + "."
                : "Revisión requerida: " + issues.size() + " hallazgo(s) en " + form + ".";
        return new FormAssessment(form, status, statement);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
