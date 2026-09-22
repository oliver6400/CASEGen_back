package com.casegen.interoperability.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.opencv.core.Mat;

class LocalImageAnalysisProviderTest {

    @Test
    void cleanDigitalDiagramProducesClassifierAndParsedCompartments() throws Exception {
        LocalImageAnalysisProvider provider = new LocalImageAnalysisProvider(new QueueOcr(
                "Cliente", "-id : Integer\n-name : String", "+save() : Boolean"));

        DetectedClassDiagram result = provider.analyze(cleanDigitalFixture(), "image/png", "clientes.png");

        assertFalse(result.detectedClassifiers().isEmpty());
        assertEquals("Cliente", result.detectedClassifiers().getFirst().name());
        assertTrue(result.detectedAttributes().stream().anyMatch(attribute -> "id".equals(attribute.name())));
        assertTrue(result.detectedOperations().stream().anyMatch(operation -> "save".equals(operation.name())));
    }

    @Test
    void handDrawnDiagramStillProducesEditableProposal() throws Exception {
        LocalImageAnalysisProvider provider = new LocalImageAnalysisProvider(new UnavailableOcr());

        DetectedClassDiagram result = provider.analyze(handDrawnFixture(), "image/jpeg", "pizarra.jpg");

        assertFalse(result.detectedClassifiers().isEmpty());
        assertTrue(result.detectedClassifiers().getFirst().name().startsWith("Clase"));
    }

    @Test
    void shadowedImageIsPreprocessedLocally() throws Exception {
        LocalImageAnalysisProvider provider = new LocalImageAnalysisProvider(new UnavailableOcr());

        DetectedClassDiagram result = provider.analyze(shadowFixture(), "image/png", "sombra.png");

        assertFalse(result.detectedClassifiers().isEmpty());
    }

    @Test
    void unreadableTextKeepsGeometryAndAddsWarning() throws Exception {
        LocalImageAnalysisProvider provider = new LocalImageAnalysisProvider(new UnavailableOcr());

        DetectedClassDiagram result = provider.analyze(cleanDigitalFixture(), "image/png", "ilegible.png");

        assertFalse(result.detectedClassifiers().isEmpty());
        assertTrue(result.warnings().stream()
                .anyMatch(warning -> "CLASSIFIER_NAME_UNREADABLE".equals(warning.code())));
        assertTrue(result.warnings().stream()
                .anyMatch(warning -> "LOCAL_OCR_UNAVAILABLE".equals(warning.code())));
    }

    @Test
    void solidLineBetweenClassifiersProducesAssociationProposal() throws Exception {
        LocalImageAnalysisProvider provider = new LocalImageAnalysisProvider(new QueueOcr(
                "Cliente", "", "", "Pedido", "", "", "", ""));

        DetectedClassDiagram result = provider.analyze(associationFixture(), "image/png", "relacion.png");

        assertTrue(result.detectedClassifiers().size() >= 2, result.toString());
        assertTrue(result.detectedRelationships().stream()
                .anyMatch(relationship -> "ASSOCIATION".equals(relationship.type())), result.toString());
    }

    @Test
    void graphPaperDoesNotBecomeDozensOfClassifiers() throws Exception {
        LocalImageAnalysisProvider provider = new LocalImageAnalysisProvider(new UnavailableOcr());

        DetectedClassDiagram result = provider.analyze(graphPaperFixture(), "image/png", "papel-cuadriculado.png");

        assertEquals(6, result.detectedClassifiers().size(), result.toString());
        assertTrue(result.detectedClassifiers().stream().allMatch(value -> value.region() != null));
        assertEquals(1, result.warnings().stream()
                .filter(warning -> "LOCAL_OCR_UNAVAILABLE".equals(warning.code())).count());
    }

    @Test
    void oneCompartmentProducesOneClassifier() throws Exception {
        assertEquals(1, analyzeCompartments(1).detectedClassifiers().size());
    }

    @Test
    void twoContiguousCompartmentsProduceOneClassifier() throws Exception {
        assertEquals(1, analyzeCompartments(2).detectedClassifiers().size());
    }

    @Test
    void threeContiguousCompartmentsIncludingEmptyOneProduceOneClassifier() throws Exception {
        assertEquals(1, analyzeCompartments(3).detectedClassifiers().size());
    }

