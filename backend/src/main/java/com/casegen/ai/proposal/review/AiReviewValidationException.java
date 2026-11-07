package com.casegen.ai.proposal.review;
public class AiReviewValidationException extends RuntimeException {private final AiReviewValidationErrorCode code;
 public AiReviewValidationException(AiReviewValidationErrorCode code,String message){super(message);this.code=code;}
 public AiReviewValidationErrorCode getCode(){return code;}}
