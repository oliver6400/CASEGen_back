package com.casegen.ai.message.exception;

public enum AiMessageErrorCode {
    SESSION_NOT_FOUND,
    SESSION_NOT_ACTIVE,
    CLIENT_MESSAGE_ID_REUSED,
    INVALID_CONTEXT,
    MESSAGE_TOO_LONG,
    MESSAGE_INVALID,
    MESSAGE_CONCURRENT_WRITE
}
