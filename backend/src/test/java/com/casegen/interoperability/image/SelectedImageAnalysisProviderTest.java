package com.casegen.interoperability.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SelectedImageAnalysisProviderTest {
    private final OpenAiClassDiagramImageAnalyzer openAi = mock(OpenAiClassDiagramImageAnalyzer.class);
    private final GeminiImageAnalysisProvider gemini = mock(GeminiImageAnalysisProvider.class);
    private final LocalImageAnalysisProvider local = mock(LocalImageAnalysisProvider.class);

    @Test
    void geminiIsSelectedByConfiguration() {
        when(gemini.provider()).thenReturn("GEMINI");
        assertEquals("GEMINI", selected("GEMINI", true).provider());
    }

    @Test
    void geminiIsDefault() {
        when(gemini.provider()).thenReturn("GEMINI");
        assertEquals("GEMINI", selected("", true).provider());
    }

    @Test
    void localIsSelectableWithoutApiConfiguration() {
        when(local.provider()).thenReturn("LOCAL");
        when(local.configured()).thenReturn(true);
        SelectedImageAnalysisProvider selected = selected("LOCAL", true);
        assertEquals("LOCAL", selected.provider());
        assertEquals(true, selected.configured());
    }

    @Test
    void openAiRemainsSelectable() {
        when(openAi.provider()).thenReturn("OPENAI");
        assertEquals("OPENAI", selected("OPENAI", true).provider());
    }

    @Test
    void unknownProviderFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> selected("OTHER", true));
    }

    @Test
    void successfulGeminiAnalysisNeverRunsLocalProvider() {
        DetectedClassDiagram result = diagram();
        when(gemini.provider()).thenReturn("GEMINI");
        when(gemini.analyze(any(), anyString(), anyString())).thenReturn(result);

        assertEquals(result, selected("GEMINI", true).analyze(new byte[] {1}, "image/png", "uml.png"));

        verify(local, never()).analyze(any(), anyString(), anyString());
    }

    @Test
    void quotaFailureUsesLocalFallbackAndAddsWarning() {
        when(gemini.provider()).thenReturn("GEMINI");
        when(local.configured()).thenReturn(true);
        when(gemini.analyze(any(), anyString(), anyString())).thenThrow(new ImageImportException(
                "GEMINI_QUOTA_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS, "quota"));
        when(local.analyze(any(), anyString(), anyString())).thenReturn(diagram());

        DetectedClassDiagram result = selected("GEMINI", true)
                .analyze(new byte[] {1}, "image/png", "uml.png");

        assertEquals("GEMINI_LOCAL_FALLBACK", result.warnings().getLast().code());
    }

    @Test
    void unavailableGeminiUsesLocalFallbackWhenEnabled() {
        when(gemini.provider()).thenReturn("GEMINI");
        when(local.configured()).thenReturn(true);
        when(gemini.analyze(any(), anyString(), anyString())).thenThrow(new ImageImportException(
                "GEMINI_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, "temporary"));
        when(local.analyze(any(), anyString(), anyString())).thenReturn(diagram());

        SelectedImageAnalysisProvider selected = selected("GEMINI", true);
        DetectedClassDiagram result = selected.analyze(new byte[] {1}, "image/jpeg", "uml.jpg");

        assertEquals(true, selected.fallbackLocalAvailable());
        assertEquals("GEMINI_LOCAL_FALLBACK", result.warnings().getLast().code());
    }

    @Test
    void disabledFallbackPreservesTransientGeminiError() {
        when(gemini.provider()).thenReturn("GEMINI");
        when(local.configured()).thenReturn(true);
        when(gemini.analyze(any(), anyString(), anyString())).thenThrow(new ImageImportException(
                "GEMINI_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, "temporary"));

        assertThrows(ImageImportException.class,
                () -> selected("GEMINI", false).analyze(new byte[] {1}, "image/jpeg", "uml.jpg"));
        verify(local, never()).analyze(any(), anyString(), anyString());
    }

    @Test
    void invalidGeminiCredentialsNeverUseLocalFallback() {
        when(gemini.provider()).thenReturn("GEMINI");
        when(local.configured()).thenReturn(true);
        when(gemini.analyze(any(), anyString(), anyString())).thenThrow(new ImageImportException(
                "GEMINI_CONFIGURATION_INVALID", HttpStatus.SERVICE_UNAVAILABLE, "invalid"));

        assertThrows(ImageImportException.class,
                () -> selected("GEMINI", true).analyze(new byte[] {1}, "image/png", "uml.png"));
        verify(local, never()).analyze(any(), anyString(), anyString());
    }

    private SelectedImageAnalysisProvider selected(String provider, boolean fallback) {
        return new SelectedImageAnalysisProvider(openAi, gemini, local, provider, fallback);
    }

    private DetectedClassDiagram diagram() {
        return new DetectedClassDiagram("Model", List.of(
                new DetectedClassDiagram.DetectedClassifier("c1", "Customer", "CLASS", "PUBLIC", 1)),
                List.of(), List.of(), List.of(), List.of(), 1);
    }
}
