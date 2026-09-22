# AI DocMate 0.8.0 release readiness

## Implemented against the reported cons

| Concern | Change | Verification |
|---|---|---|
| Unsaved editor work | Atomic source + editing-history draft; reopen/rotation recovery | Model, disk and UI navigation tests |
| Missing annotations/signatures | Text, ink/signature, highlight, rectangle, selection, styling, delete, undo/redo | Model round-trip and rendered PDF tests |
| Existing text editing | OCR boxes; anchored replacement mask; adjustable style; compatible embedded-font reuse | OCR and PDF export tests |
| Missing conversation history | Private SQLite job/answer history, bounded follow-up context | Database and backend tests |
| Background processing | WorkManager AI/import jobs, network constraints, cancellation and transient retries | Import-worker idempotence and persistence tests |
| AI requires internet / slow responses | Offline source result while queued, parallel configured providers, 28-second backend deadline | Provider cancellation and deadline tests |
| Untested Indian OCR packs | Verified download hashes; synthetic script-coverage tests for all 13 supported Indian-language packs | Android multilingual test; real-photo quality is separate |
| Production signing | Owner-key APK/AAB workflow, signature verification, no debug-key substitution | Workflow prepared; actual credentials and execution required |

A passing automated test is not a claim of universal font reconstruction, handwriting accuracy or readiness for every phone.

## Owner-controlled production signing

In this repository’s Actions secrets, configure:

- `DOCMATE_KEYSTORE_BASE64`: base64 contents of the owner’s existing upload/release keystore.
- `DOCMATE_STORE_PASSWORD`
- `DOCMATE_KEY_ALIAS`
- `DOCMATE_KEY_PASSWORD`

Retain a secure backup of the keystore and passwords. Do not put them in source files, issues or chat messages. Run **Signed production candidate** for the reviewed commit. It runs the shared backend/unit/lint/emulator gate, builds APK and AAB, verifies the APK signature and uploads artifacts. It does not publish to Google Play automatically.

## Verification that cannot be replaced by an emulator

The production owner must test camera capture/permissions, background behaviour, OCR on real documents and exported-file opening on representative physical phones. Store publication also requires the owner’s developer account and accurate privacy/store information.

## Inherent editing limits

A scan is pixels, so its exact original font cannot always be identified. PDF embedded-font reuse is attempted only for loadable fonts with usable glyphs. The user can adjust replacement styling and placement. Patterned backgrounds need review because the replacement mask uses an estimated background colour. Edited pages are rasterized at a 2600-pixel maximum edge; they are no longer selectable-text pages until OCR is applied again. Export creates a new document and leaves the original intact.
