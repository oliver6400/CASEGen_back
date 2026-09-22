package com.casegen.interoperability.xmi;

import static com.casegen.interoperability.xmi.XmiDom.*;
import static com.casegen.interoperability.xmi.XmiImportData.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Component
public class EnterpriseArchitectXmiReader implements XmiReader {
    @Override
    public XmiImportData read(Document document) {
        if (!descendants(document.getDocumentElement(), "packagedElement").isEmpty()) {
            return new Uml25XmiReader().read(document);
        }
        return new LegacyEaReader().read(document);
    }

    /** Adapter for Enterprise Architect XMI 1.1 / UML 1.3 documents. */
    private static final class LegacyEaReader {
        private final List<PackageData> packages = new ArrayList<>();
        private final List<ClassifierData> classifiers = new ArrayList<>();
        private final List<PropertyData> properties = new ArrayList<>();
        private final List<OperationData> operations = new ArrayList<>();
        private final List<ParameterData> parameters = new ArrayList<>();
        private final List<AssociationData> associations = new ArrayList<>();
        private final List<GeneralizationData> generalizations = new ArrayList<>();
        private final List<RealizationData> realizations = new ArrayList<>();
        private final List<XmiImportWarning> warnings = new ArrayList<>();
        private final Map<String, String> dataTypes = new HashMap<>();
        private final Set<String> classifierIds = new HashSet<>();
        private final Map<String, String> classifierKinds = new HashMap<>();
        private final Map<Element, String> resolvedIds = new IdentityHashMap<>();
        private final Set<String> externalIds = new HashSet<>();
        private final Set<String> eaGuids = new HashSet<>();

        XmiImportData read(Document document) {
            Element model = descendants(document.getDocumentElement(), "Model").stream().findFirst()
                    .orElseThrow(() -> invalid("XMI_MODEL_MISSING", "The EA XMI document has no UML Model"));
            for (Element type : descendants(model, "DataType")) {
                if (id(type) != null && attribute(type, "name") != null) {
                    dataTypes.put(externalReference(id(type)), attribute(type, "name"));
                }
            }
            for (Element owned : ownedElements(model)) readOwned(owned, null);
            readRelationships(model);
            return new XmiImportData(value(attribute(model, "name"), "Imported EA model"), id(model),
                    List.copyOf(packages), List.copyOf(classifiers), List.copyOf(properties),
                    List.copyOf(operations), List.copyOf(parameters), List.copyOf(associations),
                    List.copyOf(generalizations), List.copyOf(realizations), List.copyOf(warnings));
        }

        private List<Element> ownedElements(Element owner) {
            Element namespace = child(owner, "Namespace.ownedElement");
            return namespace == null ? List.of() : children(namespace);
        }

        private void readOwned(Element element, String packageId) {
            if ("Class".equals(local(element)) && isEaRootInfrastructure(element, packageId)) {
                String skippedId = id(element) == null ? null : externalReference(id(element));
                warnings.add(new XmiImportWarning("EA_INFRASTRUCTURE_ELEMENT_SKIPPED",
                        "Enterprise Architect EARootClass infrastructure element was not imported", skippedId));
                return;
            }
            switch (local(element)) {
                case "Package" -> readPackage(element, packageId);
                case "Class", "Interface" -> readClassifier(element, packageId);
                case "Association", "Generalization", "Abstraction", "Dependency", "DataType" -> { }
                default -> warnings.add(new XmiImportWarning("UNSUPPORTED_XMI_ELEMENT",
                        "Unsupported EA UML 1.3 element ignored: " + local(element), id(element)));
            }
        }

        private void readPackage(Element element, String parentId) {
            String externalId = resolveEaExternalId(element,
                    new EaIdentityContext(parentId, "PACKAGE", packages.size(), requiredName(element, "Package")));
            packages.add(new PackageData(externalId, requiredName(element, "Package"), parentId));
            for (Element owned : ownedElements(element)) readOwned(owned, externalId);
        }

