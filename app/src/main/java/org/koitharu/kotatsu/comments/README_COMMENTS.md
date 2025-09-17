# Kotatsu Comments Feature

This document explains the modular comments feature integrated into Kotatsu.

## How to Disable the Feature

The comments feature can be toggled on or off from its dedicated settings screen. To access these settings:

1. Open any manga details page.
2. Locate the comments section (if enabled).
3. Tap the "Settings" icon (gear icon) in the header of the comments section.
4. In the settings dialog, you will find a toggle labeled "Enable Comments". Turn this off to disable the feature globally.

When disabled, the comments section will not be displayed, and no background processes related to comments will run.

## Where Local Data is Stored

All local data for the comments feature is stored within the application's private storage, specifically under:

`filesDir/comments/`

This directory contains:

* **`comments_prefs.xml`**: This file (or its equivalent if DataStore is used) stores user-specific preferences for the comments feature, such as your display name, avatar URL, moderation settings, and your unique `userId`.
* **`[mangaId_hash_prefix]/[mangaId].json`**: For each manga you view comments for, a JSON file is created to cache the latest 500 comments. These files are organized into subdirectories based on a hash of the `mangaId` to prevent too many files in a single directory.

This data is private to the application and is not accessible by other apps.

## How to Change the Relay URL

The Gun.js relay server can be customized from the comments feature's settings. To change it:

1. Open any manga details page.
2. Tap the "Settings" icon in the header of the comments section.
3. In the settings dialog, locate the "Gun Relay URL" text field.
4. Enter the desired WebSocket URL for your Gun.js relay server.
5. Tap "Save" to apply the changes.

The new relay URL will be used for all subsequent Gun.js connections.

## Moderation Rules

The comments feature includes several client-side moderation rules to ensure a positive community experience. These rules are applied before a comment is sent to the Gun.js network:

* **Max Length**: Comments are limited to 500 characters.
* **Profanity Filtering**: Common profanity is replaced with `***`.
* **Full Censorship Block**: If a comment is entirely censored (becomes only `***` tokens) after profanity filtering, it will be blocked from sending.
* **Rate Limiting**: Users can send a maximum of 3 comments within a 30-second window.
* **Duplicate Blocking**: Identical comments (after normalization) cannot be posted within 60 seconds.
* **Link Limit**: A maximum of 2 URLs are allowed per comment. Excess URLs will be stripped.
* **ALL-CAPS Spam**: If more than 70% of a comment's letters are uppercase, the entire comment will be automatically converted to lowercase.
* **Repeats/Emoji Spam**: Excessive repetition of characters or emojis (more than 20 consecutive) will be collapsed.
* **Reporting and Banning**: If a comment receives 5 reports, the user who posted it will be automatically banned for 90 days. During a ban, the user cannot post new comments, and their existing cached comments will be soft-deleted (marked as removed).
* **Local Mute**: Users can locally mute other users from the comment item's overflow menu. Muted users' comments will not be displayed in your UI.

Client-side moderation can be toggled on or off in the comments settings.
