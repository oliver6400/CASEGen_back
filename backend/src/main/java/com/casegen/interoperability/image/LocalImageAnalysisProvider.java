package com.casegen.interoperability.image;

import static com.casegen.interoperability.image.DetectedClassDiagram.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.awt.geom.Line2D;
import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Deterministic local CV/OCR pipeline. It never sends image data outside the host. */
@Component
public class LocalImageAnalysisProvider implements ImageAnalysisProvider {
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "^\\s*([+\\-#~])?\\s*([\\p{L}_][\\p{L}\\p{N}_]*)\\s*(?::\\s*([\\p{L}_][\\p{L}\\p{N}_]*))?\\s*$");
    private static final Pattern OPERATION = Pattern.compile(
            "^\\s*([+\\-#~])?\\s*([\\p{L}_][\\p{L}\\p{N}_]*)\\s*\\(([^)]*)\\)\\s*(?::\\s*([\\p{L}_][\\p{L}\\p{N}_]*))?\\s*$");
    private static final Pattern MULTIPLICITY = Pattern.compile("^(1|0\\.\\.1|\\*|0\\.\\.\\*|1\\.\\.\\*)$");
    private static volatile boolean openCvLoaded;

    private final LocalOcrEngine ocr;
    private final double minimumWidthRatio;
    private final double minimumHeightRatio;
    private final double minimumAreaRatio;
    private final double minimumRectangularity;
    private final double minimumAspectRatio;
    private final double maximumAspectRatio;

    @Autowired
    public LocalImageAnalysisProvider(LocalOcrEngine ocr,
            @Value("${casegen.image-import.local.minimum-width-ratio:0.07}") double minimumWidthRatio,
            @Value("${casegen.image-import.local.minimum-height-ratio:0.045}") double minimumHeightRatio,
            @Value("${casegen.image-import.local.minimum-area-ratio:0.004}") double minimumAreaRatio,
            @Value("${casegen.image-import.local.minimum-rectangularity:0.42}") double minimumRectangularity,
            @Value("${casegen.image-import.local.minimum-aspect-ratio:0.55}") double minimumAspectRatio,
            @Value("${casegen.image-import.local.maximum-aspect-ratio:5.5}") double maximumAspectRatio) {
        this.ocr = ocr;
        this.minimumWidthRatio = minimumWidthRatio;
        this.minimumHeightRatio = minimumHeightRatio;
        this.minimumAreaRatio = minimumAreaRatio;
        this.minimumRectangularity = minimumRectangularity;
        this.minimumAspectRatio = minimumAspectRatio;
        this.maximumAspectRatio = maximumAspectRatio;
    }

    LocalImageAnalysisProvider(LocalOcrEngine ocr) {
        this(ocr, 0.07, 0.045, 0.004, 0.42, 0.55, 5.5);
    }

    @Override public boolean configured() { return true; }
    @Override public String provider() { return "LOCAL"; }
    @Override public String model() { return "opencv+tesseract"; }
    @Override public Boolean localOcrAvailable() { return ocr.available(); }

    @Override
    public DetectedClassDiagram analyze(byte[] image, String mimeType, String fileName) {
        ensureOpenCv();
        MatOfByte encoded = new MatOfByte(image);
        Mat source = Imgcodecs.imdecode(encoded, Imgcodecs.IMREAD_COLOR);
        encoded.release();
        if (source.empty()) {
            throw new ImageImportException("LOCAL_IMAGE_DECODE_FAILED", HttpStatus.UNPROCESSABLE_ENTITY,
                    "No se pudo decodificar la imagen para análisis local.");
        }
        Mat corrected = correctPerspective(source);
        Mat gray = grayscale(corrected);
        Mat thresholded = preprocess(gray);
        Mat binary = suppressGrid(thresholded, false);
        CandidateDetection detection = detectClassifierRectangles(binary, false);
        boolean noisySegmentation = isAbnormallyDense(detection.rectangles(), binary);
        if (noisySegmentation) {
            Mat strictBinary = suppressGrid(thresholded, true);
            CandidateDetection strict = detectClassifierRectangles(strictBinary, true);
            binary.release();
            binary = strictBinary;
            detection = strict;
        }
        List<Rect> rectangles = mergeAdjacentCompartments(detection.rectangles(), binary, false);
        if (isAbnormallyDense(rectangles, binary)) {
            double strictMinimumArea = binary.cols() * (double) binary.rows() * minimumAreaRatio * 2.2;
            rectangles = rectangles.stream().filter(value -> value.area() >= strictMinimumArea)
                    .sorted(Comparator.comparingInt((Rect value) -> value.y)
                            .thenComparingInt(value -> value.x)).toList();
            rectangles = mergeAdjacentCompartments(rectangles, binary, true);
            noisySegmentation = true;
        }
        if (rectangles.isEmpty()) {
            release(source, corrected, gray, thresholded, binary);
            throw new ImageImportException("INSUFFICIENT_UML_ELEMENTS", HttpStatus.UNPROCESSABLE_ENTITY,
                    "No se pudieron identificar suficientes elementos UML.");
        }

        List<DetectionWarning> warnings = new ArrayList<>();
        if (noisySegmentation) warnings.add(new DetectionWarning("NOISY_CLASSIFIER_SEGMENTATION",
                "La segmentacion geometrica contenia demasiado ruido; se aplico una segunda pasada estricta.", null));
        List<ClassifierRegion> regions = new ArrayList<>();
        for (int index = 0; index < rectangles.size(); index++) {
            regions.add(readClassifier(corrected, binary, rectangles.get(index), index + 1));
        }
        long suspiciousHeaders = regions.stream().filter(this::hasFeatureLikeHeader).count();
        if (suspiciousHeaders >= Math.max(2, Math.ceil(regions.size() * 0.3))) {
            List<Rect> regrouped = mergeAdjacentCompartments(rectangles, binary, true);
            if (regrouped.size() < rectangles.size()) {
                rectangles = regrouped;
                regions = new ArrayList<>();
                for (int index = 0; index < rectangles.size(); index++) {
                    regions.add(readClassifier(corrected, binary, rectangles.get(index), index + 1));
                }
                warnings.add(new DetectionWarning("CLASSIFIER_COMPARTMENTS_REGROUPED",
                        "Se reagruparon compartimentos contiguos que parecían atributos u operaciones.", null));
            }
        }
        long unreadableNames = regions.stream().filter(value -> value.classifier().name().startsWith("Clase")
                && value.classifier().confidence() <= 0.45).count();
        if (unreadableNames > 0) warnings.add(new DetectionWarning("CLASSIFIER_NAME_UNREADABLE",
                unreadableNames + " nombres de classifiers no pudieron leerse; reviselos antes de importar.", null));
        if (!ocr.available()) warnings.add(new DetectionWarning("LOCAL_OCR_UNAVAILABLE",
                "Tesseract no esta disponible (afecto " + regions.size() + " classifiers).", null));
        List<DetectedClassifier> classifiers = regions.stream().map(ClassifierRegion::classifier).toList();
        Map<String, String> classifierNames = new HashMap<>();
        classifiers.forEach(value -> classifierNames.put(value.name().toLowerCase(Locale.ROOT), value.id()));

        List<DetectedAttribute> attributes = new ArrayList<>();
        List<DetectedOperation> operations = new ArrayList<>();
        for (ClassifierRegion region : regions) {
            parseFeatures(region, classifierNames, attributes, operations, warnings);
        }
        List<DetectedRelationship> relationships = detectRelationships(
                corrected, gray, binary, regions, warnings);
        double confidence = classifiers.stream().mapToDouble(DetectedClassifier::confidence)
                .average().orElse(0.4);
        if (!relationships.isEmpty()) confidence = Math.min(1, confidence + 0.03);
        String suggestedName = suggestedName(fileName);
        warnings = groupedWarnings(warnings);
        release(source, corrected, gray, thresholded, binary);
        return new DetectedClassDiagram(suggestedName, classifiers, attributes, operations,
                relationships, warnings, confidence);
    }

    private ClassifierRegion readClassifier(Mat color, Mat binary, Rect rectangle, int sequence) {
        Mat classifierBinary = binary.submat(rectangle);
        List<Integer> dividers = horizontalDividers(classifierBinary);
        classifierBinary.release();
        int inset = Math.min(5, Math.max(1, rectangle.width / 50));
        int left = rectangle.x + inset;
        int width = Math.max(1, rectangle.width - inset * 2);
        int nameBottom = dividers.isEmpty() ? Math.max(30, rectangle.height / 3) : dividers.getFirst();
        int attributesBottom = dividers.size() > 1 ? dividers.get(1) : rectangle.height;
        String nameText = ocr(color, new Rect(left, rectangle.y + inset, width,
                safeHeight(nameBottom - inset, rectangle.height)) , 6);
        String attributesText = nameBottom < rectangle.height
                ? ocr(color, new Rect(left, rectangle.y + nameBottom + 2, width,
                        safeHeight(attributesBottom - nameBottom - 3, rectangle.height - nameBottom - 2)), 6) : "";
        String operationsText = dividers.size() > 1 && attributesBottom < rectangle.height
                ? ocr(color, new Rect(left, rectangle.y + attributesBottom + 2, width,
                        safeHeight(rectangle.height - attributesBottom - inset - 2,
                                rectangle.height - attributesBottom - 2)), 6) : "";

        List<String> nameLines = lines(nameText);
        boolean isInterface = nameLines.stream().anyMatch(this::interfaceMarker);
        String name = nameLines.stream().filter(line -> !interfaceMarker(line)).map(this::identifier)
                .filter(value -> !value.isBlank()).findFirst().orElse("");
        double confidence = 0.9;
        String id = "local-classifier-" + sequence;
        if (name.isBlank()) {
            name = "Clase" + sequence;
            confidence = 0.45;
        }
        DetectedClassifier classifier = new DetectedClassifier(id, name,
                isInterface ? "INTERFACE" : "CLASS", "UNKNOWN", confidence,
                normalizedRegion(rectangle, color.cols(), color.rows()));
        return new ClassifierRegion(rectangle, classifier, attributesText, operationsText,
                dividers.size());
    }

    private void parseFeatures(ClassifierRegion region, Map<String, String> classifierNames,
            List<DetectedAttribute> attributes, List<DetectedOperation> operations,
            List<DetectionWarning> warnings) {
        List<String> attributeLines = lines(region.attributesText());
        List<String> operationLines = lines(region.operationsText());
        if (region.dividerCount() < 2) {
            List<String> combined = new ArrayList<>(attributeLines);
            combined.addAll(operationLines);
            attributeLines = combined.stream().filter(value -> !value.contains("(")).toList();
            operationLines = combined.stream().filter(value -> value.contains("(")).toList();
        }
        int attributeIndex = 0;
        for (String line : attributeLines) {
            Matcher matcher = ATTRIBUTE.matcher(clean(line));
            if (!matcher.matches()) {
                if (!line.isBlank()) warnings.add(new DetectionWarning("ATTRIBUTE_TEXT_AMBIGUOUS",
                        "No se pudo interpretar un atributo: " + safeText(line), region.classifier().id()));
                continue;
            }
            String id = region.classifier().id() + "-attribute-" + (++attributeIndex);
            DetectedType type = detectedType(matcher.group(3), classifierNames);
            if ("UNKNOWN".equals(type.kind())) warnings.add(new DetectionWarning("ATTRIBUTE_TYPE_UNREADABLE",
                    "No se pudo identificar el tipo de " + matcher.group(2) + ".", id));
            attributes.add(new DetectedAttribute(id, region.classifier().id(), matcher.group(2),
                    visibility(matcher.group(1)), type, 1, 1, false, true,
                    "UNKNOWN".equals(type.kind()) ? 0.58 : 0.82));
        }
        int operationIndex = 0;
        for (String line : operationLines) {
            Matcher matcher = OPERATION.matcher(clean(line));
            if (!matcher.matches()) {
                if (!line.isBlank()) warnings.add(new DetectionWarning("OPERATION_TEXT_AMBIGUOUS",
                        "No se pudo interpretar una operación: " + safeText(line), region.classifier().id()));
                continue;
            }
            String id = region.classifier().id() + "-operation-" + (++operationIndex);
            List<DetectedParameter> parameters = parseParameters(id, matcher.group(3), classifierNames, warnings);
            DetectedType returnType = matcher.group(4) == null || matcher.group(4).isBlank()
                    ? new DetectedType("VOID", "void") : detectedType(matcher.group(4), classifierNames);
            operations.add(new DetectedOperation(id, region.classifier().id(), matcher.group(2),
                    visibility(matcher.group(1)), parameters, returnType,
                    "UNKNOWN".equals(returnType.kind()) ? 0.6 : 0.83));
        }
    }

    private List<DetectedParameter> parseParameters(String operationId, String text,
            Map<String, String> classifierNames, List<DetectionWarning> warnings) {
        if (text == null || text.isBlank()) return List.of();
        List<DetectedParameter> result = new ArrayList<>();
        String[] values = text.split(",");
        for (int position = 0; position < values.length; position++) {
            String[] pieces = values[position].trim().split(":", 2);
            String name = identifier(pieces[0]);
            DetectedType type = pieces.length == 2 ? detectedType(pieces[1], classifierNames)
                    : new DetectedType("UNKNOWN", "UNKNOWN");
            String id = operationId + "-parameter-" + position;
            if (name.isBlank() || "UNKNOWN".equals(type.kind())) warnings.add(new DetectionWarning(
                    "PARAMETER_TEXT_AMBIGUOUS", "Revise el parámetro detectado en la operación.", id));
            result.add(new DetectedParameter(id, name.isBlank() ? "param" + (position + 1) : name,
                    "IN", type, position, 1, 1, false, true,
                    "UNKNOWN".equals(type.kind()) ? 0.55 : 0.8));
        }
        return List.copyOf(result);
    }

    private List<DetectedRelationship> detectRelationships(Mat color, Mat gray, Mat binary,
            List<ClassifierRegion> regions, List<DetectionWarning> warnings) {
        Mat relationshipBinary = maskClassifierInteriors(binary, regions);
        Mat lines = new Mat();
        int minimumLineLength = Math.max(35, Math.min(binary.cols(), binary.rows()) / 30);
        Imgproc.HoughLinesP(relationshipBinary, lines, 1, Math.PI / 180, 35, minimumLineLength, 22);
        List<Marker> markers = detectMarkers(gray, relationshipBinary, regions);
        List<DetectedSegment> segments = new ArrayList<>();
        for (int row = 0; row < lines.rows(); row++) {
            double[] line = lines.get(row, 0);
            DetectedSegment segment = new DetectedSegment(
                    new Point(line[0], line[1]), new Point(line[2], line[3]));
            if (!isClassifierBorderSegment(segment, regions)
                    && segments.stream().noneMatch(existing -> sameSegment(existing, segment))) segments.add(segment);
        }
        lines.release();

        List<DetectedRelationship> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        double endpointTolerance = Math.max(42, Math.hypot(binary.cols(), binary.rows()) * 0.025);
        double connectionTolerance = Math.max(10, Math.hypot(binary.cols(), binary.rows()) * 0.009);
        for (List<DetectedSegment> component : connectedSegmentComponents(segments, connectionTolerance)) {
            Marker marker = nearestMarker(component, markers, connectionTolerance * 2.5);
            List<GraphAttachment> attachments = new ArrayList<>(
                    componentAttachments(component, regions, endpointTolerance));
            if (marker != null) {
                ClassifierRegion markerClassifier = nearestClassifier(
                        marker.center(), regions, endpointTolerance * 1.35);
                if (markerClassifier != null && attachments.stream()
                        .noneMatch(value -> value.classifier() == markerClassifier)) {
                    attachments.add(new GraphAttachment(markerClassifier, marker.center()));
                }
            }
            if (attachments.size() < 2) continue;
            boolean dashed = componentDashed(component, relationshipBinary);
            GraphAttachment marked = marker == null ? null : nearestAttachment(marker.center(), attachments);

            if (marker != null && marker.vertices() == 3 && !marker.filled() && marked != null) {
                String type = dashed ? "REALIZATION" : "GENERALIZATION";
                for (GraphAttachment child : attachments) {
                    if (child.classifier() == marked.classifier()) continue;
                    addDetectedRelationship(result, seen, color, warnings, type,
                            child, marked, marker, false);
                }
                continue;
            }
            if (marker != null && marker.vertices() == 4 && marked != null) {
                String type = marker.filled() ? "COMPOSITION" : "AGGREGATION";
                for (GraphAttachment part : attachments) {
                    if (part.classifier() == marked.classifier()) continue;
                    addDetectedRelationship(result, seen, color, warnings, type,
                            marked, part, marker, false);
                }
                continue;
            }
            GraphAttachment first = attachments.get(0);
            for (int index = 1; index < attachments.size(); index++) {
                boolean targetNavigable = marker != null && marker.vertices() == 3 && marker.filled()
                        && attachments.get(index) == marked;
                addDetectedRelationship(result, seen, color, warnings, "ASSOCIATION",
                        first, attachments.get(index), marker, targetNavigable);
            }
        }
        relationshipBinary.release();
        return List.copyOf(result);
    }

    private void addDetectedRelationship(List<DetectedRelationship> result, Set<String> seen,
            Mat color, List<DetectionWarning> warnings, String type,
            GraphAttachment sourceAttachment, GraphAttachment targetAttachment,
            Marker marker, boolean targetNavigable) {
        String key = type + "|" + sourceAttachment.classifier().classifier().id()
                + "|" + targetAttachment.classifier().classifier().id();
        if (!seen.add(key)) return;
        String id = "local-relationship-" + (result.size() + 1);
        DetectedEnd source = end(color, sourceAttachment.point(), sourceAttachment.classifier(), id, "source", warnings);
        DetectedEnd target = end(color, targetAttachment.point(), targetAttachment.classifier(), id, "target", warnings);
        if (targetNavigable) target = new DetectedEnd(target.classifierId(), target.roleName(), target.visibility(),
                target.lowerBound(), target.upperBound(), target.upperUnlimited(), target.multiplicityKnown(), true);
        double confidence = marker == null ? 0.62 : 0.78;
        result.add(new DetectedRelationship(id, type, "", source, target, confidence));
        if (marker == null) warnings.add(new DetectionWarning("RELATIONSHIP_TYPE_LOW_CONFIDENCE",
                "Se detectó un recorrido sin marcador concluyente; revise el tipo de relación.", id));
    }

    private DetectedEnd end(Mat color, Point endpoint, ClassifierRegion classifier, String relationId,
            String side, List<DetectionWarning> warnings) {
        int cropWidth = Math.max(70, color.cols() / 18);
        int cropHeight = Math.max(34, color.rows() / 28);
        Rect crop = around(endpoint, color.cols(), color.rows(), cropWidth, cropHeight);
        String text = crop.width > 2 && crop.height > 2 ? ocr(color, crop, 7) : "";
        String multiplicity = lines(text).stream().map(String::trim)
                .filter(value -> MULTIPLICITY.matcher(value.replace(" ", "")).matches())
                .findFirst().orElse("");
        Multiplicity parsed = parseMultiplicity(multiplicity);
        if (!parsed.known()) warnings.add(new DetectionWarning("MULTIPLICITY_UNREADABLE",
                "No se pudo leer la multiplicidad del extremo; revísela antes de importar.", relationId + ":" + side));
        return new DetectedEnd(classifier.classifier().id(), "", "UNKNOWN", parsed.lower(),
                parsed.upper(), parsed.unlimited(), parsed.known(), false);
    }

    private List<Marker> detectMarkers(Mat gray, Mat binary, List<ClassifierRegion> classifiers) {
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(binary.clone(), contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
        List<Marker> result = new ArrayList<>();
        for (MatOfPoint contour : contours) {
            double perimeter = Imgproc.arcLength(new MatOfPoint2f(contour.toArray()), true);
            MatOfPoint2f approximation = new MatOfPoint2f();
            Imgproc.approxPolyDP(new MatOfPoint2f(contour.toArray()), approximation, perimeter * 0.05, true);
            int vertices = (int) approximation.total();
            Rect rect = Imgproc.boundingRect(contour);
            double area = Math.abs(Imgproc.contourArea(contour));
            int maximumSide = Math.max(45, (int) Math.round(Math.min(binary.cols(), binary.rows()) * 0.06));
            double maximumArea = Math.max(1800, binary.cols() * binary.rows() * 0.004);
            if ((vertices == 3 || vertices == 4) && area >= 20 && area <= maximumArea
                    && rect.width >= 6 && rect.height >= 6 && rect.width <= maximumSide && rect.height <= maximumSide
                    && classifiers.stream().noneMatch(value -> inside(center(rect), value.rect(), 12))) {
                boolean filled = Core.mean(gray.submat(clamp(rect, gray.cols(), gray.rows()))).val[0] < 115;
                result.add(new Marker(center(rect), vertices, filled));
            }
            approximation.release();
            contour.release();
        }
        return result;
    }

    private List<List<DetectedSegment>> connectedSegmentComponents(List<DetectedSegment> segments,
            double tolerance) {
        DisjointSet sets = new DisjointSet(segments.size());
        for (int first = 0; first < segments.size(); first++) {
            for (int second = first + 1; second < segments.size(); second++) {
                if (segmentsConnected(segments.get(first), segments.get(second), tolerance)) {
                    sets.union(first, second);
                }
            }
        }
        Map<Integer, List<DetectedSegment>> components = new java.util.LinkedHashMap<>();
        for (int index = 0; index < segments.size(); index++) {
            components.computeIfAbsent(sets.find(index), ignored -> new ArrayList<>()).add(segments.get(index));
        }
        return List.copyOf(components.values());
    }

    private boolean segmentsConnected(DetectedSegment first, DetectedSegment second, double tolerance) {
        if (Line2D.linesIntersect(first.first().x, first.first().y, first.second().x, first.second().y,
                second.first().x, second.first().y, second.second().x, second.second().y)) return true;
        double endpointDistance = Math.min(Math.min(distance(first.first(), second.first()),
                distance(first.first(), second.second())), Math.min(distance(first.second(), second.first()),
                distance(first.second(), second.second())));
        if (endpointDistance <= tolerance) return true;
        double angle = segmentAngleDifference(first, second);
        return (angle <= Math.toRadians(8) || angle >= Math.toRadians(172))
                && endpointDistance <= tolerance * 3.2
                && Math.min(pointToSegment(first.first(), second), pointToSegment(first.second(), second)) <= tolerance;
    }

    private List<GraphAttachment> componentAttachments(List<DetectedSegment> component,
            List<ClassifierRegion> classifiers, double tolerance) {
        Map<String, GraphAttachment> result = new java.util.LinkedHashMap<>();
        for (DetectedSegment segment : component) {
            for (Point point : List.of(segment.first(), segment.second())) {
                ClassifierRegion classifier = nearestClassifier(point, classifiers, tolerance);
                if (classifier == null) continue;
                GraphAttachment candidate = new GraphAttachment(classifier, point);
                result.merge(classifier.classifier().id(), candidate, (current, replacement) ->
                        distanceToBorder(replacement.point(), replacement.classifier().rect())
                                < distanceToBorder(current.point(), current.classifier().rect())
                                ? replacement : current);
            }
        }
        return List.copyOf(result.values());
    }

    private Marker nearestMarker(List<DetectedSegment> component, List<Marker> markers, double tolerance) {
        return markers.stream().filter(marker -> component.stream()
                        .anyMatch(segment -> pointToSegment(marker.center(), segment) <= tolerance))
                .min(Comparator.comparingDouble(marker -> component.stream()
                        .mapToDouble(segment -> pointToSegment(marker.center(), segment)).min().orElse(Double.MAX_VALUE)))
                .orElse(null);
    }

    private GraphAttachment nearestAttachment(Point point, List<GraphAttachment> attachments) {
        return attachments.stream().min(Comparator.comparingDouble(value -> distance(point, value.point())))
                .orElse(null);
    }

    private boolean componentDashed(List<DetectedSegment> component, Mat binary) {
        if (component.stream().anyMatch(segment -> isDashed(binary, segment.first(), segment.second()))) return true;
        if (component.size() < 4) return false;
        double minX = component.stream().flatMap(segment -> java.util.stream.Stream.of(segment.first(), segment.second()))
                .mapToDouble(point -> point.x).min().orElse(0);
        double maxX = component.stream().flatMap(segment -> java.util.stream.Stream.of(segment.first(), segment.second()))
                .mapToDouble(point -> point.x).max().orElse(0);
        double minY = component.stream().flatMap(segment -> java.util.stream.Stream.of(segment.first(), segment.second()))
                .mapToDouble(point -> point.y).min().orElse(0);
        double maxY = component.stream().flatMap(segment -> java.util.stream.Stream.of(segment.first(), segment.second()))
                .mapToDouble(point -> point.y).max().orElse(0);
        double span = Math.hypot(maxX - minX, maxY - minY);
        long shortSegments = component.stream().filter(segment -> segment.length() < span * 0.32).count();
        return shortSegments >= Math.ceil(component.size() * 0.65);
    }

    private boolean isClassifierBorderSegment(DetectedSegment segment, List<ClassifierRegion> classifiers) {
        Point midpoint = new Point((segment.first().x + segment.second().x) / 2,
                (segment.first().y + segment.second().y) / 2);
        boolean axisAligned = Math.abs(segment.first().x - segment.second().x) <= 5
                || Math.abs(segment.first().y - segment.second().y) <= 5;
        if (!axisAligned) return false;
        return classifiers.stream().anyMatch(classifier -> {
            Rect rect = classifier.rect();
            boolean endpointsWithin = withinExpanded(segment.first(), rect, 8)
                    && withinExpanded(segment.second(), rect, 8);
            return endpointsWithin && distanceToBorder(midpoint, rect) <= 7;
        });
    }

    private boolean withinExpanded(Point point, Rect rect, double amount) {
        return point.x >= rect.x - amount && point.x <= rect.x + rect.width + amount
                && point.y >= rect.y - amount && point.y <= rect.y + rect.height + amount;
    }

    private boolean sameSegment(DetectedSegment first, DetectedSegment second) {
        double tolerance = Math.max(6, Math.min(first.length(), second.length()) * 0.08);
        return (distance(first.first(), second.first()) <= tolerance
                && distance(first.second(), second.second()) <= tolerance)
                || (distance(first.first(), second.second()) <= tolerance
                && distance(first.second(), second.first()) <= tolerance);
    }

    private double pointToSegment(Point point, DetectedSegment segment) {
        double dx = segment.second().x - segment.first().x;
        double dy = segment.second().y - segment.first().y;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0) return distance(point, segment.first());
        double ratio = ((point.x - segment.first().x) * dx + (point.y - segment.first().y) * dy) / lengthSquared;
        ratio = Math.max(0, Math.min(1, ratio));
        return distance(point, new Point(segment.first().x + ratio * dx, segment.first().y + ratio * dy));
    }

    private double segmentAngleDifference(DetectedSegment first, DetectedSegment second) {
        double firstAngle = Math.atan2(first.second().y - first.first().y, first.second().x - first.first().x);
        double secondAngle = Math.atan2(second.second().y - second.first().y, second.second().x - second.first().x);
        double difference = Math.abs(firstAngle - secondAngle) % Math.PI;
        return Math.min(difference, Math.PI - difference);
    }

    private CandidateDetection detectClassifierRectangles(Mat binary, boolean strict) {
        List<MatOfPoint> contours = new ArrayList<>();
        Mat contourSource = binary.clone();
        Mat hierarchy = new Mat();
        Imgproc.findContours(contourSource, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
        List<Candidate> candidates = new ArrayList<>();
        double strictFactor = strict ? 1.25 : 1.0;
        int minWidth = Math.max(55, (int) Math.round(binary.cols() * minimumWidthRatio * strictFactor));
        int minHeight = Math.max(42, (int) Math.round(binary.rows() * minimumHeightRatio * strictFactor));
        double imageArea = (double) binary.cols() * binary.rows();
        double minArea = imageArea * minimumAreaRatio * (strict ? 1.6 : 1.0);
        double rectangularityThreshold = Math.min(0.85, minimumRectangularity + (strict ? 0.08 : 0));
        for (MatOfPoint contour : contours) {
            Rect rect = Imgproc.boundingRect(contour);
            double area = Math.abs(Imgproc.contourArea(contour));
            double rectangularity = rect.area() == 0 ? 0 : area / rect.area();
            double aspect = rect.height == 0 ? 0 : rect.width / (double) rect.height;
            if (rect.width >= minWidth && rect.height >= minHeight && rect.area() >= minArea
                    && rect.area() < imageArea * 0.78 && rectangularity >= rectangularityThreshold
                    && aspect >= minimumAspectRatio && aspect <= maximumAspectRatio
                    && !hasInternalVerticalDivider(binary, rect)) {
                candidates.add(new Candidate(rect, candidateScore(rect, rectangularity, imageArea)));
            }
            contour.release();
        }
        for (Rect rectangle : rectanglesFromLinePairs(binary, minWidth, minHeight)) {
            double aspect = rectangle.width / (double) Math.max(1, rectangle.height);
            if (rectangle.area() >= minArea && rectangle.area() < imageArea * 0.78
                    && aspect >= minimumAspectRatio && aspect <= maximumAspectRatio
                    && !hasInternalVerticalDivider(binary, rectangle)) {
                candidates.add(new Candidate(rectangle, candidateScore(rectangle, 0.72, imageArea)));
            }
        }
        contourSource.release();
        hierarchy.release();
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
        List<Rect> result = new ArrayList<>();
        for (Candidate candidate : candidates) {
            boolean duplicate = result.stream().anyMatch(existing -> sameClassifier(existing, candidate.rect()));
            if (!duplicate) result.add(candidate.rect());
        }
        result.sort(Comparator.comparingInt((Rect value) -> value.y).thenComparingInt(value -> value.x));
        return new CandidateDetection(List.copyOf(result), candidates.size());
    }

    private boolean hasInternalVerticalDivider(Mat binary, Rect rectangle) {
        int margin = Math.max(8, rectangle.width / 10);
        int top = clamp(rectangle.y + 3, 0, binary.rows() - 1);
        int bottom = clamp(rectangle.y + rectangle.height - 3, top + 1, binary.rows());
        for (int x = rectangle.x + margin; x < rectangle.x + rectangle.width - margin; x += 2) {
            Rect column = clamp(new Rect(x, top, 3, Math.max(1, bottom - top)), binary.cols(), binary.rows());
            Mat roi = binary.submat(column);
            double coverage = Core.countNonZero(roi) / Math.max(1.0, column.height * (double) column.width);
            roi.release();
            if (coverage >= 0.5) return true;
        }
        return false;
    }

    /** Reconstructs one outer classifier from 1-3 vertically contiguous compartment boxes. */
    private List<Rect> mergeAdjacentCompartments(List<Rect> rectangles, Mat binary, boolean permissive) {
        List<CompartmentCandidate> candidates = rectangles.stream()
                .map(rectangle -> new CompartmentCandidate(rectangle, 1)).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        boolean changed;
        do {
            changed = false;
            MergeChoice best = null;
            for (int first = 0; first < candidates.size(); first++) {
                for (int second = first + 1; second < candidates.size(); second++) {
                    CompartmentCandidate a = candidates.get(first);
                    CompartmentCandidate b = candidates.get(second);
                    if (a.parts() + b.parts() > 3) continue;
                    double score = compartmentMergeScore(a.rect(), b.rect(), binary, permissive);
                    if (score >= (permissive ? 0.62 : 0.74)
                            && (best == null || score > best.score())) best = new MergeChoice(first, second, score);
                }
            }
            if (best != null) {
                CompartmentCandidate first = candidates.get(best.first());
                CompartmentCandidate second = candidates.get(best.second());
                Rect merged = union(first.rect(), second.rect());
                candidates.remove(best.second());
                candidates.set(best.first(), new CompartmentCandidate(merged, first.parts() + second.parts()));
                changed = true;
            }
        } while (changed);
        return candidates.stream().map(CompartmentCandidate::rect)
                .sorted(Comparator.comparingInt((Rect value) -> value.y).thenComparingInt(value -> value.x))
                .toList();
    }

    private double compartmentMergeScore(Rect first, Rect second, Mat binary, boolean permissive) {
        Rect upper = first.y <= second.y ? first : second;
        Rect lower = upper == first ? second : first;
        int gap = lower.y - (upper.y + upper.height);
        double xTolerance = Math.max(binary.cols() * (permissive ? 0.012 : 0.007),
                Math.min(upper.width, lower.width) * (permissive ? 0.08 : 0.045));
        double gapTolerance = Math.max(3.0, binary.rows() * (permissive ? 0.018 : 0.012));
        if (gap < -gapTolerance || gap > gapTolerance) return 0;
        double leftDifference = Math.abs(upper.x - lower.x);
        double rightDifference = Math.abs((upper.x + upper.width) - (lower.x + lower.width));
        if (leftDifference > xTolerance || rightDifference > xTolerance) return 0;
        double widthSimilarity = Math.min(upper.width, lower.width) / (double) Math.max(upper.width, lower.width);
        if (widthSimilarity < (permissive ? 0.84 : 0.9)) return 0;
        double continuity = sharedHorizontalBorder(binary, upper, lower);
        if (gap > Math.max(3, binary.rows() * 0.012)
                && continuity < 0.6 && exteriorWhitespace(binary, upper, lower) > 0.82) return 0;
        double alignment = 1.0 - Math.min(1.0, (leftDifference + rightDifference) / Math.max(1.0, xTolerance * 2));
        return widthSimilarity * 0.42 + alignment * 0.28 + continuity * 0.3;
    }

    private double sharedHorizontalBorder(Mat binary, Rect upper, Rect lower) {
        int left = Math.max(upper.x, lower.x);
        int right = Math.min(upper.x + upper.width, lower.x + lower.width);
        if (right <= left) return 0;
        int boundary = (upper.y + upper.height + lower.y) / 2;
        int radius = Math.max(2, binary.rows() / 500);
        int hits = 0;
        for (int x = left; x < right; x += 2) {
            boolean ink = false;
            for (int y = boundary - radius; y <= boundary + radius; y++) {
                int py = clamp(y, 0, binary.rows() - 1);
                if (binary.get(py, clamp(x, 0, binary.cols() - 1))[0] > 0) { ink = true; break; }
            }
            if (ink) hits++;
        }
        return hits / (double) Math.max(1, (right - left + 1) / 2);
    }

    private double exteriorWhitespace(Mat binary, Rect upper, Rect lower) {
        int top = upper.y + upper.height;
        int bottom = lower.y;
        if (bottom <= top) return 0;
        int left = Math.max(upper.x, lower.x);
        int right = Math.min(upper.x + upper.width, lower.x + lower.width);
        Rect gap = clamp(new Rect(left, top, Math.max(1, right - left), bottom - top), binary.cols(), binary.rows());
        Mat roi = binary.submat(gap);
        double inkRatio = Core.countNonZero(roi) / Math.max(1.0, gap.area());
        roi.release();
        return 1.0 - Math.min(1.0, inkRatio * 8.0);
    }

    private Rect union(Rect first, Rect second) {
        int left = Math.min(first.x, second.x), top = Math.min(first.y, second.y);
        int right = Math.max(first.x + first.width, second.x + second.width);
        int bottom = Math.max(first.y + first.height, second.y + second.height);
        return new Rect(left, top, right - left, bottom - top);
    }

    private List<Rect> rectanglesFromLinePairs(Mat binary, int minWidth, int minHeight) {
        Mat detected = new Mat();
        Imgproc.HoughLinesP(binary, detected, 1, Math.PI / 180, 40,
                Math.min(minWidth, minHeight) * 0.75, 12);
        List<LineSegment> horizontal = new ArrayList<>();
        for (int row = 0; row < detected.rows(); row++) {
            double[] value = detected.get(row, 0);
            Point first = new Point(value[0], value[1]);
            Point second = new Point(value[2], value[3]);
            if (Math.abs(first.y - second.y) <= 5 && Math.abs(first.x - second.x) >= minWidth * 0.75) {
                horizontal.add(LineSegment.horizontal(first, second));
            }
        }
        detected.release();
        List<Rect> result = new ArrayList<>();
        for (int firstIndex = 0; firstIndex < horizontal.size(); firstIndex++) {
            LineSegment top = horizontal.get(firstIndex);
            for (int secondIndex = firstIndex + 1; secondIndex < horizontal.size(); secondIndex++) {
                LineSegment bottom = horizontal.get(secondIndex);
                int height = (int) Math.round(bottom.axis() - top.axis());
                if (height < minHeight) continue;
                double left = Math.max(top.start(), bottom.start());
                double right = Math.min(top.end(), bottom.end());
                if (right - left < minWidth * 0.75) continue;
                int x = (int) Math.round(left);
                int width = (int) Math.round(right - left);
                if (hasVerticalBorder(binary, x, (int) top.axis(), (int) bottom.axis())
                        && hasVerticalBorder(binary, x + width, (int) top.axis(), (int) bottom.axis())) {
                    result.add(new Rect(x, (int) Math.round(top.axis()), width, height));
                }
            }
        }
        return result;
    }

    private boolean hasVerticalBorder(Mat binary, int x, int top, int bottom) {
        int samples = Math.max(1, bottom - top);
        int hits = 0;
        for (int y = top; y <= bottom; y += 3) {
            boolean present = false;
            for (int offset = -5; offset <= 5; offset++) {
                int px = clamp(x + offset, 0, binary.cols() - 1);
                int py = clamp(y, 0, binary.rows() - 1);
                if (binary.get(py, px)[0] > 0) { present = true; break; }
            }
            if (present) hits++;
        }
        return hits >= (samples / 3.0) * 0.65;
    }

    private List<Integer> horizontalDividers(Mat region) {
        Mat lines = new Mat();
        Imgproc.HoughLinesP(region, lines, 1, Math.PI / 180, 25,
                Math.max(25, region.cols() * 0.55), 8);
        List<Integer> result = new ArrayList<>();
        for (int row = 0; row < lines.rows(); row++) {
            double[] line = lines.get(row, 0);
            int y = (int) Math.round((line[1] + line[3]) / 2);
            if (Math.abs(line[1] - line[3]) <= 4 && y > 10 && y < region.rows() - 10
                    && result.stream().noneMatch(existing -> Math.abs(existing - y) < 8)) result.add(y);
        }
        lines.release();
        result.sort(Integer::compareTo);
        return result.size() > 2 ? result.subList(0, 2) : result;
    }

    private Mat correctPerspective(Mat source) {
        Mat gray = grayscale(source);
        Mat edges = new Mat();
        Imgproc.Canny(gray, edges, 60, 180);
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(edges, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        double minimum = source.cols() * source.rows() * 0.45;
        MatOfPoint2f best = null;
        double bestArea = 0;
        for (MatOfPoint contour : contours) {
            double perimeter = Imgproc.arcLength(new MatOfPoint2f(contour.toArray()), true);
            MatOfPoint2f approximation = new MatOfPoint2f();
            Imgproc.approxPolyDP(new MatOfPoint2f(contour.toArray()), approximation, perimeter * 0.02, true);
            double area = Math.abs(Imgproc.contourArea(contour));
            Rect bounds = Imgproc.boundingRect(contour);
            boolean pageLike = bounds.width >= source.cols() * 0.75
                    && bounds.height >= source.rows() * 0.75
                    && bounds.x <= source.cols() * 0.12
                    && bounds.y <= source.rows() * 0.12;
            if (approximation.total() == 4 && pageLike && area > minimum && area > bestArea) {
                if (best != null) best.release();
                best = approximation;
                bestArea = area;
            } else approximation.release();
            contour.release();
        }
        gray.release(); edges.release();
        if (best == null) return source.clone();
        Point[] ordered = orderCorners(best.toArray());
        best.release();
        int width = (int) Math.max(distance(ordered[0], ordered[1]), distance(ordered[3], ordered[2]));
        int height = (int) Math.max(distance(ordered[0], ordered[3]), distance(ordered[1], ordered[2]));
        if (width < 100 || height < 100) return source.clone();
        MatOfPoint2f destination = new MatOfPoint2f(new Point(0, 0), new Point(width - 1, 0),
                new Point(width - 1, height - 1), new Point(0, height - 1));
        Mat transform = Imgproc.getPerspectiveTransform(new MatOfPoint2f(ordered), destination);
        Mat corrected = new Mat();
        Imgproc.warpPerspective(source, corrected, transform, new Size(width, height));
        destination.release(); transform.release();
        return corrected;
    }

    private Mat grayscale(Mat source) {
        Mat gray = new Mat();
        if (source.channels() == 1) source.copyTo(gray);
        else Imgproc.cvtColor(source, gray, Imgproc.COLOR_BGR2GRAY);
        return gray;
    }

    private Mat preprocess(Mat gray) {
        Mat denoised = new Mat();
        Imgproc.GaussianBlur(gray, denoised, new Size(3, 3), 0);
        Mat binary = new Mat();
        Imgproc.adaptiveThreshold(denoised, binary, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV, 31, 11);
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, 2));
        Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_OPEN, kernel);
        denoised.release(); kernel.release();
        return binary;
    }

    /** Removes long, regularly-spaced graph-paper lines while preserving shorter UML borders. */
    private Mat suppressGrid(Mat binary, boolean strict) {
        int horizontalLength = Math.max(80, binary.cols() / (strict ? 7 : 5));
        int verticalLength = Math.max(80, binary.rows() / (strict ? 7 : 5));
        Mat horizontalKernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT, new Size(horizontalLength, 1));
        Mat verticalKernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT, new Size(1, verticalLength));
        Mat horizontal = new Mat();
        Mat vertical = new Mat();
        Imgproc.morphologyEx(binary, horizontal, Imgproc.MORPH_OPEN, horizontalKernel);
        Imgproc.morphologyEx(binary, vertical, Imgproc.MORPH_OPEN, verticalKernel);

        if (!hasPeriodicLines(horizontal, true, strict)) horizontal.setTo(Scalar.all(0));
        if (!hasPeriodicLines(vertical, false, strict)) vertical.setTo(Scalar.all(0));

        Mat grid = new Mat();
        Core.bitwise_or(horizontal, vertical, grid);
        Mat dilationKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT,
                new Size(strict ? 3 : 2, strict ? 3 : 2));
        Imgproc.dilate(grid, grid, dilationKernel);
        Mat cleaned = new Mat();
        Core.subtract(binary, grid, cleaned);
        horizontalKernel.release(); verticalKernel.release(); horizontal.release(); vertical.release();
        grid.release(); dilationKernel.release();
        return cleaned;
    }

    private boolean hasPeriodicLines(Mat mask, boolean horizontal, boolean strict) {
        List<Integer> positions = new ArrayList<>();
        int axisLength = horizontal ? mask.rows() : mask.cols();
        int span = horizontal ? mask.cols() : mask.rows();
        double requiredCoverage = strict ? 0.24 : 0.38;
        for (int axis = 0; axis < axisLength; axis++) {
            int pixels = horizontal ? Core.countNonZero(mask.row(axis)) : Core.countNonZero(mask.col(axis));
            if (pixels >= span * requiredCoverage
                    && (positions.isEmpty() || axis - positions.getLast() > 2)) positions.add(axis);
        }
        // A UML classifier normally contributes only 2-4 long borders/dividers. A grid
        // produces many periodic lines across the page, so require a stronger signal.
        if (positions.size() < 6) return false;
        List<Integer> gaps = new ArrayList<>();
        for (int index = 1; index < positions.size(); index++) {
            int gap = positions.get(index) - positions.get(index - 1);
            if (gap >= 5) gaps.add(gap);
        }
        if (gaps.size() < 5) return false;
        gaps.sort(Integer::compareTo);
        double median = gaps.get(gaps.size() / 2);
        long regular = gaps.stream().filter(gap -> Math.abs(gap - median) <= Math.max(3, median * 0.35)).count();
        return regular >= Math.max(5, Math.ceil(gaps.size() * 0.55));
    }

    private Mat maskClassifierInteriors(Mat binary, List<ClassifierRegion> regions) {
        Mat result = binary.clone();
        for (ClassifierRegion region : regions) {
            Rect rect = region.rect();
            int inset = Math.max(4, Math.min(rect.width, rect.height) / 35);
            if (rect.width > inset * 2 && rect.height > inset * 2) {
                Imgproc.rectangle(result,
                        new Point(rect.x + inset, rect.y + inset),
                        new Point(rect.x + rect.width - inset, rect.y + rect.height - inset),
                        Scalar.all(0), Imgproc.FILLED);
            }
        }
        return result;
    }

    private String ocr(Mat image, Rect rect, int psm) {
        Rect safe = clamp(rect, image.cols(), image.rows());
        if (safe.width < 2 || safe.height < 2) return "";
        Mat region = image.submat(safe);
        LocalOcrEngine.OcrResult result = ocr.recognize(region, psm);
        region.release();
        return result.available() ? result.text() : "";
    }

    private DetectedType detectedType(String raw, Map<String, String> classifierNames) {
        String value = identifier(raw);
        if (value.isBlank()) return new DetectedType("UNKNOWN", "UNKNOWN");
        String normalized = value.toUpperCase(Locale.ROOT);
        String primitive = switch (normalized) {
            case "STRING", "CADENA", "TEXT" -> "STRING";
            case "INTEGER", "INT", "ENTERO" -> "INTEGER";
            case "BOOLEAN", "BOOL", "BOOLEANO" -> "BOOLEAN";
            case "REAL", "DOUBLE", "DECIMAL", "FLOAT" -> "REAL";
            case "UNLIMITEDNATURAL", "UNLIMITED_NATURAL" -> "UNLIMITED_NATURAL";
            default -> null;
        };
        if (primitive != null) return new DetectedType("PRIMITIVE", primitive);
        String classifierId = classifierNames.get(value.toLowerCase(Locale.ROOT));
        return classifierId == null ? new DetectedType("UNKNOWN", value)
                : new DetectedType("CLASSIFIER", classifierId);
    }

    private String visibility(String symbol) {
        if (symbol == null) return "UNKNOWN";
        return switch (symbol) { case "+" -> "PUBLIC"; case "-" -> "PRIVATE";
            case "#" -> "PROTECTED"; case "~" -> "PACKAGE"; default -> "UNKNOWN"; };
    }

    private Multiplicity parseMultiplicity(String text) {
        String value = text == null ? "" : text.replace(" ", "");
        return switch (value) {
            case "1" -> new Multiplicity(1, 1, false, true);
            case "0..1" -> new Multiplicity(0, 1, false, true);
            case "*", "0..*" -> new Multiplicity(0, null, true, true);
            case "1..*" -> new Multiplicity(1, null, true, true);
            default -> new Multiplicity(null, null, false, false);
        };
    }

    private ClassifierRegion nearestClassifier(Point point, List<ClassifierRegion> regions, double tolerance) {
        return regions.stream().filter(value -> distanceToBorder(point, value.rect()) <= tolerance)
                .min(Comparator.comparingDouble(value -> distanceToBorder(point, value.rect())))
                .orElse(null);
    }

    private boolean isDashed(Mat binary, Point first, Point second) {
        int transitions = 0; boolean previous = false; int present = 0; int samples = 80;
        for (int index = 8; index < samples - 8; index++) {
            double ratio = index / (double) (samples - 1);
            int x = clamp((int) Math.round(first.x + (second.x - first.x) * ratio), 0, binary.cols() - 1);
            int y = clamp((int) Math.round(first.y + (second.y - first.y) * ratio), 0, binary.rows() - 1);
            boolean current = binary.get(y, x)[0] > 0;
            if (current) present++;
            if (index > 8 && current != previous) transitions++;
            previous = current;
        }
        return transitions >= 6 && present < (samples - 16) * 0.72;
    }

    private Point[] orderCorners(Point[] points) {
        Point topLeft = points[0], topRight = points[0], bottomRight = points[0], bottomLeft = points[0];
        for (Point point : points) {
            if (point.x + point.y < topLeft.x + topLeft.y) topLeft = point;
            if (point.x + point.y > bottomRight.x + bottomRight.y) bottomRight = point;
            if (point.y - point.x < topRight.y - topRight.x) topRight = point;
            if (point.y - point.x > bottomLeft.y - bottomLeft.x) bottomLeft = point;
        }
        return new Point[] {topLeft, topRight, bottomRight, bottomLeft};
    }

    private List<String> lines(String text) {
        if (text == null || text.isBlank()) return List.of();
        return text.lines().map(String::trim).filter(value -> !value.isBlank()).toList();
    }
    private boolean interfaceMarker(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replace(" ", "");
        return normalized.contains("<<interface>>") || normalized.contains("«interface»");
    }
    private boolean hasFeatureLikeHeader(ClassifierRegion region) {
        String name = region.classifier().name().toLowerCase(Locale.ROOT).trim();
        return name.contains(":") || name.contains("(") || name.contains(")")
                || name.matches("^[+\\-#~].*")
                || name.matches(".*\\b(string|integer|float|date|boolean|real)\\b.*");
    }
    private String identifier(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("^[^\\p{L}_]+|[^\\p{L}\\p{N}_]+$", "");
    }
    private String clean(String value) { return value == null ? "" : value.replace('—', '-').replace('–', '-').trim(); }
    private String safeText(String value) { return value.length() > 80 ? value.substring(0, 80) : value; }
    private String suggestedName(String fileName) {
        if (fileName == null || fileName.isBlank()) return "Diagrama importado";
        String base = fileName.replaceFirst("(?i)\\.(png|jpe?g|webp)$", "").trim();
        return base.isBlank() ? "Diagrama importado" : base;
    }
    private DetectionRegion normalizedRegion(Rect rectangle, int width, int height) {
        return new DetectionRegion(
                rectangle.x / (double) width,
                rectangle.y / (double) height,
                rectangle.width / (double) width,
                rectangle.height / (double) height);
    }
    private double candidateScore(Rect rectangle, double rectangularity, double imageArea) {
        return rectangularity * 2.0 + Math.sqrt(rectangle.area() / imageArea) * 4.0;
    }
    private boolean sameClassifier(Rect first, Rect second) {
        if (intersectionOverUnion(first, second) >= 0.5) return true;
        if (containmentRatio(first, second) >= 0.72) return true;
        double sizeRatio = Math.min(first.area(), second.area()) / Math.max(1.0, Math.max(first.area(), second.area()));
        double diagonal = Math.hypot(Math.max(first.width, second.width), Math.max(first.height, second.height));
        return sizeRatio >= 0.72 && distance(center(first), center(second)) <= diagonal * 0.12;
    }
    private double intersectionOverUnion(Rect first, Rect second) {
        double intersection = intersectionArea(first, second);
        return intersection / Math.max(1.0, first.area() + second.area() - intersection);
    }
    private double containmentRatio(Rect first, Rect second) {
        return intersectionArea(first, second) / Math.max(1.0, Math.min(first.area(), second.area()));
    }
    private double intersectionArea(Rect first, Rect second) {
        int x1 = Math.max(first.x, second.x), y1 = Math.max(first.y, second.y);
        int x2 = Math.min(first.x + first.width, second.x + second.width);
        int y2 = Math.min(first.y + first.height, second.y + second.height);
        return Math.max(0, x2 - x1) * (double) Math.max(0, y2 - y1);
    }
    private int densityLimit(Mat binary) {
        int areaBased = (int) Math.round(1.0 / Math.max(0.001, minimumAreaRatio) / 16.0);
        int resolutionAllowance = (int) Math.ceil(binary.cols() * binary.rows() / 1_000_000.0) * 4;
        return Math.max(12, Math.min(50, Math.max(areaBased, resolutionAllowance)));
    }
    private boolean isAbnormallyDense(List<Rect> rectangles, Mat binary) {
        if (rectangles.size() <= densityLimit(binary)) return false;
        double smallThreshold = binary.cols() * (double) binary.rows() * minimumAreaRatio * 2.2;
        long smallCandidates = rectangles.stream().filter(value -> value.area() < smallThreshold).count();
        double occupied = rectangles.stream().mapToDouble(Rect::area).sum()
                / Math.max(1.0, binary.cols() * (double) binary.rows());
        return smallCandidates >= Math.ceil(rectangles.size() * 0.65) || occupied > 1.8;
    }
    private List<DetectionWarning> groupedWarnings(List<DetectionWarning> warnings) {
        Map<String, List<DetectionWarning>> byCode = new java.util.LinkedHashMap<>();
        warnings.forEach(warning -> byCode.computeIfAbsent(warning.code(), ignored -> new ArrayList<>()).add(warning));
        List<DetectionWarning> grouped = new ArrayList<>();
        byCode.forEach((code, values) -> {
            DetectionWarning first = values.getFirst();
            if (values.size() == 1) grouped.add(first);
            else grouped.add(new DetectionWarning(code,
                    first.message() + " (" + values.size() + " casos)", null));
        });
        return List.copyOf(grouped);
    }
    private int safeHeight(int requested, int maximum) { return Math.max(1, Math.min(requested, maximum)); }
    private boolean inside(Point point, Rect rect, int inset) { return point.x > rect.x + inset && point.x < rect.x + rect.width - inset && point.y > rect.y + inset && point.y < rect.y + rect.height - inset; }
    private double distanceToBorder(Point p, Rect r) {
        double dx = Math.max(Math.max(r.x - p.x, 0), p.x - (r.x + r.width));
        double dy = Math.max(Math.max(r.y - p.y, 0), p.y - (r.y + r.height));
        if (dx > 0 || dy > 0) return Math.hypot(dx, dy);
        return Math.min(Math.min(p.x - r.x, r.x + r.width - p.x), Math.min(p.y - r.y, r.y + r.height - p.y));
    }
    private Point center(Rect rect) { return new Point(rect.x + rect.width / 2.0, rect.y + rect.height / 2.0); }
    private double distance(Point first, Point second) { return Math.hypot(first.x - second.x, first.y - second.y); }
    private Rect around(Point p, int maxWidth, int maxHeight, int width, int height) { return clamp(new Rect((int) p.x - width / 2, (int) p.y - height / 2, width, height), maxWidth, maxHeight); }
    private Rect clamp(Rect value, int width, int height) {
        int x = clamp(value.x, 0, Math.max(0, width - 1)); int y = clamp(value.y, 0, Math.max(0, height - 1));
        return new Rect(x, y, Math.max(1, Math.min(value.width, width - x)), Math.max(1, Math.min(value.height, height - y)));
    }
    private int clamp(int value, int minimum, int maximum) { return Math.max(minimum, Math.min(value, maximum)); }
    private void release(Mat... mats) { for (Mat mat : mats) if (mat != null) mat.release(); }
    private void ensureOpenCv() {
        if (openCvLoaded) return;
        synchronized (LocalImageAnalysisProvider.class) {
            if (openCvLoaded) return;
            try { OpenCV.loadLocally(); openCvLoaded = true; }
            catch (RuntimeException | UnsatisfiedLinkError exception) {
                throw new ImageImportException("LOCAL_IMAGE_PROCESSING_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                        "El motor local de procesamiento de imágenes no está disponible.", exception);
            }
        }
    }

    private record ClassifierRegion(Rect rect, DetectedClassifier classifier,
            String attributesText, String operationsText, int dividerCount) {}
    private record Candidate(Rect rect, double score) {}
    private record CandidateDetection(List<Rect> rectangles, int rawCandidateCount) {}
    private record CompartmentCandidate(Rect rect, int parts) {}
    private record MergeChoice(int first, int second, double score) {}
    private record DetectedSegment(Point first, Point second) {
        private double length() { return Math.hypot(second.x - first.x, second.y - first.y); }
    }
    private record GraphAttachment(ClassifierRegion classifier, Point point) {}
    private record Marker(Point center, int vertices, boolean filled) {}
    private record Multiplicity(Integer lower, Integer upper, boolean unlimited, boolean known) {}
    private record LineSegment(double start, double end, double axis) {
        private static LineSegment horizontal(Point first, Point second) {
            return new LineSegment(Math.min(first.x, second.x), Math.max(first.x, second.x),
                    (first.y + second.y) / 2.0);
        }
    }

    private static final class DisjointSet {
        private final int[] parent;
        private DisjointSet(int size) {
            parent = new int[size];
            for (int index = 0; index < size; index++) parent[index] = index;
        }
        private int find(int value) {
            if (parent[value] != value) parent[value] = find(parent[value]);
            return parent[value];
        }
        private void union(int first, int second) {
            int firstRoot = find(first), secondRoot = find(second);
            if (firstRoot != secondRoot) parent[secondRoot] = firstRoot;
        }
    }
}
