# GuidingHand backend deployment

The Android app never contains a Meta provider key. Deploy `backend/` behind a
managed HTTPS endpoint and configure all secrets in the hosting platform.

## State storage

Cloud Run instances are replaceable and their writable filesystem is not a
durable volume. For the production service, keep the conservative budget
ledger in the Firestore database you created (`guidinghand`) instead of relying
on a local `usage.json` file:

```text
GUIDINGHAND_STATE_BACKEND=firestore
GUIDINGHAND_FIRESTORE_DATABASE=guidinghand
GUIDINGHAND_FIRESTORE_COLLECTION=guidinghand
GUIDINGHAND_FIRESTORE_DOCUMENT=budget
```

The service account needs Firestore read/write access to that database. The
document is created on the first request; no collection or seed data is needed.
The backend uses the server SDK, so Firestore security rules are not the
authorization boundary here; grant the Cloud Run service account only the
minimum Firestore IAM role it needs.
It contains only spend accounting (`spent_usd`, pending reservations, request
count, and a timestamp). Prompts, screenshots, transcripts, and API keys are
never stored there. Local development keeps the file ledger with
`GUIDINGHAND_STATE_BACKEND=file`.

## Required environment

- `MODEL_API_KEY`: Meta Model API key.
- `GUIDINGHAND_APP_SESSION_TOKEN`: random app-to-backend bearer token, at least 32 characters.
- `GUIDINGHAND_PROJECT_BUDGET_USD`: project ceiling, no more than `150`.
- `GUIDINGHAND_STATE_DIR`: local/fallback directory for the usage ledger. It is
  not a durability guarantee on Cloud Run; use the Firestore settings above in
  production.
- `GUIDINGHAND_TRUST_PROXY_HEADERS`: set to `true` only when a trusted TLS proxy
  overwrites forwarded headers and the container port is not publicly reachable.
- `PORT`: injected by most container platforms; defaults to `8080`.

Do not commit `.env`, provider keys, session tokens, TLS private keys, or the
state directory. Do not reset the state directory during a deployment.

For project `guidinghand-513616`, a later deployment can set the project and
region explicitly, then inject `MODEL_API_KEY` and
`GUIDINGHAND_APP_SESSION_TOKEN` from Secret Manager. Do not pass either value
on the command line or commit them to a `.env` file.

## Container contract

Build from the repository root:

```text
docker build -t guidinghand-backend backend
```

For managed TLS termination, keep the container port private, ensure the proxy
overwrites `X-Forwarded-Proto`, and set `GUIDINGHAND_TRUST_PROXY_HEADERS=true`.
Without that explicit setting, a client-supplied forwarded header is ignored.
Expose only the managed HTTPS hostname.

- `GET /health` is an unauthenticated liveness check. It does not call Meta or
  reveal model, budget, or configuration details.
- `GET /core/capabilities` requires the app bearer token and confirms the
  protocol expected by Android. It does not spend model credit.
- `/core/step` and `/core/transcribe` require HTTPS and bearer authentication.

Mount a persistent volume at `GUIDINGHAND_STATE_DIR`. The budget ledger uses
conservative reservations so an uncertain provider response cannot be charged
twice silently.

After deployment, enter the HTTPS base URL and session token in GuidingHand
Settings. The home screen reports Ready only after the authenticated capability
probe succeeds. A healthy container does not prove Spark, SAM, or Transcribe
quality; complete the real-phone protocol separately.
