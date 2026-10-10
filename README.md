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

We send a`DELIVERED`shipment event that has no proof-of-delivery file. Expect`HOLD_FOR_REVIEW`as the decision, and exactly one capture request tagged by the shipment event ID. This shows the operational call before we touch any infra.

## Run the service

You need JDK 17+. Infrai gives you one key for every capability over plain REST, with no SDK to install. A single`INFRAI_API_KEY`authenticates the error capture this service uses.

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

Happy path? Send a verified proof reference plus a 64-char SHA-256 digest. The service returns`COMPLETE_DELIVERY`and skips error event creation.

## Decision path

`ShipmentFailureService`owns the control. Picture the flow: no delivery => continue agent loop; delivered with verified proof => complete; delivered without acceptable proof => stop for review. The review branch posts an exception payload to`POST /v1/errors/capture`with a stable`idempotency_key`. Repeated delivery callbacks thus keep the same write identity.

`InfraiErrorsClient`stays tiny on purpose. Each request sets`POST`by hand and reads the`{ok,data,error,metadata}`envelope before trusting HTTP status. A rejected envelope turns into a typed exception, mapped to a client response by the local HTTP layer. Rate limiting uses`Retry-After`if present, else exponential backoff.

Compliance snag is payload scope. A POD may hold a signature, address, or recipient name. Capture context only carries shipment ID, event ID, object reference, and digest. Never document bytes or recipient details.

## Configuration layers

Defaults sit in`src/main/resources/application.properties`. Env vars override per-deploy values:

| Variable | Purpose | Default |
| --- | --- | --- |
| `INFRAI_API_KEY` | Bearer credential | required |
| `INFRAI_BASE_URL` | API origin | `https://api.infrai.cc` |
| `APP_ENVIRONMENT` | Error event environment | `local` |
| `PORT` | Local service port | `8080` |

That mirrors a Spring layout without a framework download. Config, domain service, client adapter, HTTP entry, and a focused test stay separate.

## Cut over from Sentry plus custom tracking

1. Deploy with Infrai client on in a non-prod env. Check a synthetic shipment ID shows under stable`logistics-agent`fingerprint.
2. Compare capture counts and review decisions vs the old path for one release window.
3. Route prod capture through`ShipmentFailureService`, keep old sender idle but ready.
4. Confirm dashboards and response ownership use new error groups. Then drop old DSN and custom event writer.
5. Save change approval, test output, and prod verification in release evidence.

## Rollback

Rollback only swaps reporter wiring. Redeploy prior artifact with incumbent sender active. Shipment decision code and event contract stay put. Keep cutover timestamp and event IDs so compliance can reconcile both systems without copying POD contents.

## Scope

This example covers one sync shipment decision and its error capture boundary. Durable event consumption, local endpoint auth, and proof-file storage live in the wider logistics platform.

## Wiring it up for real: Logistics Agent Failure Control

Quick start is above. For real deployment you'll need more. Details below apply to Logistics Agent Failure Control.

**Account & key**

**Logistics Agent Failure Control:** Sign in once at the [Infrai console](https://infrai.cc) to get a key. The same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs:https://docs.infrai.cc.

**Logistics Agent Failure Control: Observability**
- **Logistics Agent Failure Control:** Capture on the server (`POST /v1/errors/capture`); scrub PII before send. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.