# Ownsonic — Android App Specification

## Purpose

Ownsonic is an Android music and podcast player for listening while traveling with poor or no internet access. The user reviews songs and rates them to help decide which songs to keep or remove later.

The app synchronizes music and ratings with an Airsonic server. Rating a song keeps it in the current local queue until the next successful synchronization. Rated songs remain in the server playlist but are excluded from the refreshed local library.

## Source selection

- Let users check the server connection and load playlists and podcast channels in Settings using the current connection fields.
- Select sources by server ID, including duplicate names. Offer No podcast for music-only use; podcast API errors must not prevent choosing a music playlist.
- Allow 1 to 50 podcast episodes, defaulting to five. Let users choose the oldest or newest available episodes (oldest by default), include heard episodes, and play the selected subset oldest first.
- Persist connection and source settings together. Keep cached queues, playback positions, and pending changes intact when saving; apply source changes through the next successful sync.
- Existing installations without source settings retain `laterlist`, channel `7` (Aerostat), and five episodes. New installations require a playlist selection and start with podcasts disabled.
- Missing selected playlists fail safely without replacing the local library. Episodes removed from the selected channel, or disabling podcasts, are reflected locally only after a successful sync.

## Main workflow

1. Configure the Airsonic server URL, username, and password.
2. Load sources from the server in Settings, choose a music playlist and an optional podcast with an episode limit, save, and tap Sync.
3. Listen to the downloaded songs offline in a fixed shuffled order.
4. Rate songs using 👍 or 👎, optionally returning to a previous song to correct its rating.
5. Tap Sync again when connectivity is available to upload ratings and refresh the downloaded playlist.

## Synchronization

Synchronization is started manually using the Sync button.

An active or queued synchronization can be cancelled from the Sync screen or its notification. Cancellation interrupts network requests, removes unfinished downloads, and preserves the current library and playback positions. Completed downloads are reused on retry. Ratings and listening events already acknowledged by the server remain confirmed; unsent changes stay pending. Cancelling one run does not disable scheduled synchronization.

The synchronization sequence is:

1. Upload pending local rating changes to Airsonic.
2. Upload pending completed-listening events to Airsonic, oldest first.
3. Retrieve the current contents of the selected music playlist and exclude songs with any nonzero rating, including ratings just uploaded by the app.
4. Retrieve episodes from the selected podcast (if enabled) and select up to the configured number of oldest or newest server-downloaded episodes by publication date, including episodes already heard.
5. Download missing songs and podcast episodes, reporting overall progress across both.
6. Only after all downloads succeed, delete local files no longer included in the refreshed music or podcast queues.
7. Shuffle the refreshed music queue once, select its first song, and reset music playback position. Preserve the current podcast episode position if the episode remains available.

Synchronization must not start playback automatically.

### Automatic synchronization

- Provide a Settings option disabled by default.
- Schedule daily runs at 23:30 and 05:00 in the phone's local time, preserving wall-clock times across daylight saving and time-zone changes.
- At 23:30, synchronize only if local ratings or completed listens are pending upload. At 05:00, synchronize regardless of pending changes.
- Require an active Wi-Fi connection, including metered Wi-Fi; automatic connections must not fall back to cellular data.
- Defer automatic synchronization while playback is active. Serialize synchronization operations and use the existing Sync status screen.
- Persist scheduling through app restarts and device reboot. Android may delay execution due to system restrictions or unavailable Wi-Fi.
- Disabling the option cancels future and pending automatic runs without cancelling manual synchronization. Manual Sync can replace an automatic request waiting for suitable conditions.

Local rating changes take priority over server ratings. A pending rating must remain stored locally until the server confirms that it has been saved. Failed uploads can be retried during a later synchronization.

Local songs must only be removed after the playlist has been retrieved successfully. A failed synchronization must not reset the saved listening progress or reshuffle the existing queue. Errors and synchronization progress must be visible to the user.

## Offline playback and queue

- Downloaded songs must be playable without an internet connection.
- The queue is shuffled once after each successful synchronization.
- The resulting queue order is saved and remains unchanged during playback, navigation, and app restarts until the next successful synchronization.
- Playback stops after the last song. The queue does not repeat automatically.
- The current song and playback position are saved and restored after the app is closed and reopened.
- A successful synchronization resets the current song and position to the beginning of the new queue, with playback stopped.
- Playback continues in the background and while the screen is off.
- Playback controls are available through an Android media notification and a headset.

