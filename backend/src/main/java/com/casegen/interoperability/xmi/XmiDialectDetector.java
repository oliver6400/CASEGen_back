package com.casegen.interoperability.xmi;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Component
public class XmiDialectDetector {
    public XmiDialect detect(Document document) {
        Element root = document.getDocumentElement();
        String version = first(root.getAttribute("xmi.version"), root.getAttribute("xmi:version"));
        boolean legacy = "1.1".equals(version) || document.getElementsByTagName("XMI.content").getLength() > 0;
        if (legacy) return XmiDialect.EA_XMI_1_1_UML_1_3;
        var docs = document.getElementsByTagNameNS("*", "Documentation");
        if (docs.getLength() > 0
                && ((Element) docs.item(0)).getAttribute("exporter").toLowerCase().contains("enterprise architect")) {
            return XmiDialect.EA_XMI_1_1_UML_1_3;
        }
        if (document.getElementsByTagNameNS("*", "Model").getLength() > 0) {
            return XmiDialect.CASEGEN_UML_2_5_1;
        }
        throw new XmiImportException("UNSUPPORTED_XMI_DIALECT", HttpStatus.BAD_REQUEST,
                "The XMI dialect is not supported");
    }

    private String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }
}