    @Test
    void interfaceMarkerIsReadOnlyFromTheGroupedNameCompartment() throws Exception {
        LocalImageAnalysisProvider provider = new LocalImageAnalysisProvider(new QueueOcr(
                "<<interface>>\nPayable", "", ""));

        DetectedClassDiagram result = provider.analyze(compartmentFixture(3), "image/png", "interface.png");

        assertEquals("INTERFACE", result.detectedClassifiers().getFirst().kind());
        assertEquals("Payable", result.detectedClassifiers().getFirst().name());
    }

    @Test
    void separatedBoxesRemainSeparateClassifiers() throws Exception {
        LocalImageAnalysisProvider provider = new LocalImageAnalysisProvider(new UnavailableOcr());

        DetectedClassDiagram result = provider.analyze(separatedClassifierFixture(), "image/png", "separadas.png");

        assertEquals(2, result.detectedClassifiers().size(), result.toString());
    }

    @Test
    void eightThreeCompartmentClassifiersAreNotExpandedToEleven() throws Exception {
        LocalImageAnalysisProvider provider = new LocalImageAnalysisProvider(new UnavailableOcr());

        DetectedClassDiagram result = provider.analyze(eightClassifierFixture(), "image/png", "uml-ocho.png");

        assertEquals(8, result.detectedClassifiers().size(), result.toString());
    }

    @Test
    void orthogonalRelationshipWithOneCornerProducesOneAssociation() throws Exception {
        LocalImageAnalysisProvider provider = new LocalImageAnalysisProvider(new UnavailableOcr());

        DetectedClassDiagram result = provider.analyze(orthogonalRelationshipFixture(false), "image/png", "codo.png");

        assertTrue(result.detectedRelationships().stream()
                .anyMatch(value -> "ASSOCIATION".equals(value.type())), result.toString());
    }

    @Test
    void orthogonalRelationshipWithSeveralCornersProducesOneAssociation() throws Exception {
        LocalImageAnalysisProvider provider = new LocalImageAnalysisProvider(new UnavailableOcr());

        DetectedClassDiagram result = provider.analyze(orthogonalRelationshipFixture(true), "image/png", "codos.png");

        assertEquals(1, result.detectedRelationships().stream()
                .filter(value -> "ASSOCIATION".equals(value.type())).count(), result.toString());
    }

    @Test
    void sharedGeneralizationJunctionProducesRelationshipForEveryChild() throws Exception {
        LocalImageAnalysisProvider provider = new LocalImageAnalysisProvider(new UnavailableOcr());

        DetectedClassDiagram result = provider.analyze(generalizationJunctionFixture(), "image/png", "herencia.png");

        assertEquals(3, result.detectedRelationships().stream()
                .filter(value -> "GENERALIZATION".equals(value.type())).count(), result.toString());
    }

    @Test
    void hollowAndFilledDiamondsProduceAggregationAndComposition() throws Exception {
        LocalImageAnalysisProvider provider = new LocalImageAnalysisProvider(new UnavailableOcr());

        DetectedClassDiagram aggregation = provider.analyze(diamondFixture(false), "image/png", "aggregation.png");
        DetectedClassDiagram composition = provider.analyze(diamondFixture(true), "image/png", "composition.png");

        assertTrue(aggregation.detectedRelationships().stream()
                .anyMatch(value -> "AGGREGATION".equals(value.type())), aggregation.toString());
        assertTrue(composition.detectedRelationships().stream()
                .anyMatch(value -> "COMPOSITION".equals(value.type())), composition.toString());
    }

    @Test
    void multiplicitiesAreReadOnlyNextToClassifierTouchPoints() throws Exception {
        LocalImageAnalysisProvider provider = new LocalImageAnalysisProvider(new QueueOcr(
                "Customer", "", "", "Order", "", "", "1", "0..*"));

        DetectedClassDiagram result = provider.analyze(associationFixture(), "image/png", "multiplicidades.png");

        var relationship = result.detectedRelationships().getFirst();
        assertTrue(relationship.source().multiplicityKnown(), result.toString());
        assertTrue(relationship.target().multiplicityKnown(), result.toString());
        assertTrue((Integer.valueOf(1).equals(relationship.source().lowerBound())
                        && Integer.valueOf(1).equals(relationship.source().upperBound()))
                || (Integer.valueOf(1).equals(relationship.target().lowerBound())
                        && Integer.valueOf(1).equals(relationship.target().upperBound())));
        assertTrue(relationship.source().upperUnlimited() || relationship.target().upperUnlimited());
    }

