# Upstream BitChord v1.10 integration tracker

Source: `kushagrasinghx/BitChord` release tag `v1.10` (2026-11-21), corresponding to upstream PR #1717 (291 changed files).
Target baseline: fork commit `55dfa3dd31a2c14e8fec4c88342de06cadf3e675` (installed fork version code 46).
Integration branch: `feature/upstream-v1.10-integration`.

## Non-negotiable fork invariants

- Keep the approved Playlist/Artist top-control shapes, material, and outline exactly as installed. Do not replace or refactor that chrome as part of upstream player/navigation changes.
- Keep the Artist count correction: one track count, no duplicate "songs" subtitle.
- Keep song-level Remove from Playlist / Remove from Artist collection actions and their collection-only semantics.
- Keep the existing mini-player, lyrics responsiveness/indicators, explicit badge, and other installed custom playback/UI fixes unless a specific upstream change is demonstrably compatible.
- Do not merge upstream files wholesale when the fork version has diverged from the v1.10 PR base; port the feature into the fork's current implementation.
- This fork's playlists are app-managed/local collections. Any upstream playlist feature must use the fork's collection store and must not silently redirect playlist CRUD or add/remove/reorder actions to YouTube Music's library playlists.
- Preserve Android application ID, signing identity, versionCode monotonicity, fork-only update URL, and phone/TV build workflows.

## v1.10 feature and changelog checklist

Status legend: `[ ]` not yet audited/ported; `[~]` audited or in progress; `[x]` integrated and verified; `[—]` intentionally not applicable/blocked with reason recorded.

### New headline features
- [ ] Replay Year animated, shareable listening stories.
- [ ] Google Drive backup, restore, automatic sync, and cross-device Replay aggregation.
- [ ] Radio tab and station experience.
- [ ] Best Part: jump to the most replayed section of a song.
- [ ] YouTube Premium higher-quality Opus stream support.
- [ ] Automatically download and retain liked songs offline.
- [ ] Animated, synchronized lyrics video sharing.
- [ ] AutoMix artwork transitions.
- [ ] Queue swipe actions, undo, and multi-select.

### App size, player, metadata, and system integrations
- [ ] Download AutoMix models on first enable rather than packaging full models in the APK; verify integrity, resume/failure handling, ABI/native runtime compatibility, and offline behavior.
- [ ] Apple Music-style player button animations (preserve fork's existing motion and player layout).
- [ ] Optional total-duration display instead of remaining time.
- [ ] Setting to hide lyrics preview.
- [ ] Setting to disable Spotify Canvas.
- [ ] Song Info credits.
- [ ] Quick Settings tile to play/pause the last queue.
- [ ] Sleep timer gradual 30-second fade-out.
- [ ] Android Auto / external-controller shuffle behavior.
- [ ] Correct Android notification/media action icons and Next must not like a song.

### Search, playlist, album, artwork, and library
- [ ] Search filter for YouTube Music results limited to the user's YTM library (keep this distinct from the fork's local collections).
- [ ] Swipe-to-queue and long-press live search results while typing.
- [ ] Find a playlist in Add to playlist; map to fork-managed playlists.
- [ ] Add to playlists from album menus; map to fork-managed playlists and preserve existing local playlist CRUD.
- [ ] Track-number sorting for local albums.
- [ ] High-resolution YouTube video-track artwork and no low-resolution/flickering transition covers.
- [ ] Local tracks and opened files use their embedded/file artwork.
- [ ] Long YouTube Music library playlists continue loading; do not confuse these with app-managed local playlists.
- [ ] Fully reorder long playlists where applicable; for fork-managed playlists, keep the fork's storage/order rules.
- [ ] Imported playlists can be renamed, edited, and deleted; adapt to the fork's import/collection source and never mutate a remote playlist unexpectedly.

### Lyrics, playback, queue, Replay, matching, navigation, stability
- [ ] YouTube Music timed lyrics instead of a static sheet.
- [ ] Prefer synced lyrics over plain lyrics.
- [ ] Read embedded lyrics from local MP3 files.
- [ ] Smoother karaoke sweep that holds across pause/resume.
- [ ] Refreshed lyrics strip glow (preserve the fork's established lyrics styling and responsiveness fixes).
- [ ] RTL translations aligned under their own lines.
- [ ] Chinese translation fix.
- [ ] Sorted playlists/albums play in displayed order (distinguish YTM browse pages from fork-local collection playback).
- [ ] AC-4 decoding through FFmpeg; validate native packaging, device compatibility, and licensing/source build.
- [ ] Loudness limiter engages with spatial audio to prevent distortion.
- [ ] Queue opens at upcoming songs; preserve the current-song semantics.
- [ ] Replaying queue history must not re-queue the item.
- [ ] Remove redundant Now Playing queue row if it is still present in the fork UI.
- [ ] Playlist context remains correct across Android Auto/notifications/external controllers.
- [ ] Next action no longer rates/likes the current song.
- [ ] Remote-speaker listening counts in Replay.
- [ ] Top Albums no longer crashes on Yearly/Lifetime Replay.
- [ ] Accent-insensitive title/artist matching.
- [ ] Better CJK romanized title matching.
- [ ] Tapping the active tab scrolls that tab to the top.
- [ ] Fix full-screen music video on tablets.
- [ ] Fix search results behavior.
- [ ] Fix YouTube Recents on Direct Source Stream.
- [ ] Review and port the remaining general stability fixes from all 291 changed files.

## File-level audit rules

The upstream PR changes 291 paths (87 added, 199 modified, 5 removed). Each changed path must be checked against the current fork before porting:
1. If the fork file is identical to the upstream PR base, an upstream replacement/patch can be considered, after tests.
2. If the fork file diverged, apply a focused feature-level port and preserve fork-only logic.
3. If a path is new, verify its dependencies, resources, platform source sets, settings, and call sites before adding it.
4. If upstream removes a file, retain fork behavior unless the removal is necessary and proven safe.
5. Mark the feature complete only after relevant unit/JVM tests, Android build, TV build (where affected), and artifact validation pass.

## Initial source comparison
- Upstream v1.10 PR: #1717, 291 changed files.
- Fork working baseline: `55dfa3d`; version code 46 after independent update-channel change.
- A dedicated integration branch was created from that baseline so the currently installed/stable fork branch is not overwritten.
- First isolated port selected for implementation: Song Info credits. The fork's SongInfoRepository and SongInfoDialog match the upstream PR base exactly, making this an unusually low-risk first candidate.
