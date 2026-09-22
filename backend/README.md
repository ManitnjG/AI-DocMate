# Integrated AI — owner setup (customers do not configure anything)

1. Deploy this `backend` directory as a Docker service behind HTTPS. Set `OPENROUTER_API_KEY` as a hosting secret and optionally set `OPENROUTER_MODEL`. No key belongs in Git or the APK.
2. Generate a signing secret: `python -c "import secrets; print(secrets.token_urlsafe(48))"`. Set it as `DOCMATE_SESSION_SECRET`; set `DOCMATE_PUBLIC_SESSIONS=true` to enable automatic pilot sessions. No manually distributed customer tokens are needed.
3. Set `DOCMATE_DAILY_REQUESTS=100` (or a deliberate budget). Persist `DOCMATE_QUOTA_DB` on a writable mounted disk for restart-safe quotas. The default /tmp database survives process restarts only while the same filesystem exists; a host replacement can reset it. Set a spending cap at the AI provider too.
4. Set GitHub repository Actions variable `DOCMATE_API_URL` to this service's actual HTTPS URL. Run the Android workflow again. This public service address is compiled into the app; secrets are not.
5. Verify `/health`, session issuance, and a real `/ask` response with a harmless test document before distributing the APK. Configuration flags alone do not prove the provider works.

Until this setup is complete, builds explicitly show cloud AI as awaiting activation. Offline tools remain available. They are source extraction, not a local generative model.

## Session and abuse controls
`POST /session` issues a 24-hour HMAC-signed random session. Android keeps it encrypted using Keystore, reuses it, and renews it on expiry or 401. Issuance is opt-in, requires a server provider key, and is capped at 5 per client per hour and 1,000 globally per day. Ask calls reserve quotas before contacting the provider: 5 per session per minute, 20 per client per day and the configured global daily cap. Provider retries can mean two provider requests per reserved call. This is a limited anonymous pilot, not proof that a request comes from the real app; add verified accounts/app attestation before a large public launch.

Client addresses are HMAC-hashed. Configure Uvicorn/proxy trusted forwarded IPs for the actual hosting ingress only; never trust arbitrary forwarded headers. With no trusted proxy forwarding, users may share an ingress quota. Use one service instance and a persistent SQLite disk for these limits; scaling requires a shared quota service. Session keys remain server-only and rotating them invalidates existing sessions.

Legacy `DOCMATE_ACCESS_TOKENS` remain supported for older APKs. New customer builds never ask for them. Do not embed a common access token in Android.

The server does not intentionally log or save document bodies. Your host and OpenRouter/provider have independent retention terms. Review and disclose them. Use HTTPS, avoid body logging and cap request time/size at ingress.

Questions use lexical passage retrieval across the accepted document; source references are validated by page number, not factual entailment. Large summaries are explicitly rejected. Free provider capacity is not guaranteed.

Tests from repository root: `python -m unittest discover -s backend -v`.