## Offline scrobbling

- Record a listening event only when a song finishes naturally, including automatic transitions and the last song in the queue.
- Store the timestamp of the start of playback, rather than completion or synchronization time.
- Each repeated completed listen creates a separate event with its own identifier and start timestamp.
- A song must be longer than 30 seconds and actually played for at least half its duration or four minutes, whichever is shorter.
- Paused and buffering time does not count toward actual playback time.
- Skip, Dislike, and Stop do not create listening events. A manual seek within a song disqualifies that session; restarting from the beginning starts a fresh session.
- Persist pending events and the active listening session across restarts. Time while playback is not running does not count.
- During Sync, call Subsonic `scrobble` with the song ID, the original start timestamp in milliseconds since the Unix epoch, and `submission=true`.
- Remove an event only after Airsonic confirms receipt. Failed uploads keep events pending and prevent queue reset and local song removal for that sync.
- Last.fm integration must be configured for the Airsonic user. Airsonic forwards events to Last.fm; its API success response does not confirm Last.fm delivery.

## Playback controls

- Configure Like and Dislike independently from 1 to 5 stars in Settings, defaulting to 2 and 1. Persist both values and use them for rating submission, button highlighting, and accessible labels immediately after saving. Existing ratings and pending uploads retain their original values.

Use monochrome vector icons for playback and rating buttons on Music and Podcast, including episode-list playback buttons. Use light gray icons on dark backgrounds; highlight the selected rating in lime. Navigation and synchronization actions may retain emoji. Buttons should be large enough for convenient use while traveling, with accessible labels describing their actions.

| Button | Action |
| --- | --- |
| ⏮️ Previous | Return to the previous song in the saved queue, allowing its rating to be corrected. This remains available after reaching the end of the queue. |
| ▶️ Play | Start or resume playback. |
| ⏸️ Pause | Pause playback while retaining the current position. |
| ⏹️ Stop | Stop playback and reset the current song to its beginning. |
| ⏭️ Skip | Move to the next song in the saved queue. |
| 👍 Like | Set the current song's rating to the configured Like rating (1–5 stars, default 2), without advancing to the next song. |
| 👎 Dislike | Set the current song's rating to the configured Dislike rating (1–5 stars, default 1) and advance to the next song. On the last song, save the rating and stop playback. |
| 🔄 Sync | Start synchronization. |
| ⚙️ Settings | Open server settings. |

The current rating should be indicated by highlighting the corresponding rating button. Changing a rating replaces the previous local rating; the latest value is uploaded during synchronization.

## Screens

### Player

- Offer an optional rating reminder, disabled by default: mix a 600 ms two-tone reminder into the music audio when an unrated song has 20 seconds remaining, including during background playback, without pausing music. Use the same audio stream, media volume, and output route as the song, including Bluetooth. Briefly lower the music under each tone without pausing playback. Confirm the reminder only when its PCM samples have been mixed. Play once per listening session, preserve this state across pauses and app restarts, and reset it when restarting the song from the beginning. Songs lasting 20 seconds or less do not trigger the reminder.
- Display information about the current song without the playlist count, fixed-shuffle hint, or pending-rating count.
- Keep the song title, artist, and album in one line each. Automatically scroll overflowing text horizontally, pausing for five seconds initially and between repetitions, and reserve the album line even when it is missing, so playback controls keep the same vertical position between songs.
- Adapt the Music cover art height from 180 to 280 dp to use spare vertical space on taller screens and move playback controls lower. Keep the Podcast cover at 140 dp and preserve scrolling on compact screens.
- Display embedded cover art from the local audio file in place of the large music note when available. Use the music note when cover art is absent or unreadable; cover art must work offline.
- Provide Previous, Play/Pause, Stop, Skip, Like, and Dislike controls.
- Indicate the current song's rating.
- Provide access to synchronization and settings.

### Podcast

