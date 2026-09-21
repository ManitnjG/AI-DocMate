# Deploy the API

Deploy this directory as a Docker service behind HTTPS, or install requirements and run `uvicorn main:app --host 0.0.0.0 --port 8000 --workers 1`.
Set `OPENROUTER_API_KEY`, optional `OPENROUTER_MODEL`, and `DOCMATE_ACCESS_TOKENS` in the host's secret environment configuration. `.env.example` is a reference; the app does not automatically load a .env file.
Generate each user's distinct token with `python -c "import secrets; print(secrets.token_urlsafe(32))"`. Configure comma-separated tokens on the server; give each user only their own token. Rotate by replacing the token and restarting the service.

In Android Settings, save your HTTPS server URL and user token. The provider API key is never sent to Android. Health reports configuration presence, not a successful AI-provider call. No deployed backend is included in this repository.

The server does not persist document content. Configure your hosting logs to avoid request bodies. Your AI provider has its own retention policy; disclose it before public launch.

Requests are limited to 2 MB JSON, 1 million extracted characters, and 10 requests per token per minute. The limiter is in memory: use one worker for this pilot. Before scaling, replace it with a shared rate limiter, real user identity and per-user billing quotas. Put request timeouts/body limits at the reverse proxy too.

Questions retrieve matching passages across all submitted pages. This is lexical retrieval, not semantic search. Responses include source excerpts; citation page validity is checked, but factual entailment still requires user review. A provider failure returns explicitly labelled excerpts. Summaries exceeding 45,000 excerpt characters are rejected with a clear message rather than silently truncated. Free provider capacity is not guaranteed.

Tests: `python -m unittest discover -s backend -v` from repository root.
