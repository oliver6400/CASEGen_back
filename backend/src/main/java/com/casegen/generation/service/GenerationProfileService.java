package com.casegen.generation.service;

import com.casegen.generation.dto.*;
import com.casegen.generation.repository.GenerationProfileRepository;
import com.casegen.modeling.service.ModelSnapshotReader;
import java.util.List;
import java.util.UUID;
import javax.lang.model.SourceVersion;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@Service
public class GenerationProfileService {
    private final ModelSnapshotReader snapshots;
    private final GenerationProfileRepository repository;
    public GenerationProfileService(ModelSnapshotReader snapshots, GenerationProfileRepository repository) { this.snapshots=snapshots; this.repository=repository; }
    @Transactional(readOnly=true)
    public List<GenerationProfileResponse> list(UUID modelId) { snapshots.requireModel(modelId,false); return repository.profiles(modelId); }
    @Transactional(readOnly=true)
    public GenerationProfileResponse get(UUID modelId, UUID id) { snapshots.requireModel(modelId,false); return profile(modelId,id); }
    @Transactional
    public GenerationProfileResponse save(UUID modelId, UUID id, GenerationProfileRequest request) {
        snapshots.requireModel(modelId,true);
        boolean create = id == null;
        if (create) id=UUID.randomUUID(); else profile(modelId,id);
        for (String coordinate : List.of(request.basePackage(),request.groupId())) for (String segment : coordinate.split("\\.",-1))
            if (!SourceVersion.isIdentifier(segment) || SourceVersion.isKeyword(segment)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid package/group coordinate");
        repository.save(id,modelId,request,config(request.configJson()),create);
        return profile(modelId,id);
    }
    @Transactional
    public void delete(UUID modelId, UUID id) { snapshots.requireModel(modelId,true); profile(modelId,id); repository.delete(id); }
    @Transactional(readOnly=true)
    public List<ElementMappingResponse> mappings(UUID modelId, UUID profileId) { snapshots.requireModel(modelId,false); profile(modelId,profileId); return repository.mappings(profileId); }
    @Transactional(readOnly=true)
    public ElementMappingResponse mapping(UUID modelId, UUID profileId, UUID id) { snapshots.requireModel(modelId,false); profile(modelId,profileId); return findMapping(profileId,id); }
    @Transactional
    public ElementMappingResponse saveMapping(UUID modelId, UUID profileId, UUID id, ElementMappingRequest request) {
        snapshots.requireModel(modelId,true); profile(modelId,profileId);
        boolean create = id == null;
        if (create) id=UUID.randomUUID(); else findMapping(profileId,id);
        String kind = repository.classifierKind(request.umlElementId(),modelId);
        if (kind == null) throw new ResponseStatusException(HttpStatus.CONFLICT,"Mapping must reference a classifier in this model");
        String target = request.targetKind().name();
        if ((target.equals("JAVA_INTERFACE") && !kind.equals("INTERFACE")) || (!target.equals("JAVA_INTERFACE") && !target.equals("IGNORE") && !kind.equals("CLASS")))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Mapping kind incompatible with classifier");
        repository.saveMapping(id,profileId,request,config(request.configJson()),create);
        return findMapping(profileId,id);
    }
    @Transactional
    public void deleteMapping(UUID modelId, UUID profileId, UUID id) { snapshots.requireModel(modelId,true); profile(modelId,profileId); findMapping(profileId,id); repository.deleteMapping(id); }
    private GenerationProfileResponse profile(UUID modelId, UUID id) { return repository.profiles(modelId).stream().filter(p->p.id().equals(id)).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Profile not found in model")); }
    private ElementMappingResponse findMapping(UUID profileId, UUID id) { return repository.mappings(profileId).stream().filter(m->m.id().equals(id)).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Mapping not found in profile")); }
    private String config(JsonNode node) {
        if (node == null || node.isNull()) return "{}";
        if (!node.isObject()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"configJson must be an object");
        return snapshots.stringify(node);
    }
}
