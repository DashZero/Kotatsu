# Kotatsu Reviews

## What

- Adds manga-level reviews powered by AniList GraphQL.
- Reviews are fetched for tracked manga only; no chapter-level reviews are stored.

## Where

- Entry point: Review action inside the Details → Chapters & Bookmarks bottom sheet.
- UI: dedicated `ReviewsSheet` bottom sheet that follows Kotatsu’s chat-bubble visual language.

## How

- Endpoint: `POST https://graphql.anilist.co`.
- Auth: reuses existing AniList OAuth token via `@ScrobblerType(ScrobblerService.ANILIST)` OkHttp client (Authorization: Bearer \<token\>).
- Queries / Mutations:
  - `ViewerShort` to resolve current user.
  - `MediaReviews(mediaId, page, perPage)` for paginated reviews.
  - `Review(mediaId, userId)` for the active user’s review.
  - `SaveReview(mediaId, summary, body, score)` to create/update.
  - `DeleteReview(id)` to remove.
  - `Markdown(markdown)` to render optional Markdown preview.
- Request variables follow AniList schema (`mediaId:Int`, `page:Int`, etc.); payloads are cached in memory per manga.

## When

- Pagination: page size 10, infinite scroll via `ReviewRepository.loadMore()`.
- Refresh: pull-to-refresh or optimistic save/delete triggers a first-page refetch and resets cache.
- Cache: in-memory per mediaId; invalidated on auth loss, save/delete, or manual refresh; not persisted across process death.
- Offline: falls back to cached list when available; otherwise surfaces error toast.

## Why minimal core edits

- Core was only touched to expose the entry point and reuse existing scrobbling storage/token plumbing. All network, caching, and UI logic lives under `org.koitharu.kotatsu.reviews`.

## Errors & Resiliency

- Token expiry / missing token → gated state with `review_sign_in_required`.
- Manga not tracked → gated state with `review_track_required`.
- Rate limiting (HTTP 429) → exponential backoff with contextual toast (`review_rate_limited`) and snackbar logging.
- GraphQL validation errors → surfaced through existing `onError` observers; user input validated locally (summary/body length).
- Network downtime (403/5xx) → error observer + retry affordances (pull-to-refresh).
- Privacy: review payloads and tokens are never logged in release builds.

## Theming

- Bubble colors reuse `colorSurfaceContainerHigh` / `colorPrimaryContainer` to stay consistent in light & dark themes.
- Avatars respect existing `CoilImageView` corner shapes.
- Action buttons use Material outlined styling to match bottom sheet affordances.

## Touched Core Files

- `app/src/main/res/menu/opt_chapters.xml` & `opt_pages.xml` – added Reviews action.
- `app/src/main/kotlin/org/koitharu/kotatsu/details/ui/pager/ChapterPagesMenuProvider.kt` – wired menu handler.
- `app/src/main/kotlin/org/koitharu/kotatsu/core/nav/AppRouter.kt` – navigation hook into `ReviewsSheet`.
