# Panel View Module

This module owns panel-aware reading for Kotatsu, covering panel detection, reading order, and navigation. The core goal is *modularity*: each detector is isolated, settings describe behavior, and UI hooks depend only on stable state and ordering classes.

## Directory Map

- app/src/main/kotlin/org/kotatsu/panelview/ : public API surface for the reader
  - PanelDetector.kt : orchestrates panel detection pipelines and inline refinement
  - detection/ : pluggable detectors (SimpleGutterDetector, OpenCVPanelDetector, DeepPanelDetector, HuggingFaceDetector)
  - settings/ : PanelViewSettings DTOs and enums describing scan type, frame options, reading order
  - PanelOrder.kt : sorting logic per reading order
  - PanelReaderController.kt : navigation state machine with paging callbacks
  - PanelReaderState.kt : immutable state backing the controller
  - PanelReaderScreen.kt : placeholder for future Compose UI; keep non-Compose flow decoupled

Keep panel-reader specific assets, configs, and docs in this tree so the feature can move into its own Gradle module later.

## Detection Workflow

`PanelDetector.detectPanels(bitmap, settings)` is the single entry point. It is marked `suspend` so heavy detectors can hop to background dispatchers.

1. Skip detection when `settings.detection.enabled` is false and return the full page.
2. Choose the base pipeline from `settings.scanType`:
   - `REGULAR` -> `runRegularPipeline`: `OpenCVPanelDetector` -> `SimpleGutterDetector` -> `DeepPanelDetector`.
   - `IRREGULAR` -> `runIrregularPipeline`: prefer `SimpleGutterDetector`, fall back to OpenCV, then Deep.
   - `FOUR_QUADRANTS` -> deterministic 2x2 split.
   - `WEBTOON` -> `webtoonSlices`, adaptive vertical slicing by aspect ratio.
3. If regular detection yields 1 or fewer panels and `autoSwitchIrregular` is enabled, retry the irregular pipeline.
4. If smart splitting is enabled, run `refineInlinePanels`:
   - Trim whitespace around each candidate rect.
   - Spawn crop bitmaps and rerun `SimpleGutterDetector`, `OpenCVPanelDetector`, and the projection-based splitter (`detectInlineByProjection`).
   - Merge overlapping child rects, enforce `MIN_PANEL_SIZE`, and keep significant subdivisions.
   - As a last resort, generate grid-based `inlineFallback` panels tailored to the scan type.
5. Guarantee at least one panel (full page) to avoid empty results.

Detectors must return display-space `Rect` values relative to the original bitmap. New detectors must not mutate the source `Bitmap` and should degrade gracefully by returning an empty list.

## Detectors

- `SimpleGutterDetector` : pure Kotlin; downscales, derives luminance histograms, finds high-white gutters, emits axis-aligned panels.
- `OpenCVPanelDetector` : requires OpenCV; uses contour detection, morphological ops, and optional gutter-based fallback; merges overlaps aggressively.
- `DeepPanelDetector` : coroutine placeholder for ML-based detection (currently returns empty until a model is wired in).
- `HuggingFaceDetector` : stub for future on-device TFLite models converted from HuggingFace.

When adding a detector:

- Implement a standalone object in `detection/`.
- Keep dependencies scoped (native libs should be optional and gated like OpenCV).
- Return `List<Rect>` in bitmap coordinates and document expected bitmap preconditions (size, color space, etc.).
- Register it inside `PanelDetector` in the appropriate pipeline branch.

## Settings Contract

`PanelViewSettings` groups feature toggles so callers can choose the reading experience:

- `PanelDetectionOptions` : toggle panel view mode and smart splitting.
- `PanelScanType` : selects the primary pipeline (regular, irregular, quadrants, webtoon).
- `PanelEnhancementOptions` : currently used for auto-switching and display adjustments; extend cautiously.
- `PanelReadingOrder` : consumed by `PanelOrder` to sort rectangles (standard left-to-right, manga right-to-left, four-koma vertical strips).

Keep new flags additive and avoid coupling UI state or business logic back into detectors.

## Navigation Layer

- `PanelReaderState` stores the bitmap, detected panels, and active index.
- `PanelReaderController` advances or rewinds panels and can request the next or previous page through injected callbacks. This keeps paging out of UI code and makes the controller reusable across views.
- `PanelReaderScreen` is a placeholder; the actual UI integration lives in a fragment using `SubsamplingScaleImageView`. A future Compose implementation should continue to depend only on `PanelReaderState` and `PanelReaderController`.

## Modularity Roadmap

To spin this into a dedicated Gradle module later:

- Keep detector dependencies isolated (OpenCV via optional build flavor, ML kits behind interfaces).
- Move shared contracts (`PanelViewSettings`, `PanelReaderState`, `PanelOrder`) into an `api` package first.
- Provide DI-friendly factories so Android-specific context (for example asset loading) stays outside core logic.
- Add instrumented or unit tests per detector once fixtures are ready; store them under `app/src/test/.../panelview/` to ease relocation.
- Update this README whenever the pipeline order or settings contract changes; it should be the source for AI or automation briefings.
