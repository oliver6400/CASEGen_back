package com.casegen.ai.proposal.controller;

import com.casegen.ai.proposal.service.AiProposalException;
import com.casegen.ai.proposal.service.AiProposalGenerationException;
import com.casegen.ai.proposal.validation.AiProposalValidationException;
import com.casegen.ai.provider.AiProviderException;
import com.casegen.ai.proposal.review.AiReviewGenerationException;
import com.casegen.ai.proposal.review.AiReviewValidationException;
import com.casegen.ai.proposal.review.AiReviewValidationErrorCode;
import com.casegen.shared.api.ApiErrorResponse;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = AiProposalController.class)
public class AiProposalExceptionHandler {

    @ExceptionHandler(AiProposalException.class)
    ResponseEntity<ApiErrorResponse> handleProposal(AiProposalException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case PROJECT_NOT_FOUND, PROPOSAL_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CLIENT_PROPOSAL_ID_REUSED, CLIENT_BATCH_ID_REUSED,
                    CONTEXT_REVISION_CHANGED -> HttpStatus.CONFLICT;
            case PROPOSAL_ALREADY_DECIDED, PROPOSAL_STALE,
                    PROPOSAL_DEPENDENCY_NOT_APPLIED, BATCH_STALE,
                    BATCH_ALREADY_DECIDED, BATCH_HAS_UNSATISFIED_DEPENDENCY ->
                    HttpStatus.CONFLICT;
            case PROPOSAL_NO_LONGER_VALID -> HttpStatus.UNPROCESSABLE_ENTITY;
            case INVALID_SOURCE_CONTEXT, INVALID_BATCH_PROPOSAL_PAYLOAD ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        String message = switch (exception.getCode()) {
            case PROPOSAL_ALREADY_DECIDED -> "La propuesta ya fue decidida.";
            case PROPOSAL_STALE -> "El modelo cambió desde que se creó esta propuesta.";
            case PROPOSAL_NO_LONGER_VALID ->
                    "La propuesta ya no es válida para el estado actual del modelo.";
            default -> exception.getMessage();
        };
        return error(status, exception.getCode().name(), message);
    }

    @ExceptionHandler(AiProposalGenerationException.class)
    ResponseEntity<ApiErrorResponse> handleGeneration(AiProposalGenerationException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case SOURCE_MESSAGE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_SOURCE_MESSAGE, NO_APPLICABLE_UML_PROPOSAL ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            case INVALID_PROVIDER_RESPONSE -> HttpStatus.BAD_GATEWAY;
            case INVALID_COMMAND -> HttpStatus.BAD_REQUEST;
        };
        return error(status, exception.getCode().name(), exception.getMessage());
    }

    @ExceptionHandler(AiProposalValidationException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(AiProposalValidationException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case INVALID_PAYLOAD, UNKNOWN_PAYLOAD_FIELD -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return error(status, exception.getCode().name(), exception.getMessage());
    }

    @ExceptionHandler(AiProviderException.class)
    ResponseEntity<ApiErrorResponse> handleProvider(AiProviderException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case NOT_CONFIGURED, UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
        };
        String message = switch (exception.getCode()) {
            case NOT_CONFIGURED -> "The selected AI provider is not configured";
            case UNAVAILABLE -> "The AI provider is unavailable";
            case TIMEOUT -> "The AI provider timed out";
            case RATE_LIMITED -> "The AI provider rate limit was reached";
            case INVALID_RESPONSE -> "The AI provider returned an invalid response";
        };
        return error(status, exception.getCode().name(), message);
    }

    @ExceptionHandler(AiReviewGenerationException.class)
    ResponseEntity<ApiErrorResponse> handleReviewGeneration(AiReviewGenerationException exception) {
        return error(HttpStatus.BAD_GATEWAY, "INVALID_REVIEW_RESPONSE",
                "La IA devolvió una revisión que no cumple el formato o los límites permitidos.");
    }

    @ExceptionHandler(AiReviewValidationException.class)
    ResponseEntity<ApiErrorResponse> handleReviewValidation(AiReviewValidationException exception) {
        HttpStatus status=exception.getCode()==AiReviewValidationErrorCode.REVIEW_CONTEXT_CHANGED
                ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY;
        return error(status,exception.getCode().name(),
                status==HttpStatus.CONFLICT
                        ? "El modelo cambió mientras se generaba la revisión. Inténtalo nuevamente."
                        : "La revisión contiene una recomendación incompatible con el modelo actual.");
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(code, message, Instant.now()));
    }
}
