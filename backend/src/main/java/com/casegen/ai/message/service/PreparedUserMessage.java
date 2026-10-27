package com.casegen.ai.message.service;

import com.casegen.ai.message.domain.AiMessage;

record PreparedUserMessage(AiMessage userMessage, AiMessage assistantMessage, boolean created) {
}
