package com.casegen.interoperability.image;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ImageImportController {
    private final ImageImportService service;

    public ImageImportController(ImageImportService service) {
        this.service = service;
    }

    @GetMapping("/api/image-import/status")
    public ImageImportConfigurationResponse status() {
        return service.configuration();
    }

    @PostMapping(value = "/api/projects/{projectId}/image-import/analyze",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DetectedClassDiagram analyze(@PathVariable UUID projectId,
            @RequestPart("file") MultipartFile file) {
        try {
            return service.analyze(projectId, file.getBytes(), file.getContentType(), file.getOriginalFilename());
        } catch (IOException exception) {
            throw new ImageImportException("IMAGE_FILE_UNREADABLE", org.springframework.http.HttpStatus.BAD_REQUEST,
                    "The uploaded image could not be read", exception);
        }
    }

    @PostMapping(value = "/api/projects/{projectId}/image-import/confirm",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ImageImportResponse> confirm(@PathVariable UUID projectId,
            @RequestBody DetectedClassDiagram proposal) {
        ImageImportResponse response = service.confirm(projectId, proposal);
        return ResponseEntity.created(URI.create("/api/uml-models/" + response.modelId())).body(response);
    }
}
