# Panel View Module

Modern panel-aware reading inside Kotatsu lives in the `panelview/` package. The feature is designed for portability: each detector is a self‑contained strategy, settings capture user intent, and the UI consumes only immutable state plus stable navigation helpers.

## Directory Map

- `app/src/main/kotlin/org/koitharu/kotatsu/panelview/`
  - `PanelDetector.kt` (thin orchestration interface kept for compatibility)
  - `PanelOrder.kt` final ordering utility shared by adapters and detectors
  - `PanelReaderController.kt` / `PanelReaderState.kt` navigation state & cursor logic
  - `settings/` parcelable DTOs (`PanelViewSettings`, `PanelDetectionOptions`, `PanelEnhancementOptions`, `PanelReadingOrder`)
  - `detection/`
    - `PanelDetectionConstants.kt` research-backed thresholds used globally
    - `PanelDetectorImpl.kt` caching + post-processing coordinator
    - `MangaDetector.kt`, `WesternDetector.kt`, `StripDetector.kt`, `WebtoonDetector.kt` dedicated style strategies
  - `ui/` panel-ready reader integration (`PanelReaderFragment`, `PanelPageHolder`, settings sheet)

The tree is intentionally self-contained so the module can graduate into its own Gradle project later.

## Detection Pipeline

`PanelDetectorImpl.detect(bitmap, settings)` is the only place that performs detection. The flow:

1. **Short-circuit** when `PanelViewSettings.detection.enabled` is `false` → return a single full-page rect `(0,0,w,h)`.
2. **Mode resolution**
   - Manual overrides come from `settings.detection.detectionMode`.
   - `AUTO` delegates to `DetectionModeManager.detectMode`, which classifies using aspect ratio thresholds and average saturation.
3. **Per-style detector**
   - Map of detectors: `MANGA`, `WESTERN`, `STRIP`, `WEBTOON` (defaults to Manga when unknown).
   - Each detector returns bitmap-space `Rect`s and never mutates the source.
4. **Post-processing** (shared for all styles, see constants below)
   - Clamp panel coordinates to the image bounds.
   - Filter out rectangles that violate *any* threshold:
     - Minimum panel dimension ≥ **40 px** (width and height)
     - Minimum panel area ≥ **5 %** of the page `(width*height*0.05)`
     - (When contour data available) rectangularity >= **0.75**.
   - Merge overlapping rectangles when overlap fraction (intersection area ÷ smaller rect area) ≥ **0.15**.
   - Ensure at least one rect; fallback is the entire page.
   - Sort results using `PanelSorter` (style-specific strategy) followed by `PanelOrder.order` (reading order aware).
5. **Caching**
   - Results are stored under `<cacheDir>/panel_cache/` keyed by bitmap hash, reading order, detection mode, and `CACHE_VERSION`.
   - Cache files are JSON lists (`Gson`) of `Rect` values.

The implementation is fully deterministic: identical bitmap + settings produce identical rectangles.

## Detection Strategies

All detectors operate in OpenCV on a background dispatcher (`Dispatchers.Default` / `Dispatchers.IO` inside `PanelPageHolder`).

### MangaDetector
| Step | Details |
| --- | --- |
| Pre-process | Convert to HSV to estimate saturation. |
| Branch | Saturation `< 20` → grayscale + **Otsu** threshold; otherwise grayscale + **adaptive Gaussian** thresholding. |
| Contours | `RETR_TREE` + `CHAIN_APPROX_SIMPLE`, inspect only top-level contours (parent = -1). |
| Filters | Rectangularity ≥ **0.75**, area ≥ 5 %, width & height ≥ 40 px. |
| Output | Sorted by `top` then `left`; fallback to full page if empty. |

This covers both B&W and colour manga without a second detector.

### WesternDetector
| Step | Details |
| --- | --- |
| Canny | Thresholds **50 / 150** on the grayscale page. |
| Morphology | `MORPH_CLOSE` + `dilate` using a **3 × 3** kernel. |
| Contours | `RETR_EXTERNAL`, convert to bounding rects. |
| Filters | Same 40 px / 5 % / 0.75 rectangularity filters. |
| Output | Passes raw rects to post-processing for merging; fallback to full page if empty. |

### StripDetector
- Deterministic equal-width slicing.
- Choose panel count by aspect ratio: `<3.0` → 3 panels, `3.0–3.8` → 4, `>=3.8` → 5.
- Rectangles span `0..height`.

### WebtoonDetector
| Step | Details |
| --- | --- |
| Pre-process | Grayscale + Gaussian blur (`3 × 3` kernel). |
| Threshold | Otsu binary inversion. |
| Projection | Reduce the binary image vertically to a single column sum. |
| Gap detection | Row density < **10 %** of full black → candidate gap; require consecutive gap height ≥ **20 px**. |
| Slice | Full-width panel between gaps; last panel extends to page bottom. |

## Post-processing & Ordering

- Constants live in `PanelDetectionConstants`.
- `PanelSorter.sortPanels(panels, mode)` chooses per-style ordering:
  - Manga: top-to-bottom, right-to-left inside row.
  - Western: top-to-bottom, left-to-right.
  - Strip: left-to-right (single row).
  - Webtoon: top-to-bottom.