        private void readClassifier(Element element, String packageId) {
            String externalId = resolveEaExternalId(element,
                    new EaIdentityContext(packageId, local(element).toUpperCase(Locale.ROOT), classifiers.size(),
                            requiredName(element, local(element))));
            classifierIds.add(externalId);
            String stereotype = value(attribute(element, "stereotype"), taggedValue(element, "stereotype"));
            boolean interfaceClassifier = "Interface".equals(local(element))
                    || "interface".equalsIgnoreCase(stereotype);
            classifierKinds.put(externalId, interfaceClassifier ? "INTERFACE" : "CLASS");
            classifiers.add(new ClassifierData(externalId, requiredName(element, local(element)), packageId,
                    interfaceClassifier ? "INTERFACE" : "CLASS", visibility(element, "PUBLIC"),
                    bool(element, "isAbstract", interfaceClassifier),
                    !interfaceClassifier && bool(element, "isActive", false)));
            Element features = child(element, "Classifier.feature");
            if (features == null) return;
            int attributePosition = 0;
            int operationPosition = 0;
            for (Element feature : children(features)) {
                if ("Attribute".equals(local(feature))) readAttribute(feature, externalId, attributePosition++);
                else if ("Operation".equals(local(feature))) readOperation(feature, externalId, operationPosition++);
            }
        }

        private void readAttribute(Element element, String ownerId, int fallbackPosition) {
            String name = requiredName(element, "Attribute");
            int position = position(element, fallbackPosition);
            properties.add(new PropertyData(resolveEaExternalId(element,
                    new EaIdentityContext(ownerId, "ATTRIBUTE", position, name)), name,
                    ownerId, visibility(element, "PRIVATE"), typedValue(element), lower(element), upper(element),
                    bool(element, "ownerScope", false) || bool(element, "isStatic", false),
                    "frozen".equalsIgnoreCase(attribute(element, "changeability")),
                    bool(element, "isDerived", false)));
        }

        private void readOperation(Element element, String ownerId, int fallbackPosition) {
            String name = requiredName(element, "Operation");
            int operationPosition = position(element, fallbackPosition);
            String operationId = resolveEaExternalId(element,
                    new EaIdentityContext(ownerId, "OPERATION", operationPosition, name));
            operations.add(new OperationData(operationId, name, ownerId,
                    visibility(element, "PUBLIC"), bool(element, "isAbstract", false),
                    bool(element, "ownerScope", false) || bool(element, "isStatic", false),
                    bool(element, "isQuery", false)));
            int fallbackParameterPosition = 0;
            Element parameterContainer = child(element, "BehavioralFeature.parameter");
            List<Element> values = parameterContainer == null ? descendants(element, "Parameter")
                    : children(parameterContainer, "Parameter");
            List<ReturnCandidate> returnCandidates = new ArrayList<>();
            int sourceOrder = 0;
            for (Element parameter : values) {
                String direction = value(attribute(parameter, "kind", "direction"), "in").toUpperCase(Locale.ROOT);
                if ("RETURN".equals(direction) || "RESULT".equals(direction)) direction = "RETURN";
                String parameterName = "RETURN".equals(direction) ? attribute(parameter, "name")
                        : requiredName(parameter, "Parameter");
                int parameterPosition = position(parameter, fallbackParameterPosition++);
                String identityName = value(parameterName, "return");
                ParameterData parsed = new ParameterData(resolveEaExternalId(parameter,
                        new EaIdentityContext(operationId, "PARAMETER", parameterPosition, identityName)),
                        parameterName, operationId, direction, typedValue(parameter), lower(parameter), upper(parameter),
                        parameterPosition);
                if ("RETURN".equals(direction)) {
                    returnCandidates.add(new ReturnCandidate(parsed, sourceOrder));
                } else {
                    parameters.add(parsed);
                }
                sourceOrder++;
            }
            normalizeReturnParameters(element, operationId, name, returnCandidates, fallbackParameterPosition);
        }

