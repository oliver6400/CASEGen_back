package com.casegen.interoperability.xmi;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class XmiImportController {
    private final XmiImportService service;

    public XmiImportController(XmiImportService service) {
        this.service = service;
    }

    @PostMapping(value = "/api/projects/{projectId}/xmi/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<XmiImportResponse> importXmi(@PathVariable UUID projectId,
            @RequestPart("file") MultipartFile file) {
        try {
            XmiImportResponse response = service.importModel(projectId, file.getBytes());
            return ResponseEntity.created(URI.create("/api/uml-models/" + response.modelId())).body(response);
        } catch (IOException exception) {
            throw new XmiImportException("XMI_FILE_UNREADABLE", HttpStatus.BAD_REQUEST,
                    "The uploaded XMI file could not be read", exception);
        }
    }
}
