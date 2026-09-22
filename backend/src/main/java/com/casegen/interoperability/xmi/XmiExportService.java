package com.casegen.interoperability.xmi;

import com.casegen.modeling.domain.UmlModel;
import com.casegen.modeling.service.ModelSnapshotReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class XmiExportService {
    private final ModelSnapshotReader snapshots;
    private final UmlXmiWriter writer;

    public XmiExportService(ModelSnapshotReader snapshots, UmlXmiWriter writer) {
        this.snapshots = snapshots;
        this.writer = writer;
    }

    @Transactional(readOnly = true)
    public XmiExportDocument export(UUID modelId) {
        UmlModel model = snapshots.requireModel(modelId, false);
        byte[] content = writer.write(snapshots.parse(snapshots.semantic(modelId)))
                .getBytes(StandardCharsets.UTF_8);
        return new XmiExportDocument(fileName(model.getName()), content);
    }

    private String fileName(String modelName) {
        String normalized = Normalizer.normalize(modelName, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return (normalized.isBlank() ? "uml-model" : normalized) + ".xmi";
    }
}
