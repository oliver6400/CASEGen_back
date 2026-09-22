package com.casegen.generation.logical.service;

import com.casegen.generation.logical.dto.LogicalModelDtos.*;
import com.casegen.generation.logical.repository.LogicalModelRepository;
import com.casegen.generation.repository.GenerationProfileRepository;
import com.casegen.modeling.service.ModelSnapshotReader;
import com.casegen.project.service.ProjectAuthorizationService;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class LogicalModelService {
    private final LogicalModelRepository repository;
    private final GenerationProfileRepository profiles;
    private final ProjectAuthorizationService authorization;
    private final LogicalModelTransformer transformer;
    private final JsonMapper json=JsonMapper.builder().build();
    public LogicalModelService(LogicalModelRepository repository, GenerationProfileRepository profiles,
            ProjectAuthorizationService authorization, LogicalModelTransformer transformer) {
        this.repository=repository;this.profiles=profiles;this.authorization=authorization;this.transformer=transformer;
    }
    @Transactional
    public Response build(UUID versionId,UUID profileId) {
        Prepared prepared=prepare(versionId,profileId);
        // Canonical field order; generated row IDs, actor and build time never enter the checksum.
        String checksum=ModelSnapshotReader.checksum(canonical(json.valueToTree(Map.of("transformerVersion",1,"semantic",prepared.semantic(),"profile",prepared.profile(),"logical",prepared.plan()))));
        UUID id=UUID.randomUUID();repository.append(id,versionId,profileId,prepared.profile(),prepared.plan(),checksum,prepared.actor());
        return get(id);
    }
    @Transactional
    public Plan preview(UUID versionId,UUID profileId) { return prepare(versionId,profileId).plan(); }
    @Transactional(readOnly=true)
    public List<Summary> list(UUID modelId) {
        UUID projectId=repository.projectId(modelId).orElseThrow(()->notFound("UmlModel not found"));
        authorization.requireRead(projectId);return repository.list(modelId);
    }
    private record Prepared(UUID actor,JsonNode semantic,JsonNode profile,Plan plan) {}
    private Prepared prepare(UUID versionId,UUID profileId) {
        var source=repository.source(versionId).orElseThrow(()->notFound("ModelVersion not found"));
        UUID actor=authorization.requireGenerate(source.projectId()).getId();
        repository.lockProfileBoundary(source.modelId());
        var profile=profiles.profiles(source.modelId()).stream().filter(p->p.id().equals(profileId)).findFirst()
                .orElseThrow(()->notFound("GenerationProfile not found in the version's model"));
        JsonNode frozen=json.valueToTree(Map.of("schemaVersion",1,"profile",profile,"mappings",profiles.mappings(profileId)));
        Plan plan=transformer.transform(source.semantic(),frozen);
        return new Prepared(actor,source.semantic(),frozen,plan);
    }
    @Transactional(readOnly=true)
    public Response get(UUID id) {
        Response row=repository.find(id).orElseThrow(()->notFound("LogicalModel not found"));
        var source=repository.source(row.modelVersionId()).orElseThrow(()->notFound("ModelVersion not found"));
        authorization.requireRead(source.projectId());
        return new Response(row.id(),row.modelVersionId(),row.generationProfileId(),row.profileSnapshot(),row.status(),row.checksum(),row.createdBy(),row.createdAt(),repository.tables(id),row.issues());
    }
    @Transactional(readOnly=true)
    public List<Table> tables(UUID id) { return get(id).tables(); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND,message); }
    private String canonical(JsonNode node) {
        if (node.isObject()) {
            TreeMap<String,JsonNode> fields=new TreeMap<>();node.properties().forEach(e->fields.put(e.getKey(),e.getValue()));
            List<String> parts=new ArrayList<>();fields.forEach((k,v)->parts.add(json.writeValueAsString(k)+":"+canonical(v)));
            return "{"+String.join(",",parts)+"}";
        }
        if (node.isArray()) { List<String> parts=new ArrayList<>();for(JsonNode n:node) parts.add(canonical(n));return "["+String.join(",",parts)+"]"; }
        return json.writeValueAsString(node);
    }
}
