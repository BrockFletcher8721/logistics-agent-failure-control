package dev.infrai.logistics;

import java.util.Map;
import java.util.Objects;

public record ShipmentEvent(
        String shipmentId,
        String eventId,
        String status,
        ProofOfDelivery proofOfDelivery) {

    public ShipmentEvent {
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(status, "status");
    }

    @SuppressWarnings("unchecked")
    public static ShipmentEvent from(Map<String, Object> input) {
        Object podValue = input.get("proofOfDelivery");
        ProofOfDelivery pod = podValue instanceof Map<?, ?> map
                ? ProofOfDelivery.from((Map<String, Object>) map)
                : null;
        return new ShipmentEvent(
                required(input, "shipmentId"),
                required(input, "eventId"),
                required(input, "status"),
                pod);
    }

    private static String required(Map<String, Object> input, String name) {
        Object value = input.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(name + " must be a non-empty string");
        }
        return text;
    }
}