- Provide a separate Podcast screen with the configured number of oldest or newest available episodes from the selected Airsonic podcast, ordered by publication date with no shuffle.
- Only episodes already downloaded on the server, with a valid `streamId`, can be copied to the phone. Download audio using `streamId`, not the podcast episode ID.
- During sync, cache server artwork for the retained episodes, preferring each episode's `coverArt` ID over the channel's. Fetch shared images once per sync using `getCoverArt` with a requested size of 512 pixels, refresh them on later syncs, and remove unreferenced images only after successful sync. Artwork failures must not fail audio synchronization or overwrite a valid cached image; cancellation must remove partial image downloads.
- Display cached artwork offline in the Podcast player. Fall back to embedded audio artwork and then the music note when a server image is missing or unreadable.
- Include already-heard episodes in the selected episodes; hearing an episode does not automatically replace or remove it.
- Provide offline Play/Pause, Previous, Stop, Next, seeking, and an episode list with publication dates and heard status. Background playback and headset controls use the same media session as music.
- Mark an episode heard when it reaches the end naturally, including transitions and the final episode. Next and Stop do not mark it heard. Users may set or clear the mark manually.
- Heard marks are local and persist across restarts and synchronization. Do not upload podcast ratings or Last.fm scrobbles, and do not play rating reminders for podcasts.
- Preserve separate current-item and position bookmarks for music and podcasts. Viewing a different screen does not interrupt playback; pressing Play on the other queue switches playback.
- After successful sync, remove episodes deleted on the server or no longer among the selected episodes. Failed sync preserves both queues and existing files. Never autoplay after synchronization.

### Synchronization status

- Show synchronization progress, including rating uploads and song downloads.
- Disable the Music and Podcast tabs while a manual sync is queued or any sync is running. If either playback screen is open, switch to Sync. Re-enable both tabs when the run completes, fails, or is cancelled. An automatic run waiting for its conditions does not lock playback tabs.
- During downloads, show overall progress as completed downloads divided by the number of songs requiring download. Exclude rated songs and existing nonempty local files; increment progress only after a file is downloaded successfully.
- Show completion status and errors.

### Settings

- Airsonic server URL.
- Username.
- Password.

## Local data

Persist the following on the phone:

- Downloaded audio and the song information needed for offline playback.
- The fixed shuffled music queue order.
- The chronological podcast episode list, local heard marks, selected episode, and podcast playback position.
- The current song and playback position.
- Local ratings and pending rating changes awaiting server confirmation.
- Pending listening events and the active session, including start time and actual playback duration.
- Server connection settings.

## Acceptance criteria

1. After a successful synchronization, songs from the selected playlist can be played with internet access disabled.
2. Like assigns its configured rating without changing songs; Dislike assigns its configured rating and advances, or stops if the song is last.
3. Previous allows the user to return to a song and replace its rating.
4. Reopening the app restores the same queue, current song, and playback position.
5. Stop resets the current song to the beginning; Pause preserves its position.
6. Playback stops at the end of the queue without repeating.
7. Playback works with the screen off, and notification and headset controls are supported.
8. Synchronization uploads pending local ratings, overriding conflicting server ratings.
9. Successful synchronization excludes songs with any nonzero rating from downloads and the refreshed queue, and removes their local copies along with songs removed from the selected playlist. The server playlist is unchanged. Rating a song alone does not immediately remove it from the current queue.
10. Successful synchronization creates a newly shuffled music queue and resets music listening progress without starting playback.
11. Podcast synchronization selects the configured number of oldest or newest available episodes from the selected channel, includes heard episodes, and downloads audio using episode `streamId`.
12. Podcast playback is chronological, works offline, and preserves its bookmark independently of music.
13. Natural completion marks an episode heard; Next and Stop do not. Manual heard marks persist across restarts.
14. Successful sync removes stale podcast files, while failures preserve both existing queues and their files.
11. Failed synchronization preserves listening progress and the existing queue order; unconfirmed rating changes remain available for retry.
12. Natural completion produces one listening event when duration and actual-playback requirements are met; skipping or seeking to the end does not.
13. Automatic transitions and the final song's completion both record listens with the original start timestamp.
14. Repeated completed listens produce separate events. Pending events survive restarts and are uploaded in timestamp order before playlist refresh and local song removal.
15. Failed scrobble uploads preserve pending events, the queue, and playback position. Confirmed events are not resubmitted during subsequent normal syncs.
