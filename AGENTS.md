You are an expert Android engineer working on an existing open-source / forked manga reading app (APK build exists, source code is available in the repo). Your task is to implement a new optional feature called “Anti Motion Sickness” similar to Apple iOS “Vehicle Motion Cues” and vivo OriginOS “Motion Prompts”.

Goal:
Add an in-app overlay (NOT system overlay) that shows a subtle moving dot / edge-cues while reading, driven by device motion sensors (accelerometer + gyroscope / rotation vector), to reduce car sickness when reading in a moving vehicle. User can toggle it ON/OFF in app settings and optionally via a quick toggle in the reader screen.

Constraints:
1) Must be modular: new feature must live in a new package/module area, not scattered across core reading logic.
2) Do not rewrite the reader core. Only small integration points are allowed:
   - add settings key
   - add one overlay view in reader UI container
   - register/unregister based on reader lifecycle
   - add a toolbar toggle (optional but recommended)
3) No “draw over other apps”, no SYSTEM_ALERT_WINDOW permission. Only draw inside the app window.
4) Must be battery-friendly: sensors active only while the reader screen is visible and feature enabled.
5) Provide clean architecture: separate sensor processing from UI rendering from settings state.
6) Must be robust: smooth motion, avoid jitter, cap movement, handle orientation changes, handle sensor absence.
7) Keep changes small and reviewable: prefer adding new files and small diffs to existing ones.
8) Provide a clear PR-style summary at the end: files changed, what was added, how to test.

Deliverables:
- New feature implementation (code + resources).
- Settings toggle in app settings (Reader settings).
- Reader overlay: subtle dot or edge-cues that move with motion.
- Optional quick toggle in reader toolbar/overflow menu.
- Basic instrumentation/logic test (at least for filtering / mapping function).
- Documentation: short README section “Anti Motion Sickness”.

Feature behavior (exact spec):
A) Visual:
- Default: ONE small dot (8–12dp) drawn near screen edges, OR four small dots near each edge (Apple-like). Pick ONE approach that is easy to implement and unobtrusive. Prefer: four dots near edges.
- Color: auto-contrast with reader theme (light/dark). If app has theme system, follow it. Otherwise, use a neutral color with alpha ~0.6.
- Must not obstruct content; place near edges with margin 8–16dp.
- Movement:
  - When car accelerates forward, dots should move down slightly.
  - When braking, dots move up slightly.
  - When turning right, dots drift left.
  - When turning left, dots drift right.
  - Keep motion subtle: max displacement ~24–36dp.
  - Movement must be smoothed and damped.

B) Sensors & mapping:
- Use SensorManager and preferably TYPE_ROTATION_VECTOR + TYPE_LINEAR_ACCELERATION if available.
- If TYPE_LINEAR_ACCELERATION not available, use TYPE_ACCELEROMETER with gravity removal (high-pass / low-pass separation).
- Compute a stable “vehicle frame” approximation:
  - We do NOT need perfect navigation-grade; we need stable left/right and forward/back cues.
  - Use device orientation from rotation vector to transform acceleration into a world frame, then project into screen axes.
  - If orientation math is too risky in this codebase, fall back to using screen axes directly (portrait): X for left/right, Y for forward/back, but keep it behind a strategy interface so we can upgrade later.
- Filtering:
  - Apply low-pass smoothing to the displayed offset:
    displayed = displayed * (1 - alpha) + target * alpha
    with alpha around 0.10–0.20 (tune).
  - Apply dead-zone threshold to ignore tiny vibrations:
    if |value| < 0.03g => treat as 0 (tune).
  - Apply clamp to max displacement.

C) Activation:
- User setting: “Anti Motion Sickness” (boolean, default OFF).
- When OFF: overlay view not attached (or attached but GONE); sensors not registered.
- When ON: overlay visible only on the Reader screen (manga page reading view). Not in library screens.
- Auto-hide: if no meaningful motion for 2–3 seconds, gently animate back to neutral and reduce alpha (optional; implement if simple).

D) UX:
- Settings entry includes a short help text:
  “Shows subtle motion indicators to reduce discomfort when reading in a moving vehicle.”
- Optional: a “Sensitivity” slider (Low/Medium/High) OR a numeric scale (0.5x–1.5x). Implement only if easy with the existing settings framework. If it would require large changes, skip slider and keep a constant tuned value.
- Reader quick toggle:
  - Add to reader toolbar overflow: “Anti Motion Sickness” with a checkmark
  - Toggling updates setting and immediately enables/disables overlay

Implementation plan (follow exactly):
1) Repo discovery:
   - Inspect codebase to find:
     - reader screen Activity/Fragment (e.g., ReaderActivity/ReaderFragment)
     - main reader container layout where you can add an overlay view on top of pages (FrameLayout)
     - settings storage mechanism (SharedPreferences / DataStore / custom settings manager)
     - theming system for colors
   - Do NOT refactor. Identify minimal integration points.