        private void normalizeReturnParameters(Element operation, String operationId, String operationName,
                List<ReturnCandidate> candidates, int fallbackPosition) {
            String taggedTypeName = taggedValue(operation, "type");
            boolean taggedReturnPresent = taggedTypeName != null && !taggedTypeName.isBlank()
                    && !"void".equalsIgnoreCase(taggedTypeName.trim());
            TypedValue taggedType = taggedReturnPresent ? primitive(taggedTypeName.trim()) : null;

            if (candidates.isEmpty()) {
                if (taggedType != null) {
                    String externalId = registerSyntheticExternalId(new EaIdentityContext(
                            operationId, "PARAMETER_RETURN", fallbackPosition, "return"));
                    parameters.add(new ParameterData(externalId, null, operationId, "RETURN", taggedType,
                            1, 1, fallbackPosition));
                }
                return;
            }

            ReturnCandidate baseline = candidates.getFirst();
            boolean equivalent = candidates.stream().allMatch(candidate -> equivalentReturn(
                    baseline.parameter(), candidate.parameter()));
            if (!equivalent) {
                throw ambiguousReturn(operationName, candidates,
                        "EA operation contains conflicting explicit RETURN parameters");
            }
            if (taggedType != null && !taggedType.equals(baseline.parameter().type())) {
                throw ambiguousReturn(operationName, candidates,
                        "EA operation tagged return type " + describeType(taggedType)
                                + " contradicts its explicit RETURN parameter");
            }

            ReturnCandidate retained = candidates.stream().min(Comparator
                    .comparing((ReturnCandidate candidate) -> !blank(candidate.parameter().name()))
                    .thenComparing(candidate -> !candidate.parameter().externalId()
                            .toUpperCase(Locale.ROOT).contains("RETURNID"))
                    .thenComparingInt(candidate -> candidate.parameter().position())
                    .thenComparingInt(ReturnCandidate::sourceOrder)).orElseThrow();
            parameters.add(retained.parameter());

            if (candidates.size() > 1) {
                List<String> discarded = candidates.stream().filter(candidate -> candidate != retained)
                        .map(candidate -> candidate.parameter().externalId()).toList();
                warnings.add(new XmiImportWarning("DUPLICATE_RETURN_PARAMETER_NORMALIZED",
                        "Equivalent duplicate RETURN parameters were normalized for operation " + operationName,
                        retained.parameter().externalId(), Map.of(
                                "operation", operationName,
                                "operationExternalId", operationId,
                                "discardedExternalIds", discarded,
                                "retainedExternalId", retained.parameter().externalId(),
                                "returnType", describeType(retained.parameter().type()))));
            }
        }

        private boolean equivalentReturn(ParameterData first, ParameterData second) {
            return first.type().equals(second.type())
                    && first.lower() == second.lower()
                    && java.util.Objects.equals(first.upper(), second.upper());
        }

        private XmiImportException ambiguousReturn(String operationName, List<ReturnCandidate> candidates,
                String reason) {
            String details = candidates.stream().map(candidate -> candidate.parameter().externalId() + "="
                    + describeType(candidate.parameter().type()) + "[" + candidate.parameter().lower() + ".."
                    + (candidate.parameter().upper() == null ? "*" : candidate.parameter().upper()) + "]")
                    .toList().toString();
            return invalid("AMBIGUOUS_RETURN_PARAMETER", reason + " in operation " + operationName
                    + ". Returns: " + details);
        }

        private String describeType(TypedValue type) {
            return "PRIMITIVE".equals(type.typeKind()) ? type.primitiveType() : type.classifierExternalId();
        }

        private boolean blank(String value) {
            return value == null || value.isBlank();
        }

