package dev.infrai.logistics;

import java.util.Map;

public record FailureDecision(String shipmentId, String action, boolean errorCaptured) {
    public Map<String, Object> asMap() {
        return Map.of(
                "shipmentId", shipmentId,
                "action", action,
                "errorCaptured", errorCaptured);
    }
}
