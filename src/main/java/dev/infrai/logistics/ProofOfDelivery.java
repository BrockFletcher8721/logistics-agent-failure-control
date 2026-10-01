package dev.infrai.logistics;

import java.util.Map;

public record ProofOfDelivery(String objectKey, String sha256, boolean verified) {
    public static ProofOfDelivery from(Map<String, Object> input) {
        return new ProofOfDelivery(
                text(input, "objectKey"),
                text(input, "sha256"),
                Boolean.TRUE.equals(input.get("verified")));
    }

    public boolean isAcceptable() {
        return !objectKey.isBlank() && sha256.matches("[a-fA-F0-9]{64}") && verified;
    }

    private static String text(Map<String, Object> input, String name) {
        Object value = input.get(name);
        return value instanceof String text ? text : "";
    }
}
