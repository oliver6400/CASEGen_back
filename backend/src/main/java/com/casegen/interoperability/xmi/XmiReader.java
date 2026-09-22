package com.casegen.interoperability.xmi;

import org.w3c.dom.Document;

public interface XmiReader {
    XmiImportData read(Document document);
}
