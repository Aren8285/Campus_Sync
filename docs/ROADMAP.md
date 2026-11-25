# Campus Sync — Roadmap

Update this checklist in the same change as the work it describes. Keep completed items
ticked and add new work here instead of leaving TODOs in code.

## Iteration 0 — Project skeleton

- [x] Gradle: Google Services plugin, Hilt with KSP, version catalog entries
- [x] Firebase BoM with Auth, Firestore, and Analytics
- [x] Navigation Compose, Hilt Navigation Compose, Lifecycle ViewModel Compose
- [x] Coil, DataStore Preferences, material-icons-extended
- [x] Package layout: `core`, `data`, `domain`, and 12 feature packages
- [x] Placeholder Screen + ViewModel for every feature package
- [x] Hilt Application class and single `MainActivity`
- [x] Material 3 light/dark theme
- [x] Bottom navigation: Home, Events, Learn, Market, Profile
- [x] Shared `UiState`, `LoadingView`, `ErrorView`, `EmptyView`
- [x] Hilt module providing `FirebaseAuth` and `FirebaseFirestore`
- [x] Starter `firestore.rules` (own `/users/{uid}` document only)
- [x] `AGENTS.md`, roadmap, and `.gitignore` updates

## Iteration 1 — Authentication, college selection, and profiles

- [x] Welcome, sign-up, login, and forgot-password screens
- [x] College list loaded from Firestore with a sign-up dropdown
- [x] College email-domain validation against the selected college
- [x] Email verification email, resend, and verification gate
- [x] Session gate: welcome, verify, onboarding, or main tabs
- [x] Auth repository with friendly Firebase error messages
- [x] Onboarding: username, department, year, graduation year, bio, skills, interests
- [x] Unique usernames via `/usernames/{username}` in the same batch as the profile
- [x] Profile tab with initials avatar, edit screen, public profile, and logout
- [x] Optional profile photo through a public `https://` link, with initials as
      the loading/failure fallback
- [x] 300-character bio cap enforced in the form and in rules
- [x] Firestore rules for colleges, users, and username reservations
- [x] `docs/SETUP.md` and `docs/DATA_MODEL.md`
- [ ] Automated tests for auth, repositories, and validation (follow-up)

## Iteration 2 — Feed, stories, and safety

- [x] Create post with text and up to four external image links
- [x] College-scoped, paginated feed with pull-to-refresh and "Load more"
- [x] Like/unlike and delete-own-post actions
- [x] Comments with one-level threaded replies and a comment count
- [x] Share post through the Android share sheet
- [x] Stories with text or image, 24-hour `expiresAt`, and client filtering
- [x] Story viewer with progress bars and a "Seen by" count
- [x] Story cleanup through a Firestore TTL policy on `stories.expiresAt`
      (no Cloud Functions on the Spark plan)
- [x] Block and report student, post, and comment actions
- [x] `/reports` as write-only moderation reports
- [x] Firestore rules and composite indexes for `/posts`, comments, `/stories`,
      `/blocks`, and `/reports`
- [ ] Automated tests for pagination, comment threading, and story expiry
      (follow-up)

## Iteration 3 — Events

- [ ] Campus event list with date filters
- [ ] Event details and RSVP (going/interested)
- [ ] Rules and indexes for `/events` and `/events/{id}/attendees`

## Iteration 4 — Learn (knowledge)

- [ ] Course/major resource posts with text and external links
- [ ] Search and tag filters
- [ ] Rules and indexes for `/resources`

## Iteration 5 — Market

- [ ] Buy/sell listings with price, condition, and contact method
- [ ] Mark as sold and delete own listing
- [ ] Rules and indexes for `/listings`

## Iteration 6 — Chat, messaging, and notifications

- [x] One-to-one conversations with realtime messages and last-message previews
- [x] Unread dots and per-chat mute
- [x] Group chats: create, rename, add/remove members, leave, and admin roles
- [x] Read receipts and typing indicators
- [x] Image sharing through external `https://` links
- [x] Message requests for students you do not follow, with accept and decline
- [x] Follow graph (`/follows`) as the known-contact signal for messaging
- [x] FCM registration, notification channel, and notification tap that opens the chat
- [x] Rules and indexes for `/chats`, messages, typing, `/follows`, and devices
- [x] Push sending without Cloud Functions: `/pushQueue` jobs plus the optional
      owner-deployed relay in `docs/PUSH.md` and `tools/push-relay`
- [ ] Deploy the push relay (owner action; chat works without it)
- [ ] Pagination for chat history older than the newest 100 messages (follow-up)
- [ ] Migrate FCM registration tokens to Firebase Installation IDs when a sender
      that targets them exists (follow-up)
- [ ] Remove the device token from the previous account on logout, for shared
      devices (follow-up; the receive side already checks membership)

## Iteration 7 — Boards and Collab

- [ ] Course/community boards with threads
- [ ] Study partner and project collaboration posts
- [ ] Rules and indexes for board and collab collections

## Iteration 8 — Safety, alumni, and settings

- [x] Block/report flows (delivered with the feed in Iteration 2)
- [ ] Blocked-student management screen and privacy controls
- [ ] Campus safety resources and emergency contacts
- [ ] Alumni directory and mentorship opt-in
- [ ] Notification preferences (DataStore) and account deletion request

## Security hardening (November 2026)

- [x] Verified-email requirement in rules, plus an ID-token refresh after verification
- [x] College email-domain check when a profile is created
- [x] Follow graph readable only by the students on each edge
- [x] `https://`-only media in rules, repositories, and the image loader
- [x] Push jobs bound to the sender's real message; relay copies notification text
      from the message instead of the queue
- [x] Backups and device transfer disabled; lock-screen notification text hidden
- [x] Chat IDs from notification intents validated before use
- [x] `docs/SECURITY.md` with the threat model, known limits, and owner checklist
- [ ] Firebase App Check (Play Integrity) for Firestore and FCM
- [ ] Firestore rules unit tests in the emulator
- [ ] Rate limiting or abuse controls for posts, follows, reports, and requests
- [ ] Verify group membership by college (needs a rules-friendly design; up to 50
      `get()` calls would exceed rule limits)

## Later / blocked

- [ ] Avatar images. The app shows initials for now because the project does not
      use Firebase Storage. Revisit when an approved image host or Storage plan exists.
- [ ] Image uploads for posts and stories. Until then they accept public image
      links, which the product constraints allow.
- [ ] Scheduled cleanup for old data. Stories already use a Firestore TTL policy;
      other clean-up work would need an approved server option.
