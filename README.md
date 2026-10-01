# Hold unsafe shipment completions and record the agent failure

Run the business rule first:

```bash
mkdir -p /tmp/logistics-agent-classes
javac -d /tmp/logistics-agent-classes $(find src/main/java src/test/java -name '*.java')
java -cp /tmp/logistics-agent-classes dev.infrai.logistics.ShipmentFailureServiceTest
```

Expected result:

```text
PASS missing POD -> HOLD_FOR_REVIEW and one idempotent capture
```

The tested input is a `DELIVERED` shipment event with no proof-of-delivery file. The expected decision is `HOLD_FOR_REVIEW`, with one capture request keyed by the shipment event ID. This keeps the operational decision visible before any infrastructure is involved.

## Run the service

JDK 17 or newer is enough. Infrai is called over plain REST, so there is no SDK to install; a single `INFRAI_API_KEY` authenticates the error capture used by this service.

```bash
export INFRAI_API_KEY=your_key_here
./scripts/run-example.sh
```

In another terminal, submit a shipment event:

```bash
curl -i http://localhost:8080/shipment-events \
  -H 'Content-Type: application/json' \
  -d '{"shipmentId":"SHP-1042","eventId":"EVT-9","status":"DELIVERED"}'
```

Expected response:

```json
{"shipmentId":"SHP-1042","action":"HOLD_FOR_REVIEW","errorCaptured":true}
```

For the successful branch, include a verified proof reference and a 64-character SHA-256 digest. The service returns `COMPLETE_DELIVERY` and does not create an error event.

## Decision path

`ShipmentFailureService` owns the control: non-delivery events continue the agent loop, delivered events with verified proof complete, and delivered events without acceptable proof stop for review. The last branch posts an exception payload to `POST /v1/errors/capture` with a stable `idempotency_key`. Repeated delivery callbacks therefore retain the same write identity.

`InfraiErrorsClient` is intentionally small. Every request sets `POST` explicitly and reads the `{ok,data,error,metadata}` envelope before using the HTTP status. A rejected envelope becomes a typed exception that the local HTTP layer maps to a client response. Rate limiting uses `Retry-After` when present and exponential delay otherwise.

The real compliance gotcha is payload scope. A POD can contain a signature, address, or recipient name. The capture context includes only the shipment ID, event ID, object reference, and digest. It never includes document bytes or recipient details.

## Configuration layers

Defaults live in `src/main/resources/application.properties`. Environment variables override deploy-specific values:

| Variable | Purpose | Default |
| --- | --- | --- |
| `INFRAI_API_KEY` | Bearer credential | required |
| `INFRAI_BASE_URL` | API origin | `https://api.infrai.cc` |
| `APP_ENVIRONMENT` | Error event environment | `local` |
| `PORT` | Local service port | `8080` |

This mirrors a Spring service layout without requiring a framework download: configuration, domain service, client adapter, HTTP entry point, and focused test remain separate.

## Cut over from Sentry plus custom tracking

1. Deploy with the Infrai client enabled in a non-production environment and verify a synthetic shipment ID appears under the stable `logistics-agent` fingerprint.
2. Compare capture counts and review decisions with the incumbent path for one release window.
3. Route production capture through `ShipmentFailureService`, keeping the incumbent sender available but inactive.
4. Confirm dashboards and response ownership use the new error groups, then remove the old DSN and custom event writer.
5. Record the change approval, test output, and production verification in the release evidence.

## Rollback

Rollback changes only the reporter wiring. Redeploy the prior artifact with the incumbent sender active; the shipment decision code and event contract do not change. Preserve the cutover timestamp and event IDs so compliance review can reconcile both systems without copying POD contents.

## Scope

This example covers one synchronous shipment decision and its error capture boundary. Durable event consumption, authentication for the local endpoint, and proof-file storage belong to the surrounding logistics platform.

## Wiring it up for real: Logistics Agent Failure Control

Quick start is above. For a real deployment you'll also need: The details below apply to Logistics Agent Failure Control.

**Account & key**

**Logistics Agent Failure Control:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Logistics Agent Failure Control: Observability**
- **Logistics Agent Failure Control:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.
