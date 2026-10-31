# GuidingHand backend deployment

The Android app never contains a Meta provider key. Deploy `backend/` behind a
managed HTTPS endpoint and configure all secrets in the hosting platform.

## Required environment

- `MODEL_API_KEY`: Meta Model API key.
- `GUIDINGHAND_APP_SESSION_TOKEN`: random app-to-backend bearer token, at least 32 characters.
- `GUIDINGHAND_PROJECT_BUDGET_USD`: project ceiling, no more than `150`.
- `GUIDINGHAND_STATE_DIR`: mounted persistent directory for the usage ledger.
- `PORT`: injected by most container platforms; defaults to `8080`.

Do not commit `.env`, provider keys, session tokens, TLS private keys, or the
state directory. Do not reset the state directory during a deployment.

## Container contract

Build from the repository root:

```text
docker build -t guidinghand-backend backend
```

The platform must terminate TLS and forward `X-Forwarded-Proto: https`.
Expose only the managed HTTPS hostname. Keep the container port private.

- `GET /healthz` is an unauthenticated liveness check. It does not call Meta or
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
