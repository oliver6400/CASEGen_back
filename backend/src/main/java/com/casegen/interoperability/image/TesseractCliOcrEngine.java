package com.casegen.interoperability.image;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Local OCR adapter. No image or text leaves the host process. */
@Component
class TesseractCliOcrEngine implements LocalOcrEngine {
    private final String command;
    private final String language;
    private volatile Boolean available;

    TesseractCliOcrEngine(
            @Value("${casegen.image-import.local.tesseract-command:tesseract}") String command,
            @Value("${casegen.image-import.local.tesseract-language:eng}") String language) {
        this.command = command == null || command.isBlank() ? "tesseract" : command.trim();
        this.language = language == null || language.isBlank() ? "eng" : language.trim();
    }

    @Override
    public OcrResult recognize(Mat image, int pageSegmentationMode) {
        if (!isAvailable() || image == null || image.empty()) return new OcrResult("", false);
        Path temporary = null;
        try {
            temporary = Files.createTempFile("casegen-uml-ocr-", ".png");
            if (!Imgcodecs.imwrite(temporary.toString(), image)) return new OcrResult("", true);
            Process process = new ProcessBuilder(command, temporary.toString(), "stdout",
                    "-l", language, "--psm", Integer.toString(pageSegmentationMode))
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return new OcrResult("", true);
            }
            return new OcrResult(process.exitValue() == 0 ? output : "", true);
        } catch (IOException exception) {
            available = false;
            return new OcrResult("", false);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new OcrResult("", true);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    @Override
    public boolean available() {
        return isAvailable();
    }

    private boolean isAvailable() {
        Boolean current = available;
        if (current != null) return current;
        synchronized (this) {
            if (available != null) return available;
            try {
                Process process = new ProcessBuilder(command, "--version").redirectErrorStream(true).start();
                process.getInputStream().readAllBytes();
                available = process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
            } catch (Exception exception) {
                available = false;
            }
            return available;
        }
    }
}
