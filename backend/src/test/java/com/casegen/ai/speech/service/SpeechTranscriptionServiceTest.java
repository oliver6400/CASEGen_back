package com.casegen.ai.speech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class SpeechTranscriptionServiceTest {

    @TempDir
    Path temporaryDirectory;

    private SpeechTranscriptionService service(Path modelPath) {
        return new SpeechTranscriptionService(modelPath, new ObjectMapper());
    }

    @Test
    void rejectsEmptyAudio() {
        MockMultipartFile audio = new MockMultipartFile("audio", new byte[0]);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service(temporaryDirectory).transcribe(audio));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void rejectsUnsupportedAudio() {
        MockMultipartFile audio = new MockMultipartFile("audio", "not-a-wav".getBytes());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service(temporaryDirectory).transcribe(audio));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void reportsWhenOfflineModelIsNotInstalled() throws IOException {
        MockMultipartFile audio = new MockMultipartFile("audio", "recording.wav",
                "audio/wav", silentWav(16_000));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service(temporaryDirectory.resolve("missing-model")).transcribe(audio));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void convertsBrowserAudioSampleRateBeforeRecognition() throws IOException {
        MockMultipartFile audio = new MockMultipartFile("audio", "recording.wav",
                "audio/wav", silentWav(48_000));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service(temporaryDirectory.resolve("missing-model")).transcribe(audio));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    private byte[] silentWav(int sampleRate) throws IOException {
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        byte[] samples = new byte[sampleRate / 50 * format.getFrameSize()];
        try (AudioInputStream stream = new AudioInputStream(
                new java.io.ByteArrayInputStream(samples), format, samples.length / format.getFrameSize());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            AudioSystem.write(stream, javax.sound.sampled.AudioFileFormat.Type.WAVE, output);
            return output.toByteArray();
        }
    }
}
