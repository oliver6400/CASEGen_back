package com.casegen.ai.session.service;

import com.casegen.ai.session.domain.AiSession;

public record StartOrReuseSessionResult(AiSession session, boolean created) {
}
