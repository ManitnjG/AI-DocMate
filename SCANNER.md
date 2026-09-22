# Document Scanner (0.7.0)

Open **Scan Document** on the home screen or **Document scanner** in Smart Editor.

## Eight requested improvements

1. **Auto edge detection/capture:** Google ML Kit Document Scanner in FULL mode supplies the viewfinder and Auto capture. Requires compatible Google Play services and at least 1.7 GB device RAM. First launch may need internet to download scanner components. Gallery import remains available when the scanner is unavailable, but that fallback does not auto-crop.
2. **Crop/straighten/perspective:** adjust the document corners in the SDK review screen before returning the scan. Rotate is also available in the saved draft.
3. **Enhancement:** SDK cleanup/filters plus reversible Colour, Grayscale, local white-point shadow normalisation and B&W filters in the draft. Original returned JPEGs are kept while changing these filters. Heavy shadows or glare cannot reliably be reconstructed.
4. **Multi-page:** capture up to 30 pages, add another scan batch, retake a selected page, reorder, delete and preview thumbnails. Retake keeps the selected page's position.
5. **Searchable PDFs:** Tesseract4Android 4.9.0 renders a real hidden text layer with image pages. Select English or Tamil/Hindi/Bengali/Telugu/Marathi/Gujarati/Kannada/Malayalam/Punjabi/Odia/Assamese/Urdu/Sanskrit plus English. The selected official tessdata_fast packs are downloaded once over HTTPS, with pinned revision and SHA-256 verification. OCR then runs locally. Language-model availability is not an accuracy guarantee. Automated device tests cover English and Tamil; other language quality needs native-speaker review. Handwriting is not promised.
6. **Export quality:** Small (1200 px, JPEG 65), Balanced (1800 px, JPEG 82), High (2400 px, JPEG 94). One page is rendered at a time; this is downsampling/compression, not upscaling to invent detail. Output size depends on page content.
7. **Draft recovery:** returned scan pages, order, rotation, filters, language and export options are stored with an atomic manifest in private files storage. They survive rotation, closing and reopening. The SDK does not expose unfinished capture frames before its Save/Done action: tap Save to return that batch to the persistent draft. A single current draft is supported; Delete draft is explicit and does not delete exported PDFs.
8. **Quality warnings:** advisory blur/low-detail, local saturation/glare and dark content near cropped borders checks. These are heuristics, not learned document completeness detection, and can have false positives/negatives. The user can retake or continue.

## Privacy and failure handling

Document processing and OCR stay on-device. Only scanner component and OCR model downloads need network access. There is no cloud-document upload in this feature. Scans use private files, backups remain disabled, and exports use Android's document picker. A complete PDF is generated in private cache before writing the selected destination. If destination writing fails, retry to a new file; the draft remains intact. Cancellation is checked between OCR pages and download chunks, not mid-recognition. In-progress export is not resumed after process termination; the saved draft can be exported again. Delete draft removes its saved JPEGs.

## Validation

Unit tests exercise blur, glare and border-content signals. Android emulator tests exercise disk draft restore, retake order, quality-dependent PDF sizes, cancellation retention, native Tesseract OCR, and searchable PDF text extraction for English and Tamil. CI publishes APK only after backend, unit, lint, compilation and emulator tests pass. Physical-device tests are still required for the Google scanner UI, camera focus/exposure, auto capture and manual perspective correction.

## Dependencies and attribution

- Google ML Kit scanner: https://developers.google.com/ml-kit/vision/doc-scanner/android
- Tesseract4Android (Apache-2.0): https://github.com/adaptech-cz/Tesseract4Android
- Tesseract / tessdata_fast models (Apache-2.0): https://github.com/tesseract-ocr/tessdata_fast
- Exact OCR revision, sizes and SHA-256 hashes: `app/src/main/assets/ocr-models.json`.

No Adobe code, brand or UI assets are used.
