package dev.infrai.logistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ShipmentFailureServiceTest {
    public static void main(String[] args) {
        List<Map<String, Object>> captures = new ArrayList<>();
        ShipmentFailureService service = new ShipmentFailureService(captures::add, "test");

        ShipmentEvent event = new ShipmentEvent("SHP-1042", "EVT-9", "DELIVERED", null);
        FailureDecision decision = service.handle(event);

        assertEquals("HOLD_FOR_REVIEW", decision.action(), "missing POD must stop completion");
        assertEquals(true, decision.errorCaptured(), "failure must be observable");
        assertEquals(1, captures.size(), "one event produces one capture request");
        assertEquals("shipment-event:EVT-9", captures.get(0).get("idempotency_key"),
                "capture retry must retain a stable idempotency key");
        Map<?, ?> context = (Map<?, ?>) captures.get(0).get("context");
        assertEquals("SHP-1042", context.get("shipment_id"), "capture identifies the shipment");
        System.out.println("PASS missing POD -> HOLD_FOR_REVIEW and one idempotent capture");
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
