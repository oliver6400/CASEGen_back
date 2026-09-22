package com.casegen.interoperability.xmi;

import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

final class XmiDom {
    private XmiDom() {}

    static String local(Node node) {
        String local = node.getLocalName();
        if (local != null) return local;
        String name = node.getNodeName();
        int colon = name.indexOf(':');
        return colon < 0 ? name : name.substring(colon + 1);
    }

    static String attribute(Element element, String... names) {
        NamedNodeMap attributes = element.getAttributes();
        for (String expected : names) {
            for (int index = 0; index < attributes.getLength(); index++) {
                Node attribute = attributes.item(index);
                if (expected.equals(attribute.getNodeName()) || expected.equals(local(attribute))) {
                    String value = attribute.getNodeValue();
                    if (value != null && !value.isBlank()) return value.trim();
                }
            }
        }
        return null;
    }

    static String id(Element element) { return attribute(element, "xmi:id", "xmi.id", "id"); }
    static String idref(Element element) { return attribute(element, "xmi:idref", "xmi.idref", "idref"); }
    static String type(Element element) { return attribute(element, "xmi:type", "xmi.type", "type"); }

    static List<Element> children(Element parent) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) result.add(element);
        }
        return result;
    }

    static List<Element> children(Element parent, String localName) {
        return children(parent).stream().filter(child -> localName.equals(local(child))).toList();
    }

    static Element child(Element parent, String localName) {
        return children(parent, localName).stream().findFirst().orElse(null);
    }

    static List<Element> descendants(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        collect(parent, localName, result);
        return result;
    }

    private static void collect(Element parent, String localName, List<Element> result) {
        for (Element child : children(parent)) {
            if (localName.equals(local(child))) result.add(child);
            collect(child, localName, result);
        }
    }

    static boolean bool(Element element, String name, boolean fallback) {
        String value = attribute(element, name);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }
}
