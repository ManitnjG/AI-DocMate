# AI DocMate — Android document assistant

Version 0.8.0 is a pilot with real local document processing and an optional authenticated AI API.

## Implemented
- Import selectable-text PDFs and JPG/PNG images (25 MB; 100 PDF pages).
- On-device Latin-script OCR for images and PDF pages without selectable text.
- Private device document history, name search, rename and delete; latest result saved per document.
- Page-labelled source text; offline date, amount and phone extraction.
- System dark mode, scrollable compact action rows, and full cited-page text inspection.
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

## Editor, background processing and conversations
- Smart Editor stores its source, page order, annotations, selection and undo/redo in an atomic private draft. Reopen it after leaving the app to continue.
- Add and adjust text, draw signatures/freehand strokes, highlights and rectangles, select annotations, and undo/redo. OCR exposes text regions for replacement.
- Where a selectable PDF embeds a usable TrueType/OpenType font and the required glyphs exist, the editor attempts to reuse that font. Otherwise it offers adjustable family, size, bold, italic and colour. Scans contain no embedded font to recover.
- Edited pages are flattened on PDF export; unedited pages retain their source contents. Replacement masks stay anchored if the new text is moved. Flattened edited pages need OCR again to be searchable.
- WorkManager persists document imports and AI requests. Network-dependent jobs wait for connectivity, and transient AI connection failures retry with backoff. Conversation results remain on the device; pending requests can be cancelled.
- An offline source-based result appears immediately while a cloud request waits. Recent completed AI turns accompany follow-up questions, and the backend still answers from document evidence.
- Configured AI providers race to return a valid cited answer; other provider requests are cancelled. The total backend deadline is 28 seconds, after which explicit source excerpts are returned. Hosting/network delays can add time.
- The signed production-candidate workflow uses owner-managed GitHub signing secrets; it never substitutes a debug key. See [release readiness](RELEASE_READINESS.md).

## Known limits / remaining release work
- Tamil and other Indian-language OCR is available in both Document Scanner and AI document import through downloadable Tesseract packs. Choose the OCR language and download its packs before importing scanned files. Recognition quality still requires review.
- The Document Scanner provides crop/perspective correction, multi-page capture and OCR. The older basic camera button remains a simple photo-to-PDF path.
- Summaries have an explicit size limit; Q&A uses lexical retrieval across the whole accepted document.
- Semantic retrieval remains future work. Conversation history, background AI/OCR import, Unicode DOCX export, arbitrary page-range PDF export, offline multi-document comparison and text/CSV exports are implemented.
- OCR font/background matching is approximate for scans, unembedded fonts, missing glyphs and complex backgrounds. Inspect replacements before sharing. Hand-drawn signatures are visual marks, not certificate-backed digital signatures.
- Drafts remain app-private on this device; uninstalling the app or clearing its data removes them. A cancelled in-flight operation may need retrying; saved drafts are retained.
- Merge starts from an empty workspace; reopen Smart Editor to combine another set. Physical-device verification of camera, preview and exports remains required.
- AI and document import jobs survive activity recreation through WorkManager. Android may defer background work; force-stop pauses it until the app is opened. Scanner/editor export is lifecycle-managed, but an interrupted export must be retried from the saved draft.
- Saved documents use app-private storage, not additional database encryption. Backups are disabled. Access tokens use Keystore encryption.
- Subscriptions, payment verification, cloud sync and crash reporting are not implemented. Production signing is configured but needs the owner’s signing secrets and release execution.
- Before public launch: real user account provisioning, shared quotas, privacy policy covering the chosen hosting/AI provider, signed release configuration and physical-device testing.

Do not interpret a passing build as verification of OCR quality, cloud availability or AI accuracy.