        private void readRelationships(Element model) {
            for (Element association : descendants(model, "Association")) {
                readAssociation(association);
            }
            for (Element generalization : descendants(model, "Generalization")) {
                String specific = reference(generalization, "child", "subtype");
                String general = reference(generalization, "parent", "supertype");
                String parentId = value(containingPackage(generalization), externalReference(id(model)));
                String relationId = resolveEaExternalId(generalization,
                        new EaIdentityContext(parentId, "GENERALIZATION", generalizations.size(), "generalization"));
                generalizations.add(new GeneralizationData(relationId, requiredRef(specific, "specific"),
                        requiredRef(general, "general"), bool(generalization, "isSubstitutable", true)));
            }
            for (Element relation : descendants(model, "Abstraction")) readRealization(relation);
            for (Element relation : descendants(model, "Dependency")) readRealization(relation);
        }

        private void readAssociation(Element association) {
            String packageId = containingPackage(association);
            String associationId = resolveEaExternalId(association,
                    new EaIdentityContext(value(packageId, "model"), "ASSOCIATION", associations.size(),
                            value(attribute(association, "name"), "association")));
            Element connection = child(association, "Association.connection");
            List<Element> ends = connection == null ? descendants(association, "AssociationEnd")
                    : children(connection, "AssociationEnd");
            if (ends.size() != 2) throw invalid("XMI_ASSOCIATION_END_COUNT",
                    "Association " + id(association) + " must contain exactly two ends");
            RawEnd first = rawEnd(ends.get(0), associationId, 0);
            RawEnd second = rawEnd(ends.get(1), associationId, 1);
            String source = associationEndpoint(association, true);
            String target = associationEndpoint(association, false);
            first = first.withNavigable(resolveEaNavigability(association, ends.get(0), first.classifier(),
                    source, target, 0, first.navigable()));
            second = second.withNavigable(resolveEaNavigability(association, ends.get(1), second.classifier(),
                    source, target, 1, second.navigable()));
            associations.add(new AssociationData(associationId, attribute(association, "name"),
                    packageId, first.toData(normalizeAggregation(second.aggregation)),
                    second.toData(normalizeAggregation(first.aggregation))));
        }

        private RawEnd rawEnd(Element end, String associationId, int memberOrder) {
            String classifier = reference(end, "type", "participant");
            if (classifier == null) {
                for (Element participant : descendants(end, "AssociationEnd.participant")) {
                    classifier = firstReference(participant);
                }
            }
            classifier = requiredRef(classifier, "association classifier");
            String role = attribute(end, "name");
            String endId = resolveEaExternalId(end,
                    new EaIdentityContext(associationId, "ASSOCIATION_END", memberOrder, value(role, "end")));
            return new RawEnd(endId, role, classifier,
                    visibility(end, "PUBLIC"), lower(end), upper(end), bool(end, "isNavigable", false),
                    value(attribute(end, "aggregation"), "none"));
        }

        private void readRealization(Element relation) {
            String stereotype = value(attribute(relation, "stereotype"), taggedValue(relation, "stereotype"));
            String eaType = taggedValue(relation, "ea_type");
            boolean realization = isRealizationName(stereotype) || isRealizationName(eaType);
            if (!realization) return;
            String client = reference(relation, "client");
            String supplier = reference(relation, "supplier");
            client = requiredRef(client, "client");
            supplier = requiredRef(supplier, "supplier");
            if (!"CLASS".equals(classifierKinds.get(client))) {
                throw invalid("XMI_REALIZATION_CLIENT_INVALID",
                        "EA Realization client must resolve to a Class: " + client);
            }
            if (!"INTERFACE".equals(classifierKinds.get(supplier))) {
                throw invalid("XMI_REALIZATION_SUPPLIER_INVALID",
                        "EA Realization supplier must resolve to an Interface: " + supplier);
            }
            String relationId = resolveEaExternalId(relation,
                    new EaIdentityContext(value(containingPackage(relation), "model"), "REALIZATION",
                            realizations.size(), "realization"));
            realizations.add(new RealizationData(relationId,
                    client, supplier));
        }

        private boolean isRealizationName(String value) {
            return value != null && (value.equalsIgnoreCase("realize")
                    || value.equalsIgnoreCase("realization") || value.equalsIgnoreCase("realisation"));
        }

