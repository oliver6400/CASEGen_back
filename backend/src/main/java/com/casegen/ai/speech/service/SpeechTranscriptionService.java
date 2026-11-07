package com.casegen.ai.speech.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.vosk.Model;
import org.vosk.Recognizer;

@Service
public class SpeechTranscriptionService {

    private static final long MAX_AUDIO_BYTES = 10 * 1024 * 1024;
    private static final AudioFormat RECOGNITION_FORMAT =
            new AudioFormat(16_000, 16, 1, true, false);

    private final Path modelPath;
    private final ObjectMapper objectMapper;
    private volatile Model model;

    public SpeechTranscriptionService(
            @Value("${casegen.speech.model-path:runtime/vosk-model-small-es}") Path modelPath,
            ObjectMapper objectMapper) {
        this.modelPath = modelPath;
        this.objectMapper = objectMapper;
    }

    public String transcribe(MultipartFile audio) {
        if (audio.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audio recording is empty");
        }
        if (audio.getSize() > MAX_AUDIO_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Audio recording must not exceed 10 MB");
        }

        try (AudioInputStream source = AudioSystem.getAudioInputStream(
                new ByteArrayInputStream(audio.getBytes()))) {
            AudioInputStream recognitionAudio = source.getFormat().matches(RECOGNITION_FORMAT)
                    ? source
                    : AudioSystem.getAudioInputStream(RECOGNITION_FORMAT, source);
            try (recognitionAudio) {
                return recognize(recognitionAudio);
            }
        } catch (UnsupportedAudioFileException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Audio must be a supported WAV recording", exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Audio recording could not be read", exception);
        }
    }

    private String recognize(AudioInputStream audio) throws IOException {
        try (Recognizer recognizer = new Recognizer(getModel(), RECOGNITION_FORMAT.getSampleRate())) {
            byte[] buffer = new byte[8_192];
            int bytesRead;
            while ((bytesRead = audio.read(buffer)) != -1) {
                recognizer.acceptWaveForm(buffer, bytesRead);
            }
            JsonNode result = objectMapper.readTree(recognizer.getFinalResult());
            return result.path("text").asText("");
        }
    }

    private Model getModel() throws IOException {
        Model loadedModel = model;
        if (loadedModel != null) {
            return loadedModel;
        }
        synchronized (this) {
            if (model == null) {
                if (!Files.isDirectory(modelPath)) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Offline speech model is missing. Configure VOSK_MODEL_PATH.");
                }
                model = new Model(modelPath.toString());
            }
            return model;
        }
    }
}
