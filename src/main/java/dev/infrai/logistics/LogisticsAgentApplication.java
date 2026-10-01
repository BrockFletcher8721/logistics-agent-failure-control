package dev.infrai.logistics;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class LogisticsAgentApplication {
    private LogisticsAgentApplication() {}

    public static void main(String[] args) throws IOException {
        ServiceConfig config = ServiceConfig.load();
        ShipmentFailureService service = new ShipmentFailureService(
                new InfraiErrorsClient(config), config.environment());
        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/shipment-events", exchange -> handle(exchange, service));
        server.start();
        System.out.println("Logistics failure service listening on http://localhost:" + config.port());
    }

    @SuppressWarnings("unchecked")
    private static void handle(HttpExchange exchange, ShipmentFailureService service) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }
        try {
            Object decoded = Json.parse(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (!(decoded instanceof Map<?, ?> map)) throw new IllegalArgumentException("body must be an object");
            FailureDecision decision = service.handle(ShipmentEvent.from((Map<String, Object>) map));
            respond(exchange, 200, decision.asMap());
        } catch (IllegalArgumentException exception) {
            respond(exchange, 400, Map.of("error", exception.getMessage()));
        } catch (InfraiErrorsClient.InfraiApiException exception) {
            int status = exception.statusCode() >= 400 && exception.statusCode() < 500
                    ? exception.statusCode() : 502;
            respond(exchange, status, Map.of("error", exception.code(), "message", exception.getMessage()));
        } catch (InfraiErrorsClient.InfraiTransportException exception) {
            respond(exchange, 502, Map.of("error", "observability_transport", "message", exception.getMessage()));
        }
    }

    private static void respond(HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
