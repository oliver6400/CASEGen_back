package com.casegen.collaboration;

import java.util.UUID;

public record PresenceCommand(UUID selectedElementId, PresenceActivity activity) {
}
