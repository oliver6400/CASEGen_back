package com.casegen.interoperability.xmi;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

@Component
public class CasegenXmiReader implements XmiReader {
    @Override
    public XmiImportData read(Document document) {
        return new Uml25XmiReader().read(document);
    }
}
