package com.casegen.interoperability.xmi;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class XmiExportController {
    private static final MediaType XMI_MEDIA_TYPE = MediaType.parseMediaType("application/xml");
    private final XmiExportService service;

    public XmiExportController(XmiExportService service) {
        this.service = service;
    }

    @GetMapping("/api/uml-models/{modelId}/export/xmi")
    public ResponseEntity<byte[]> export(@PathVariable UUID modelId) {
        XmiExportDocument document = service.export(modelId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(document.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(XMI_MEDIA_TYPE)
                .contentLength(document.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(document.content());
    }
}
