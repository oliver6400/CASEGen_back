package com.casegen.interoperability.xmi;

import java.io.ByteArrayInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

@Component
public class SecureXmiParser {
    public static final int MAX_FILE_BYTES = 10 * 1024 * 1024;

    public Document parse(byte[] content) {
        if (content == null || content.length == 0) {
            throw error("XMI_FILE_EMPTY", "The XMI file is empty", null);
        }
        if (content.length > MAX_FILE_BYTES) {
            throw error("XMI_FILE_TOO_LARGE", "The XMI file exceeds the 10 MB limit", null);
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new java.io.StringReader("")));
            return builder.parse(new ByteArrayInputStream(content));
        } catch (Exception exception) {
            throw error("XMI_XML_MALFORMED", "The XMI file is not well-formed or uses unsafe XML", exception);
        }
    }

    private XmiImportException error(String code, String message, Throwable cause) {
        return new XmiImportException(code, HttpStatus.BAD_REQUEST, message, cause);
    }
}
