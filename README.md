# AI DocMate — Android document assistant

Version 0.7.0 is a pilot with real local document processing and an optional authenticated AI API.

## Implemented
- Import selectable-text PDFs and JPG/PNG images (25 MB; 100 PDF pages).
- On-device Latin-script OCR for images and PDF pages without selectable text.
- Private device document history, name search, rename and delete; latest result saved per document.
- Page-labelled source text; offline date, amount and phone extraction.
- Cloud questions and summaries with source excerpts, English/Tamil answer selection, and confirmation before sending extracted text.
- HTTPS-only service connection; automatically issued session encrypted with Android Keystore; provider key stays on the server.
- Customer-facing About/privacy screen replaces developer connection settings.
- Copy, Android text sharing, and TXT export.
- Validated API requests, per-token pilot rate limits, provider retry and explicit extractive fallback.
- CI backend tests, Android unit tests, debug build and Android lint. Android artifacts are gated on backend success and scanner emulator tests.
- Office archives have actual decompressed-byte limits, entry limits and slide limits to reduce memory-exhaustion risk.
- Separate offline Smart Editor workspace: PDF/photo preview, pinch zoom, pan, rotate, reorder, duplicate, delete, insert blank page, undo/redo, and export a separate PDF.
- Export the current page to a separate PDF. Merge selected PDFs/photos into a new workspace (up to 20 inputs, 100 pages, 100 MB combined).
- Camera capture through the system camera app, with private temporary files and permission handling; captured photos can be exported as PDF.

## Document scanner

See [Scanner features and validation](SCANNER.md) for auto-capture, perspective correction, enhancements, multi-page drafts, downloadable Indian-language OCR packs, searchable PDFs, export-quality settings and quality warnings.

## Setup
The owner activates the service once using [backend setup](backend/README.md). Customer builds get the public HTTPS service address from the GitHub Actions variable `DOCMATE_API_URL` and obtain temporary sessions automatically. Customers never enter a URL, access token or API key. Until owner activation, cloud buttons are disabled and clearly labelled; offline source search, extractive overview, import, extraction, history and export remain available.

## Build
Java 17, Android SDK 35 and Gradle 8.9: `gradle testDebugUnitTest assembleDebug lintDebug`. GitHub Actions uploads `AI-DocMate-debug-apk` after successful checks. The repository does not yet include a Gradle wrapper.

## Known limits / remaining release work
- Tamil and other Indian-language OCR is available in both Document Scanner and AI document import through downloadable Tesseract packs. Choose the OCR language and download its packs before importing scanned files. Recognition quality still requires review.
- The Document Scanner provides crop/perspective correction, multi-page capture and OCR. The older basic camera button remains a simple photo-to-PDF path.
- Summaries have an explicit size limit; Q&A uses lexical retrieval across the whole accepted document.
- Conversation threads, semantic retrieval and background processing remain future work. Genuine Unicode DOCX result export and arbitrary page-range PDF export are implemented. Offline multi-document comparison and text/CSV exports are implemented.
- Smart Editor does not yet replace existing text, perform Indic OCR, add annotations/signatures, or provide durable editing drafts after explicitly leaving the editor. Page edits, selection, undo/redo history and pending camera paths are restored across activity recreation while the cached source remains available. Export before leaving the workspace. PDF export is not a secure-redaction workflow.
- Merge starts from an empty workspace; reopen Smart Editor to combine another set. Physical-device verification of camera, preview and exports remains required.
- Keep the app open while processing. Latest results are saved, but an in-flight request is not restored after activity recreation.
- Saved documents use app-private storage, not additional database encryption. Backups are disabled. Access tokens use Keystore encryption.
- No subscriptions, payment verification, cloud sync, signed store release or crash reporting yet.
- Before public launch: real user account provisioning, shared quotas, privacy policy covering the chosen hosting/AI provider, signed release configuration and physical-device testing.

Do not interpret a passing build as verification of OCR quality, cloud availability or AI accuracy.
