# Campus Sync — Security model

What protects student data, what the app does not protect, and what an owner still
has to configure. Last reviewed: November 24, 2026.

The rules, not the UI, are the security boundary. Every statement below describes
what `firestore.rules` enforces or what the client does in addition.

## Trust boundaries

| Boundary | Control |
|---|---|
| Signed-out visitor | `/colleges` is public so sign-up can list colleges. Everything else requires an account. |
| Unverified account | **No Firestore access.** `isSignedIn()` requires `request.auth.token.email_verified == true`, so an account created with someone else's address cannot write until the link is opened. |
| Verified student, no profile | Can read `/usernames` (uniqueness check) and `/colleges`. Cannot read feeds, profiles, or chats until the profile exists. |
| College membership | A profile can only be created with the college whose `emailDomains` contains the account's email domain. `collegeId` is immutable afterwards. |
| Same-college student | Can read profiles, posts, comments, and stories in that college. Cannot read another college's content. |
| Chat member | The only readers and writers of `/chats/{id}` and its messages. Membership is checked with `get()` on the parent chat. |
| Owner (device tokens) | Only the owner can read, write, or delete `/users/{uid}/devices`. |
| Relay (server) | Uses a service account, bypasses rules, and is the only reader of `/pushQueue` and other students' device tokens. Credentials never enter the app or the repository. |

## What the rules enforce

- **Verification and college binding.** Profile creation requires a verified
  email, and the email's domain must be in the chosen college's `emailDomains`
  list. This closes the gap where the college picker was only validated on the
  device. Direct chats additionally require both profiles to share a college.
- **No cross-college reads.** Profiles, posts, comments, stories, blocks, and
  chats are scoped by `collegeId` or `members`. Direct chats also verify that the
  other student's profile is in the caller's college. The follow graph is the exception:
  each student can read only edges they are part of, and chat creation checks both
  directions with `exists()`, which does not require read access.
- **Immutable media fields.** Stories and chat messages accept only `https://`
  links (text stories carry none), and messages are either text or an image, never
  both. Cleartext links are rejected at the database, not just hidden in the UI.
- **Owner-only mutations.** Likes change only the caller's UID, `seenBy` only adds
  the caller, `readAt`/`mutedBy` only change the caller's entry, and profile edits
  cannot touch `collegeId`, `role`, `karma`, `badges`, or `createdAt`.
- **Message requests cannot be bypassed.** An accepted direct chat requires a
  follow edge in either direction; a pending request must be created by its
  sender, and only the recipient can accept it.
- **Push queue integrity.** `pushQueue` is write-only for clients. A job's ID must
  be the ID of a message the caller actually sent in that chat, so the queue cannot
  be used to notify a chat without a message. The relay reads notification copy
  from the message, so the queue document cannot carry forged text.
- **Immutable messages.** Chat messages and typing markers have their own
  ownership checks; messages can never be edited or deleted from a client.
- **Reports are write-only.** Students can submit them; only the console can read.

## Client hardening

- **Backups disabled.** `android:allowBackup="false"` plus exclusion rules for
  backup and device transfer, so a signed-in session cannot be restored onto
  another device.
- **Lock-screen privacy.** Chat notifications use `VISIBILITY_PRIVATE` with a
  generic public version, so message text is not shown on a secure lock screen.
- **Only https images.** The shared image loader refuses anything but `https://`,
  and post, story, and message composers validate the scheme before writing.
- **No cleartext traffic.** The app does not opt into cleartext HTTP; Android's
  default network security policy blocks it.
- **Validated navigation input.** A chat ID arriving from a notification intent is
  checked against a strict pattern before it becomes a Firestore path.
- **No secrets in the app.** The FCM sender is the owner-run relay. There is no
  service-account key, signing secret, or server credential in the APK.

## Known limitations

These are accepted for the current Spark-plan design, not oversights:

- **No App Check.** A modified client with a valid student account can call the
  Firebase APIs directly within the rules above. App Check with Play Integrity
  would raise the cost of scripting abuse; it needs a dependency and console setup,
  so it is a follow-up rather than part of this pass.
- **No server-side rate limiting.** A verified student can create posts, comments,
  follows, reports, or message requests as fast as Spark quotas allow. Rules can
  bound shape and ownership, not frequency.
- **`commentCount` can be inflated.** Rules allow a comment batch to increment by
  one; they cannot require that a comment document was written in the same batch.
- **Group membership is UID-addressed.** Rules verify the college for a direct
  chat (one read) but not for every member added to a group: up to 50 `get()` calls
  would exceed rule limits. Clients refuse to add another college's students, but
  for groups that is a UX guard, not enforcement. A UID must be known to add one.
- **Reports are unmoderated.** They are write-only and reviewed manually in the
  console.
- **Rules are not unit-tested.** They were reviewed by hand; the Firebase emulator
  suite is a follow-up.

## Owner checklist

1. Publish `firestore.rules` (Setup step 5). The old rules trust the client's
   college choice and unverified accounts.
2. Add TTL policies for `stories.expiresAt` and `pushQueue.expireAt`.
3. Restrict the Firebase API key in Google Cloud to the Android package name and
   the release SHA-1/SHA-256 fingerprints. `google-services.json` is public by
   design, so the key restriction is what stops other apps from using it.
4. Give the relay service account the smallest useful roles: **Cloud Datastore
   User** and **Firebase Cloud Messaging API Admin**. Do not use an Editor key.
5. Enable App Check once the app ships a Play Integrity build (recommended, not
   required today).
6. Do not commit `google-services.json`, `local.properties`, service-account JSON,
   keystores, or `tools/push-relay/node_modules`.
