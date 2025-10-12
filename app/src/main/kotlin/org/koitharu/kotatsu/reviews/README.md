# Kotatsu Reviews Module

This package integrates AniList reviews into Kotatsu using the same MVVM + repository structure as
the new threads feature. The notes below outline the module’s responsibilities, key entry points,
and the small number of hooks into the core app so other contributors can work confidently without
reverse‑engineering the whole flow.

---

## Architecture Overview

```
AniListReviewClient (GraphQL & mutations)
          │
          ▼
ReviewRepository ── manages cached feed, viewer, drafts
          │
          ├── ReviewViewModel        (list + inline actions)
          ├── ReviewDetailViewModel  (single review screen)
          └── review editor dialog helpers
```

`ReviewViewModel` exposes a `ReviewUiState` (Loading, NotAuthorized, NotTracked, Content with paging)
and forwards GraphQL calls through the repository. Mutations (save, delete, rate) bubble up as
messages so the UI can surface snackbars without re‑fetching everything.

---

## Core Source Files

| Path | Purpose |
| ---- | ------- |
| `AniListReviewClient.kt` | Wraps AniList’s GraphQL endpoints used by Kotatsu: fetch reviews for a media, fetch viewer’s review, save/delete reviews, rate reviews, render markdown, resolve fallback media IDs. Handles rate limits transparently. |
| `ReviewRepository.kt` | Caches review feed, viewer, media list IDs, and drafts. Provides APIs for refresh, pagination, save/delete, vote, and draft persistence. |
| `ReviewViewModel.kt` | Backing logic for both the detail preview list and the dedicated sheet; owns `ReviewUiState` and message/event flows. |
| `ReviewDetailViewModel.kt` | Powers the standalone `ReviewDetailActivity` screen (view a single review, vote, edit/delete). |
| `ReviewDetailActivity.kt`, `ReviewUi.kt`, `ReviewEditorDialog.kt` | UI controllers: activity for full review view, `ReviewsSheet` bottom sheet for lists, and the markdown editor dialog with draft support. |
| `ReviewDraftStorage.kt`, `ReviewDraft.kt` | Simple file-based storage for unsent reviews keyed by mediaId + userId. |

---

## Resources

| File | Notes |
| ---- | ----- |
| `res/layout/sheet_reviews.xml` | Bottom sheet list layout (swipe refresh + recycler). |
| `res/layout/item_review.xml` | Review list item with avatar, summary, body snippet, score, vote counts. |
| `res/layout/activity_review_detail.xml` | Full review screen with markdown-rendered body, vote buttons, share/delete actions. |
| `res/layout/dialog_review_editor.xml` | Markdown editor dialog shared between sheet and detail. |
| `res/values/strings.xml` | Strings under the “ANILIST REVIEW” comment block cover copy for states, buttons, errors, markdown hints, etc. |

---

## Integration Points / Existing Modules Touching Reviews

| File | Reason |
| ---- | ------ |
| `details/ui/DetailsActivity.kt` | Hosts the preview list beneath “Community Preview”, handles “Write / Edit / Delete review” actions, and routes to detail sheet/activity. |
| `res/layout/activity_details.xml` & `layout-w600dp-land/activity_details.xml` | Contain the preview RecyclerView + CTA buttons that are toggled based on `ReviewUiState`. |
| `core/nav/AppRouter.kt` | Contains helpers (`showReviews`, `openReviewDetails`) for routing from activities/fragments to the review sheet / detail activity. |
| `AndroidManifest.xml` | Registers `ReviewDetailActivity`. |

No other core modules rely on reviews, but the pattern (repository + view model + bottom sheet)
served as the template for the new threads feature.

---

## Network & Auth Notes

- Endpoint: `https://graphql.anilist.co`
- Reviews reuse the OAuth token already stored for AniList tracking. If the token is missing or the
  manga isn’t linked to AniList, the repository returns `ReviewAccess.NotAuthorized` or
  `ReviewAccess.NotTracked` so the UI can show the correct messaging.
- `ReviewRepository.resolveAccess` is the gatekeeper—always call it before trying to load data for a
  manga so the fallback media ID logic remains consistent.
- Markdown preview (`ReviewEditorDialog`) uses `AniListReviewClient.renderMarkdown`, which offloads
  the conversion to AniList’s renderer.

---

## Hand-off Tips for New Contributors

1. **Preview vs. Full List:** The same `ReviewViewModel` instance feeds both the inline list and the
   full sheet. Keep state transitions idempotent and surface message events through the provided
   `EventFlow`.
2. **Drafts:** The editor dialog auto-saves drafts after a delay; if you change save behavior make
   sure to respect `ReviewDraftStorage` so users don’t lose work.
3. **Pagination:** `ReviewViewModel.loadMore()` relies on the repository’s cached page values. When
   changing sort/order, reset the cache by calling `ReviewRepository.refresh`.
4. **Voting:** The vote buttons call `ReviewRepository.rateReview`, which returns an updated review
   snapshot. Update the adapter with that result to avoid refetching the whole page.
5. **Localization:** All user-facing strings live together under the review section in
   `strings.xml`. Add new copy there and avoid hard-coded text in Kotlin or layouts.

Refer to this README together with the threads README if you plan to evolve both features—the stacks
are deliberately parallel, so improvements can usually be ported from one module to the other with
minimal friction.***
