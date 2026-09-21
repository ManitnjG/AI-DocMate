# AI DocMate — Android document assistant

Version 0.3.0 is a pilot with real local document processing and an optional authenticated AI API.

## Implemented
- Import selectable-text PDFs and JPG/PNG images (25 MB; 100 PDF pages).
- On-device Latin-script OCR for images and PDF pages without selectable text.
- Private device document history, name search, rename and delete; latest result saved per document.
- Page-labelled source text; offline date, amount and phone extraction.
- Cloud questions and summaries with source excerpts, English/Tamil answer selection, and confirmation before sending extracted text.
- HTTPS-only backend connection; user access token encrypted with Android Keystore; provider key stays on the server.
- Copy, Android text sharing, and TXT export.
- Validated API requests, per-token pilot rate limits, provider retry and explicit extractive fallback.
- CI backend tests, Android debug build and Android lint.

## Setup
See [backend setup](backend/README.md). Deploy and configure the backend, then enter its HTTPS URL and your access token in Android Settings once. No backend URL or provider secret is bundled. Import, extraction, history and export work without a backend.

## Build
Java 17, Android SDK 35 and Gradle 8.9: `gradle assembleDebug lintDebug`. GitHub Actions uploads `AI-DocMate-debug-apk` after successful checks. The repository does not yet include a Gradle wrapper.

## Known limits / remaining release work
- Tamil scanned-image OCR is not implemented. Selectable Tamil text can be sent to the AI; verify font encoding extraction on your files.
- OCR does not yet deskew/rotate camera images; no built-in camera capture.
- Summaries have an explicit size limit; Q&A uses lexical retrieval across the whole accepted document.
- Conversation threads, multi-document comparison, DOCX/PDF/CSV exports, semantic retrieval, background/cancellable jobs and page-range selection remain future work.
- Keep the app open while processing. Latest results are saved, but an in-flight request is not restored after activity recreation.
- Saved documents use app-private storage, not additional database encryption. Backups are disabled. Access tokens use Keystore encryption.
- No subscriptions, payment verification, cloud sync, signed store release or crash reporting yet.
- Before public launch: real user account provisioning, shared quotas, privacy policy covering the chosen hosting/AI provider, signed release configuration and physical-device testing.

Do not interpret a passing build as verification of OCR quality, cloud availability or AI accuracy.