        private String associationEndpoint(Element association, boolean source) {
            String direct = attribute(association, source ? new String[]{"source", "start"}
                    : new String[]{"target", "end"});
            if (direct != null) return externalReference(direct);
            String tagged = taggedValue(association, source ? "ea_sourceID" : "ea_targetID");
            if (tagged == null) tagged = taggedValue(association, source ? "src" : "dst");
            return tagged == null ? null : externalReference(tagged);
        }

        private boolean resolveEaNavigability(Element association, Element end, String classifier,
                String source, String target, int memberOrder, boolean fallback) {
            boolean sourceEnd = source != null ? source.equals(classifier)
                    : target != null ? !target.equals(classifier) : memberOrder == 0;
            String styleTag = sourceEnd ? "sourcestyle" : "deststyle";
            String style = value(taggedValue(end, styleTag),
                    value(taggedValue(association, styleTag), attribute(association, styleTag)));
            Boolean styled = parseStyleNavigability(style);
            if (styled != null) return styled;

            String navigableTag = sourceEnd ? "src_isNavigable" : "dst_isNavigable";
            String tagged = value(taggedValue(end, navigableTag),
                    value(taggedValue(association, navigableTag), attribute(association, navigableTag)));
            Boolean taggedNavigation = parseEaBoolean(tagged);
            return taggedNavigation == null ? fallback : taggedNavigation;
        }

        private Boolean parseStyleNavigability(String style) {
            if (style == null) return null;
            for (String item : style.split(";")) {
                String[] pair = item.trim().split("=", 2);
                if (pair.length != 2 || !"Navigable".equalsIgnoreCase(pair[0].trim())) continue;
                return switch (pair[1].trim().toLowerCase(Locale.ROOT)) {
                    case "navigable" -> true;
                    case "non-navigable", "nonnavigable", "unspecified" -> false;
                    default -> null;
                };
            }
            return null;
        }

        private Boolean parseEaBoolean(String value) {
            if (value == null) return null;
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "true", "1", "yes", "navigable" -> true;
                case "false", "0", "no", "non-navigable", "nonnavigable", "unspecified" -> false;
                default -> null;
            };
        }

        private boolean isEaRootInfrastructure(Element element, String packageId) {
            if ("EAID_11111111_5487_4080_A7F4_41526CB0AA00".equalsIgnoreCase(id(element))) return true;
            return packageId == null && "EARootClass".equals(attribute(element, "name"))
                    && bool(element, "isRoot", false);
        }

        private TypedValue typedValue(Element element) {
            String reference = reference(element, "type");
            if (reference != null && classifierIds.contains(reference)) return new TypedValue("CLASSIFIER", null, reference);
            String typeName = reference == null ? null : dataTypes.get(reference);
            if (typeName == null) typeName = taggedValue(element, "type");
            if (typeName == null) typeName = attribute(element, "type");
            if (typeName == null) {
                for (Element typeContainer : descendants(element, "StructuralFeature.type")) {
                    String ref = externalReference(firstReference(typeContainer));
                    if (ref != null && classifierIds.contains(ref)) return new TypedValue("CLASSIFIER", null, ref);
                    if (ref != null) typeName = dataTypes.get(ref);
                }
                for (Element typeContainer : descendants(element, "Parameter.type")) {
                    String ref = externalReference(firstReference(typeContainer));
                    if (ref != null && classifierIds.contains(ref)) return new TypedValue("CLASSIFIER", null, ref);
                    if (ref != null) typeName = dataTypes.get(ref);
                }
            }
            return primitive(requiredRef(typeName, "type"));
        }