    private DetectedClassDiagram analyzeCompartments(int count) throws Exception {
        LocalImageAnalysisProvider provider = new LocalImageAnalysisProvider(new UnavailableOcr());
        return provider.analyze(compartmentFixture(count), "image/png", "compartimentos.png");
    }

    private byte[] cleanDigitalFixture() throws Exception {
        BufferedImage image = canvas();
        Graphics2D graphics = graphics(image);
        graphics.setStroke(new BasicStroke(4));
        graphics.drawRect(120, 70, 360, 260);
        graphics.drawLine(120, 135, 480, 135);
        graphics.drawLine(120, 235, 480, 235);
        graphics.dispose();
        return png(image);
    }

    private byte[] handDrawnFixture() throws Exception {
        BufferedImage image = canvas();
        Graphics2D graphics = graphics(image);
        graphics.setStroke(new BasicStroke(5));
        graphics.drawLine(115, 74, 482, 69);
        graphics.drawLine(482, 69, 476, 332);
        graphics.drawLine(476, 332, 118, 328);
        graphics.drawLine(118, 328, 115, 74);
        graphics.drawLine(117, 142, 480, 138);
        graphics.drawLine(119, 238, 478, 241);
        graphics.dispose();
        return jpeg(image);
    }

    private byte[] shadowFixture() throws Exception {
        BufferedImage image = canvas();
        Graphics2D graphics = graphics(image);
        graphics.setColor(new Color(205, 205, 205));
        graphics.fillRect(132, 82, 365, 265);
        graphics.setColor(Color.BLACK);
        graphics.setStroke(new BasicStroke(4));
        graphics.drawRect(110, 60, 360, 260);
        graphics.drawLine(110, 125, 470, 125);
        graphics.drawLine(110, 225, 470, 225);
        graphics.dispose();
        return png(image);
    }

    private byte[] associationFixture() throws Exception {
        BufferedImage image = canvas();
        Graphics2D graphics = graphics(image);
        graphics.setStroke(new BasicStroke(4));
        graphics.drawRect(30, 25, 220, 180);
        graphics.drawLine(30, 80, 250, 80);
        graphics.drawLine(30, 145, 250, 145);
        graphics.drawRect(390, 215, 220, 180);
        graphics.drawLine(390, 270, 610, 270);
        graphics.drawLine(390, 335, 610, 335);
        graphics.drawLine(255, 125, 385, 295);
        graphics.dispose();
        return png(image);
    }

