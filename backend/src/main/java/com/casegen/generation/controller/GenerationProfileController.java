package com.casegen.generation.controller;

import com.casegen.generation.dto.*;
import com.casegen.generation.service.GenerationProfileService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/uml-models/{modelId}/generation-profiles")
public class GenerationProfileController {
    private final GenerationProfileService service;
    public GenerationProfileController(GenerationProfileService service) { this.service=service; }
    @GetMapping public List<GenerationProfileResponse> list(@PathVariable UUID modelId) { return service.list(modelId); }
    @GetMapping("/{profileId}") public GenerationProfileResponse get(@PathVariable UUID modelId,@PathVariable UUID profileId) { return service.get(modelId,profileId); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public GenerationProfileResponse create(@PathVariable UUID modelId,@Valid @RequestBody GenerationProfileRequest request) { return service.save(modelId,null,request); }
    @PutMapping("/{profileId}") public GenerationProfileResponse update(@PathVariable UUID modelId,@PathVariable UUID profileId,@Valid @RequestBody GenerationProfileRequest request) { return service.save(modelId,profileId,request); }
    @DeleteMapping("/{profileId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID modelId,@PathVariable UUID profileId) { service.delete(modelId,profileId); }
    @GetMapping("/{profileId}/mappings") public List<ElementMappingResponse> mappings(@PathVariable UUID modelId,@PathVariable UUID profileId) { return service.mappings(modelId,profileId); }
    @GetMapping("/{profileId}/mappings/{mappingId}") public ElementMappingResponse mapping(@PathVariable UUID modelId,@PathVariable UUID profileId,@PathVariable UUID mappingId) { return service.mapping(modelId,profileId,mappingId); }
    @PostMapping("/{profileId}/mappings") @ResponseStatus(HttpStatus.CREATED) public ElementMappingResponse addMapping(@PathVariable UUID modelId,@PathVariable UUID profileId,@Valid @RequestBody ElementMappingRequest request) { return service.saveMapping(modelId,profileId,null,request); }
    @PutMapping("/{profileId}/mappings/{mappingId}") public ElementMappingResponse updateMapping(@PathVariable UUID modelId,@PathVariable UUID profileId,@PathVariable UUID mappingId,@Valid @RequestBody ElementMappingRequest request) { return service.saveMapping(modelId,profileId,mappingId,request); }
    @DeleteMapping("/{profileId}/mappings/{mappingId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteMapping(@PathVariable UUID modelId,@PathVariable UUID profileId,@PathVariable UUID mappingId) { service.deleteMapping(modelId,profileId,mappingId); }
}