        private TypedValue primitive(String name) {
            String normalized = name.contains("::") ? name.substring(name.lastIndexOf("::") + 2) : name;
            String primitive = switch (normalized.toLowerCase(Locale.ROOT)) {
                case "string" -> "STRING"; case "integer", "int" -> "INTEGER";
                case "boolean", "bool" -> "BOOLEAN"; case "real", "double", "float" -> "REAL";
                case "unlimitednatural", "unlimited_natural" -> "UNLIMITED_NATURAL";
                default -> throw invalid("XMI_PRIMITIVE_UNSUPPORTED", "Unsupported primitive type: " + name);
            };
            return new TypedValue("PRIMITIVE", primitive, null);
        }

        private String taggedValue(Element owner, String tag) {
            for (Element value : ownTaggedValues(owner)) {
                if (tag.equalsIgnoreCase(value(attribute(value, "tag", "name"), ""))) {
                    return attribute(value, "value");
                }
            }
            return null;
        }

        private String reference(Element owner, String... names) {
            String direct = attribute(owner, names);
            if (direct != null) return externalReference(direct);
            for (String name : names) {
                for (Element container : descendants(owner, name)) {
                    String found = firstReference(container);
                    if (found != null) return externalReference(found);
                }
            }
            return null;
        }

        private String firstReference(Element owner) {
            if (idref(owner) != null) return idref(owner);
            for (Element child : children(owner)) {
                String found = firstReference(child);
                if (found != null) return found;
            }
            return null;
        }

        private int lower(Element element) {
            String direct = attribute(element, "lower", "lowerBound");
            if (direct == null) direct = multiplicityPart(element, true);
            return parseBound(direct, 1);
        }

        private Integer upper(Element element) {
            String direct = attribute(element, "upper", "upperBound");
            if (direct == null) direct = multiplicityPart(element, false);
            if ("*".equals(direct) || "-1".equals(direct)) return null;
            return parseBound(direct, 1);
        }

        private String multiplicityPart(Element element, boolean lower) {
            String multiplicity = attribute(element, "multiplicity");
            if (multiplicity != null) {
                String[] parts = multiplicity.split("\\.\\.");
                return parts.length == 1 ? parts[0] : parts[lower ? 0 : 1];
            }
            for (Element range : descendants(element, "MultiplicityRange")) {
                String value = attribute(range, lower ? "lower" : "upper");
                if (value != null) return value;
            }
            return null;
        }

        private int parseBound(String value, int fallback) {
            if (value == null || value.isBlank()) return fallback;
            try { return Integer.parseInt(value); }
            catch (NumberFormatException exception) { throw invalid("XMI_MULTIPLICITY_INVALID", "Invalid bound: " + value); }
        }

        private String containingPackage(Element element) {
            for (var node = element.getParentNode(); node instanceof Element parent; node = parent.getParentNode()) {
                if ("Package".equals(local(parent))) return resolvedIds.get(parent);
            }
            return null;
        }