    private byte[] graphPaperFixture() throws Exception {
        BufferedImage image = canvas(1600, 1200);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(205, 222, 235));
        graphics.setStroke(new BasicStroke(1));
        for (int x = 0; x < image.getWidth(); x += 40) graphics.drawLine(x, 0, x, image.getHeight());
        for (int y = 0; y < image.getHeight(); y += 40) graphics.drawLine(0, y, image.getWidth(), y);
        graphics.setColor(Color.BLACK);
        graphics.setStroke(new BasicStroke(5));
        int[][] locations = {{105, 105}, {650, 115}, {1105, 105}, {115, 655}, {655, 665}, {1110, 650}};
        for (int[] location : locations) {
            graphics.drawRect(location[0], location[1], 300, 205);
            graphics.drawLine(location[0], location[1] + 60, location[0] + 300, location[1] + 60);
            graphics.drawLine(location[0], location[1] + 135, location[0] + 300, location[1] + 135);
        }
        graphics.dispose();
        return png(image);
    }

    private byte[] compartmentFixture(int count) throws Exception {
        BufferedImage image = canvas();
        Graphics2D graphics = graphics(image);
        graphics.setStroke(new BasicStroke(4));
        drawStackedClassifier(graphics, 145, 70, 350, count);
        graphics.dispose();
        return png(image);
    }

    private byte[] separatedClassifierFixture() throws Exception {
        BufferedImage image = canvas();
        Graphics2D graphics = graphics(image);
        graphics.setStroke(new BasicStroke(4));
        drawStackedClassifier(graphics, 40, 45, 230, 2);
        drawStackedClassifier(graphics, 365, 225, 230, 2);
        graphics.dispose();
        return png(image);
    }

    private byte[] eightClassifierFixture() throws Exception {
        BufferedImage image = canvas(1200, 800);
        Graphics2D graphics = graphics(image);
        graphics.setStroke(new BasicStroke(4));
        int[][] positions = {{35, 35}, {325, 35}, {615, 35}, {905, 35},
                {35, 440}, {325, 440}, {615, 440}, {905, 440}};
        for (int[] position : positions) drawStackedClassifier(graphics, position[0], position[1], 230, 3);
        graphics.dispose();
        return png(image);
    }

    private byte[] orthogonalRelationshipFixture(boolean severalCorners) throws Exception {
        BufferedImage image = canvas(900, 600);
        Graphics2D graphics = graphics(image);
        graphics.setStroke(new BasicStroke(4));
        graphics.drawRect(40, 55, 230, 150);
        graphics.drawRect(620, 390, 230, 150);
        if (severalCorners) {
            graphics.drawLine(270, 130, 390, 130);
            graphics.drawLine(390, 130, 390, 300);
            graphics.drawLine(390, 300, 620, 300);
            graphics.drawLine(620, 300, 620, 465);
        } else {
            graphics.drawLine(270, 130, 500, 130);
            graphics.drawLine(500, 130, 500, 465);
            graphics.drawLine(500, 465, 620, 465);
        }
        graphics.dispose();
        return png(image);
    }

    private byte[] generalizationJunctionFixture() throws Exception {
        BufferedImage image = canvas(900, 650);
        Graphics2D graphics = graphics(image);
        graphics.setStroke(new BasicStroke(4));
        graphics.drawRect(335, 25, 230, 120);
        graphics.drawRect(25, 480, 220, 120);
        graphics.drawRect(340, 480, 220, 120);
        graphics.drawRect(655, 480, 220, 120);
        int[] xs = {135, 450, 765};
        graphics.drawPolygon(new int[]{430, 450, 470}, new int[]{170, 145, 170}, 3);
        graphics.drawLine(450, 170, 450, 320);
        graphics.drawLine(135, 320, 765, 320);
        for (int x : xs) graphics.drawLine(x, 320, x, 480);
        graphics.dispose();
        return png(image);
    }

    private byte[] diamondFixture(boolean filled) throws Exception {
        BufferedImage image = canvas(900, 500);
        Graphics2D graphics = graphics(image);
        graphics.setStroke(new BasicStroke(4));
        graphics.drawRect(35, 150, 230, 150);
        graphics.drawRect(635, 150, 230, 150);
        int[] x = {265, 290, 315, 290};
        int[] y = {225, 203, 225, 247};
        if (filled) graphics.fillPolygon(x, y, 4); else graphics.drawPolygon(x, y, 4);
        graphics.drawLine(315, 225, 635, 225);
        graphics.dispose();
        return png(image);
    }

    private void drawStackedClassifier(Graphics2D graphics, int x, int y, int width, int compartments) {
        int[] heights = {62, 82, 72};
        int top = y;
        for (int index = 0; index < compartments; index++) {
            graphics.drawRect(x, top, width, heights[index]);
            top += heights[index];
        }
    }

    private BufferedImage canvas() {
        return canvas(640, 420);
    }

    private BufferedImage canvas(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        return image;
    }

    private Graphics2D graphics(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLACK);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return graphics;
    }

    private byte[] png(BufferedImage image) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private byte[] jpeg(BufferedImage image) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return output.toByteArray();
    }

    private static final class QueueOcr implements LocalOcrEngine {
        private final Queue<String> responses;

        private QueueOcr(String... responses) {
            this.responses = new ArrayDeque<>(Arrays.asList(responses));
        }

        @Override
        public OcrResult recognize(Mat image, int pageSegmentationMode) {
            return new OcrResult(responses.isEmpty() ? "" : responses.remove(), true);
        }

        @Override
        public boolean available() {
            return true;
        }
    }

    private static final class UnavailableOcr implements LocalOcrEngine {
        @Override
        public OcrResult recognize(Mat image, int pageSegmentationMode) {
            return new OcrResult("", false);
        }

        @Override
        public boolean available() {
            return false;
        }
    }
}
