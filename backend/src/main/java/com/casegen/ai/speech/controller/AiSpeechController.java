package com.casegen.ai.speech.controller;

import com.casegen.ai.speech.dto.SpeechTranscriptionResponse;
import com.casegen.ai.speech.service.SpeechTranscriptionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai/speech")
public class AiSpeechController {

    private final SpeechTranscriptionService service;

    public AiSpeechController(SpeechTranscriptionService service) {
        this.service = service;
    }

    @PostMapping(path = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SpeechTranscriptionResponse transcribe(@RequestPart("audio") MultipartFile audio) {
        return new SpeechTranscriptionResponse(service.transcribe(audio));
    }
}
