package dev.infrai.logistics;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ShipmentFailureService {
    public interface ErrorReporter {
        void capture(Map<String, Object> exceptionPayload);
    }

    private final ErrorReporter reporter;
    private final String environment;

    public ShipmentFailureService(ErrorReporter reporter, String environment) {
        this.reporter = reporter;
        this.environment = environment;
    }

    public FailureDecision handle(ShipmentEvent event) {
        if (!"DELIVERED".equals(event.status())) {
            return new FailureDecision(event.shipmentId(), "CONTINUE_AGENT_LOOP", false);
        }
        ProofOfDelivery pod = event.proofOfDelivery();
        if (pod != null && pod.isAcceptable()) {
            return new FailureDecision(event.shipmentId(), "COMPLETE_DELIVERY", false);
        }

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("shipment_id", event.shipmentId());
        context.put("event_id", event.eventId());
        context.put("agent_step", "verify-proof-of-delivery");
        context.put("pod_object_key", pod == null ? "absent" : pod.objectKey());
        context.put("pod_sha256", pod == null ? "absent" : pod.sha256());

        // Keep document bytes and recipient details out of observability payloads.
        reporter.capture(Map.of(
                "title", "Proof of delivery validation failed",
                "message", "A delivered shipment requires a verified proof file",
                "exception", "ProofOfDeliveryException: missing or unverified proof",
                "level", "error",
                "tags", Map.of("workflow", "logistics-agent", "control", "pod-required"),
                "fingerprint", List.of("logistics-agent", "verify-proof-of-delivery"),
                "context", context,
                "environment", environment,
                "service", "shipment-agent",
                "idempotency_key", "shipment-event:" + event.eventId()));
        return new FailureDecision(event.shipmentId(), "HOLD_FOR_REVIEW", true);
    }
}