- `PanelOrder.order(panels, readingOrder)` reorders for user-selected reading order (`STANDARD`, `MANGA`, `FOUR_KOMA`) using row clustering with a **35 %** vertical overlap tolerance.
- Final list is consumed by `PanelReaderController` and the overlay renderer.

## Settings & UI Surface

`PanelViewSettings` (parcelable):
```kotlin
PanelViewSettings(
    detection = PanelDetectionOptions(
        enabled = Boolean,
        smartSplitting = Boolean, // reserved
        detectionMode = DetectionMode
    ),
    readingOrder = PanelReadingOrder,
    enhancements = PanelEnhancementOptions(
        autoSwitchIrregular = Boolean, // reserved
        fitToWidth = Boolean,
        panBound = Boolean,
        overlayEnabled = Boolean,
        zoomEnabled = Boolean,
        borderOpacity = Float // 0.0 – 1.0
    ),
)
```

User controls:
- **Quick toggle button** in `fragment_reader_pager.xml`: popup menu to enable/disable detection, switch detection mode, or change reading order on the fly.
- **Panel Settings bottom sheet**:
  1. Enable / disable panel view
  2. Manual detection mode (Auto, Manga, Western, Strip, Webtoon)
  3. Reading order (Western, Manga, Four-koma)
  4. Overlay toggle + opacity slider (0 % – 100 % in 5 % steps; persisted to `borderOpacity`)
  5. Auto-zoom toggle

Changes propagate via `PanelReaderFragment.onSettingsChanged`, purge cached panels when mode/order flips, and update the overlay behaviour immediately.

## Navigation & Zoom

- `PanelReaderController` caches up to **12** recent page states (`MAX_CACHED_PAGES`) for quick backtracking.
- `PanelPageHolder` handles detection off the UI thread, caches states per page, and triggers `PanelReaderController` updates.
- Zoom transitions (`PanelPageHolder.focusState`) animate with `SubsamplingScaleImageView.animateScaleAndCenter` at **300 ms**, padding panel rects by **32 px** so panels fill the screen without harsh edges. Zoom is skipped when user disables auto zoom.
- Overlay rendering lives in `PanelOverlayView`: dim background, highlight border thickness `2dp`, opacity controlled by settings.

## Integration Notes

- `PanelReaderFragment` replaces the reader pager layout with standard `ViewPager2` (`fragment_reader_pager.xml`).
- Quick toggle button text reflects detection state using `panel_quick_toggle_label_pattern`.
- Build depends on OpenCV via existing Gradle setup; no other native libs required.
- All panel-specific classes reside under `panelview/`; consuming code interacts only through `PanelReaderFragment` or the settings DTOs, preserving modularity.

## AI Agent Quick Reference

Use this section when handing work to another automation agent:

### Core Kotlin entry points
- `app/src/main/kotlin/org/koitharu/kotatsu/panelview/detection/PanelDetectorImpl.kt` — detection coordinator, cache, post-processing constants.
- `app/src/main/kotlin/org/koitharu/kotatsu/panelview/detection/MangaDetector.kt` / `WesternDetector.kt` / `StripDetector.kt` / `WebtoonDetector.kt` — style-specific pipelines.
- `app/src/main/kotlin/org/koitharu/kotatsu/panelview/detection/DetectionModeManager.kt` — AUTO mode heuristics.
- `app/src/main/kotlin/org/koitharu/kotatsu/panelview/ui/pager/PanelReaderFragment.kt` — hooks panel mode into the reader UI, handles quick toggle menu.
- `app/src/main/kotlin/org/koitharu/kotatsu/panelview/ui/pager/PanelPageHolder.kt` — runs detection, manages zoom/overlay, feeds `PanelReaderController`.
- `app/src/main/kotlin/org/koitharu/kotatsu/panelview/ui/settings/PanelSettingsBottomSheet.kt` — settings sheet wiring.

### Key resources
- `app/src/main/res/layout/fragment_reader_pager.xml` — pager container; quick toggle button lives here.
- `app/src/main/res/layout/bottom_sheet_panel_settings.xml` — settings bottom sheet layout.
- `app/src/main/res/values/strings.xml` — panel strings share prefix `panel_…`.
- `app/src/main/res/layout/item_page.xml` — hosts `PanelOverlayView` (ensure overlay ids line up with `PanelPageHolder`).

### Supporting contracts
- `PanelDetectionConstants.kt` — authoritative numeric thresholds (40 px, 5 %, 0.75, 15 %, etc.).
- `PanelViewSettings.kt` — parcelable state passed between fragments/viewmodels.
- `PanelOrder.kt` & `PanelSorter.kt` — final ordering logic; adjust when adding new reading orders.
- Cache path: `<cacheDir>/panel_cache/` (JSON rectangles).

### External touchpoints
- Reader config UI: `app/src/main/kotlin/org/koitharu/kotatsu/reader/ui/config/ReaderConfigSheet.kt` (toggle) if new options are exposed.
- Build config: `app/build.gradle` for OpenCV dependency updates.
- Tests (future): place fixtures under `app/src/test/.../panelview/`.

Keep this document in sync whenever constants, detectors, or UI contracts change—it is the canonical spec for automation and future module extraction.