        private String normalizeAggregation(String value) {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "aggregate", "shared" -> "SHARED"; case "composite" -> "COMPOSITE"; default -> "NONE";
            };
        }

        private String visibility(Element element, String fallback) {
            return value(attribute(element, "visibility"), fallback).toUpperCase(Locale.ROOT);
        }
        private String resolveEaExternalId(Element element, EaIdentityContext context) {
            String existing = resolvedIds.get(element);
            if (existing != null) return existing;
            String rawXmiId = id(element);
            String externalId;
            if (rawXmiId != null && !rawXmiId.isBlank()) {
                externalId = externalReference(rawXmiId);
            } else {
                String guid = taggedValue(element, "ea_guid");
                if (guid != null && !guid.isBlank()) {
                    externalId = "ea-guid:" + guid.trim();
                    if (!eaGuids.add(guid.trim().toUpperCase(Locale.ROOT))) {
                        throw invalid("EA_GUID_DUPLICATED", "Duplicated Enterprise Architect ea_guid: " + guid);
                    }
                } else {
                    externalId = registerSyntheticExternalId(context);
                    warnings.add(new XmiImportWarning("SYNTHETIC_EXTERNAL_ID",
                            context.kind() + " has no xmi.id or ea_guid; CASEGen generated a deterministic external id",
                            externalId));
                }
            }
            if (!externalId.startsWith("synthetic:") && !externalIds.add(externalId)) {
                throw invalid("XMI_ID_DUPLICATED", "External XMI identifier is duplicated: " + externalId);
            }
            resolvedIds.put(element, externalId);
            return externalId;
        }

        private String registerSyntheticExternalId(EaIdentityContext context) {
            String parent = value(context.parentExternalId(), "model");
            String seed = parent + ":" + context.kind() + ":" + context.position() + ":"
                    + value(context.name(), "unnamed");
            String externalId = boundedSyntheticId(seed);
            if (!externalIds.add(externalId)) {
                throw invalid("XMI_ID_DUPLICATED", "External XMI identifier is duplicated: " + externalId);
            }
            return externalId;
        }

        private String externalReference(String rawId) {
            if (rawId == null || rawId.isBlank()) return null;
            return rawId.startsWith("xmi:") ? rawId : "xmi:" + rawId;
        }

        private String boundedSyntheticId(String seed) {
            String candidate = "synthetic:" + seed;
            if (candidate.length() <= 255) return candidate;
            String digest = UUID.nameUUIDFromBytes(candidate.getBytes(StandardCharsets.UTF_8)).toString();
            return candidate.substring(0, 218) + ":" + digest;
        }

        private List<Element> ownTaggedValues(Element owner) {
            List<Element> result = new ArrayList<>();
            collectOwnTaggedValues(owner, result);
            return result;
        }

        private void collectOwnTaggedValues(Element current, List<Element> result) {
            for (Element nested : children(current)) {
                if ("TaggedValue".equals(local(nested))) {
                    result.add(nested);
                } else if (!identityBoundary(nested)) {
                    collectOwnTaggedValues(nested, result);
                }
            }
        }

        private boolean identityBoundary(Element element) {
            return switch (local(element)) {
                case "Package", "Class", "Interface", "Attribute", "Operation", "Parameter",
                        "Association", "AssociationEnd", "Generalization", "Abstraction", "Dependency" -> true;
                default -> false;
            };
        }

        private int position(Element element, int fallback) {
            String raw = attribute(element, "position", "pos");
            if (raw == null) raw = taggedValue(element, "position");
            if (raw == null) raw = taggedValue(element, "pos");
            if (raw == null || raw.isBlank()) return fallback;
            try { return Integer.parseInt(raw); }
            catch (NumberFormatException ignored) { return fallback; }
        }
        private String requiredName(Element element, String kind) {
            if (attribute(element, "name") == null) throw invalid("XMI_NAME_MISSING", kind + " has no name");
            return attribute(element, "name");
        }
        private String requiredRef(String value, String kind) {
            if (value == null || value.isBlank()) throw invalid("XMI_REFERENCE_UNRESOLVED", "Missing " + kind + " reference");
            if ((kind.contains("classifier") || kind.equals("specific") || kind.equals("general")
                    || kind.equals("client") || kind.equals("supplier")) && !classifierIds.contains(value)) {
                throw invalid("XMI_REFERENCE_UNRESOLVED", "Unresolved " + kind + " reference: " + value);
            }
            return value;
        }
        private String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
        private XmiImportException invalid(String code, String message) {
            return new XmiImportException(code, HttpStatus.BAD_REQUEST, message);
        }

        private record RawEnd(String id, String role, String classifier, String visibility,
                int lower, Integer upper, boolean navigable, String aggregation) {
            RawEnd withNavigable(boolean value) {
                return new RawEnd(id, role, classifier, visibility, lower, upper, value, aggregation);
            }

            AssociationEndData toData(String casegenAggregation) {
                return new AssociationEndData(id, role, classifier, visibility, lower, upper,
                        navigable, casegenAggregation, "ASSOCIATION");
            }
        }

        private record EaIdentityContext(String parentExternalId, String kind, int position, String name) {}

        private record ReturnCandidate(ParameterData parameter, int sourceOrder) {}
    }
}
