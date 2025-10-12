# Kotatsu Threads Module

This package hosts the AniList thread integration that augments the existing review feature set.  
It follows the same MVVM + repository pattern used elsewhere in the app and introduces a small set
of new resources and navigation entry points. The notes below document the structure and highlight
the non‑thread classes that were touched so other contributors (human or AI) can quickly pick up the
work.

---

## High-Level Flow

```
AniListThreadClient (GraphQL)
        │
        ▼
ThreadRepository ── caches mediaId, viewer, pages
        │
        ├── ThreadPreviewViewModel (detail preview section)
        ├── ThreadListViewModel    (full list screen)
        └── ThreadDetailViewModel  (thread detail + replies)
```

Each view model exposes a sealed `UiState`, an `EventFlow` for navigation/snackbars, and delegates
network / pagination work to the repository. All write operations reuse the existing AniList OAuth
token the same way the review module does.

---

## Source Files

| Path | Purpose |
| ---- | ------- |
| `AniListThreadClient.kt` | GraphQL entry point: fetch preview threads, thread detail (with comments), create thread/comment, delete comment, render markdown, and look up media IDs. Mirrors `reviews/AniListReviewClient`. |
| `ThreadRepository.kt` | Caches the current media, page cursors, viewer info; mediates fallback in case AniList media IDs shift. Surface APIs: `fetchPreview`, `refresh`, `loadMore`, `fetchThread`, `fetchComments`, `saveThread`, `saveComment`, and `renderMarkdown`. |
| `ThreadPreviewViewModel.kt` | Drives the preview list shown on the manga detail page. Emits `ThreadPreviewState` (Loading / NotAuthorized / NotTracked / Content) and raises navigation events when a thread is tapped. |
| `ThreadListViewModel.kt` | Drives the dedicated list screen; handles pagination, sort selection, and thread creation feedback. |
| `ThreadDetailViewModel.kt` | Loads a single thread + replies, manages pagination for comments, and posts new replies through the repository. |
| `ThreadPreviewAdapter.kt`, `ThreadHeaderAdapter.kt`, `ThreadCommentAdapter.kt` | RecyclerView adapters for preview tiles, the detail header card, and the comment bubbles, respectively. All adapters use Material card styling consistent with the new layouts. |
| `ThreadListActivity.kt`, `ThreadDetailActivity.kt` | Thin activities that wire toolbar navigation, RecyclerView setup, SwipeRefresh, and the new composer card. They rely on Hilt view model injection just like the review sheet module. |

---

## Resources

| File | Notes |
| ---- | ----- |
| `res/layout/item_thread_preview.xml` | Card used by the preview section and list rows (title, meta, timestamp, lock chip). |
| `res/layout/item_thread_header.xml` | Card rendered as the top item in the detail ConcatAdapter. |
| `res/layout/item_thread_comment.xml` | Comment bubble design with avatar, meta line, body, a like chip, and reply button stub. |
| `res/layout/activity_thread_list.xml`, `activity_thread_detail.xml` | Screen layouts for the new activities. Detail view includes the inline composer card. |
| `res/layout/dialog_thread_composer.xml`, `dialog_thread_reply.xml` | Dialogs used when composing from other entry points (e.g., FAB). |
| `res/menu/menu_thread_list.xml` | Toolbar options (sort selection). |
| `res/values/strings.xml` | Added user-facing thread strings (headers, empty states, composer hints, etc.). |

---

## Integration Points (existing modules touched)

| File | Reason |
| ---- | ------ |
| `details/ui/DetailsActivity.kt` | Injected a `ThreadPreviewViewModel`, wired the preview RecyclerView (below the “Community Preview” header), and navigation callbacks for “View more” threads/reviews. |
| `res/layout/activity_details.xml` & `layout-w600dp-land/activity_details.xml` | Added the “Community Preview” containers for threads + reviews with view more buttons. |
| `core/nav/AppRouter.kt` | New helpers: `openThreadList`, `openThreadDetail`. Reuses `ParcelableManga` and attaches thread/list extras. |
| `AndroidManifest.xml` | Registered `ThreadListActivity` and `ThreadDetailActivity`. |

No other core modules were modified; the review system and manga detail view models remain unchanged beyond the new preview hooks.

---

## Network & Auth

- GraphQL endpoint: `https://graphql.anilist.co`
- Queries/mutations follow the new documents in `AniListThreadClient.kt`. Preview uses `Media { threads }`, detail pulls `Thread(id) { comments(page, perPage) }`.
- All writer operations call `client.saveThread(...)` or `client.saveThreadComment(...)`. Ensure the AniList OAuth token is present (same flow as reviews). The repository returns `ThreadAccess.NotAuthorized` / `NotTracked` so UI can hide composer actions accordingly.

---

## Pending / Follow-Up Ideas

- Wire like/unlike actions using AniList’s `ToggleLikeV2` mutation (plumb into `ThreadCommentAdapter` once backend support lands).
- Hook the inline composer “Reply” button to nested replies (currently posts to thread root).
- Add unit tests mirroring the review module once the GraphQL queries stabilise.
- Reuse markdown preview from the review editor if we want richer formatting before posting.

---

## Hand-off Checklist for New Contributors

- When touching thread UI, check both phone (`activity_thread_detail.xml`) and tablet (`layout-w600dp-land/activity_details.xml`) variants.
- Repository caches media IDs aggressively; call `ThreadRepository.resolveAccess` before hitting AniList to avoid 401s.
- Navigation is centralised in `AppRouter`; always add new entry points there to keep back stack behaviour consistent.
- Keep strings in `res/values/strings.xml` grouped under the existing “threads” comments to simplify localisation passes.

Feel free to ping the reviews module for parallel patterns—the thread feature intentionally mirrors its structure so changes are predictable.***