2) Create new package:
   - Create package: com.<app>.features.motioncues (exact root uses existing app package)
   - Add these components (new files):
     a) MotionCuesController
        - public API:
          - start(lifecycleOwner, motionCueHost: MotionCueHost)
          - stop()
          - setEnabled(boolean)
          - setSensitivity(float) (optional)
        - internally coordinates settings, sensors, and UI updates.
     b) MotionCueHost (interface)
        - methods:
          - showMotionCues()
          - hideMotionCues()
          - setOffsets(dxPx: Float, dyPx: Float)
          - setIntensity(alpha: Float) (optional)
        - Reader screen implements host by delegating to overlay view.
     c) MotionCueOverlayView (custom View or ViewGroup)
        - draws dots and applies translation based on dx/dy.
        - supports theme changes (dark/light)
        - neutral position is stored.
     d) MotionSensorSource
        - encapsulates SensorManager registration/unregistration
        - emits sensor samples (accel + rotation)
        - runs callbacks on main thread (use Handler/Looper)
     e) MotionEstimator
        - converts raw sensor samples -> target offsets (dx, dy in “g” units or normalized)
        - handles dead-zone, mapping inversion (turn right => drift left)
     f) MotionSmoother
        - applies low-pass smoothing and clamps output
     g) MotionCuesSettings
        - get/set enabled flag, sensitivity (if implemented)
        - uses existing settings infra; keep API small.
     h) Small unit test for MotionSmoother / MotionEstimator mapping.

3) Settings integration (minimal):
   - Add one boolean preference key:
     - key: motion_cues_enabled
     - default: false
   - Add UI entry in Reader settings screen:
     - Title: “Anti Motion Sickness”
     - Summary: “Show subtle motion indicators while reading in a moving vehicle.”
   - If a sensitivity option is feasible:
     - key: motion_cues_sensitivity (float or enum)
     - default: 1.0
     - show as list: Low/Medium/High mapping to 0.75/1.0/1.25 (keep simple)

4) Reader screen integration (minimal):
   - Add overlay view to reader container:
     - If reader uses a FrameLayout: add MotionCueOverlayView as the last child so it draws on top.
     - Keep overlay view creation small and local.
   - Create MotionCuesController instance in reader screen, but keep lifecycle clean:
     - onStart/onResume: if setting enabled, controller.start(...)
     - onStop/onPause: controller.stop()
   - Implement MotionCueHost in the reader screen or a small adapter class inside reader screen file to avoid polluting it.

5) Quick toggle (optional but recommended):
   - Add one menu item in reader overflow:
     - id: action_motion_cues
     - title: “Anti Motion Sickness”
     - checkable: true
     - checked state from settings
   - On click: toggle setting and call controller.setEnabled(newValue).
   - Ensure it updates immediately without restarting activity.

6) Performance & safety:
   - Sensor sampling rate: SENSOR_DELAY_GAME or SENSOR_DELAY_UI. Prefer UI for battery; tune if laggy.
   - Avoid allocations in sensor callback. Reuse arrays/objects.
   - Use Choreographer or main-thread Handler to apply UI updates no faster than display refresh; if complex, simply update view translations and rely on smoothing.
   - Ensure sensor listeners are unregistered on stop to prevent leaks.

7) Visual math:
   - Convert normalized offsets to px:
     - maxPx = dpToPx(30) * sensitivity
     - dxPx = clamp(-axNormalized * scale, -maxPx, maxPx) where axNormalized is lateral acceleration.
     - dyPx = clamp(+ayNormalized * scale, -maxPx, maxPx) for forward/back (note direction rules above).
   - Use inversion rules exactly:
     - turning right => indicators drift left  (dx negative)
     - turning left  => indicators drift right (dx positive)
     - accelerating forward => drift down (dy positive)
     - braking => drift up (dy negative)

8) Documentation:
   - Add a short section in README or help page:
     - what it is, how to enable, safety note: passenger use only.

9) Final output requirements:
   - Provide:
     - a list of all new files added
     - a list of existing files modified (should be few)
     - instructions for manual testing:
       - enable setting
       - open reader
       - simulate motion by walking/tilting phone
       - verify dots move smoothly and return to neutral
     - screenshots are not required, but describe expected visuals.

Non-negotiable:
- Keep diffs small.
- No large refactors.
- Do not add new heavy dependencies unless absolutely necessary; prefer standard Android SDK classes.
- If the project uses Compose, implement overlay as Compose; if View-based, implement as View. Follow existing UI tech.

Start now:
1) Identify package name, reader screen entry point, and settings mechanism.
2) Implement the new module files.
3) Integrate into settings + reader with minimal changes.
4) Provide final PR summary and testing steps.