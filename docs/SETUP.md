# Campus Sync — Firebase setup

Do these steps once in the Firebase console. The project already contains
`app/google-services.json`, so no Firebase SDK configuration is needed in code.

## Pending Firebase steps (Ghost Mode)

The Ghost Mode feature is built into the app, but the Firebase project still needs
these owner actions:

- [ ] **Republish `firestore.rules`** (step 6). The new rules add
  `ghostIdentities`, `ghostAliases`, `ghostLimits`, `ghostReports`, and
  `revealLogs` blocks.
- [ ] **Create the `revealLogs` composite index** (step 7): `boardId` ascending,
  `createdAt` descending.
- [ ] **Do not create the new collections by hand.** The app creates
  `ghostIdentities`, `ghostAliases`, `ghostLimits`, `ghostReports`, and
  `revealLogs` automatically when they are first needed. They appear in the
  console after the first anonymous post, report, or reveal.
- [ ] **Keep at least one board moderator** in `moderatorIds` (step 4). Without a
  moderator, nobody can read ghost identities, restore hidden items, or use
  **Reveal author**.

## 1. Turn on email/password sign-in

1. Open **Firebase Console → Authentication**.
2. Open the **Sign-in method** tab.
3. Enable **Email/Password** and save.

## 2. Create Firestore

1. Open **Firebase Console → Firestore Database**.
2. Create a database in production mode.
3. Pick a location close to your campus and create it.

Campus Sync uses the Spark plan and does not use Cloud Functions or Firebase
Storage. Do not enable either.

## 3. Add college documents

Students pick a college from this collection during sign-up.

1. Open **Firestore Database → Data**.
2. Click **Start collection** and use the collection ID `colleges`.
3. For the document ID, type a short lowercase ID such as `demo-university`.
   This ID is what the app stores as `collegeId`.
4. Add exactly these fields:

| Field | Type | Example value |
|---|---|---|
| `name` | string | `Demo University` |
| `emailDomains` | array | `youruniversity.edu` |
| `createdAt` | timestamp | the current date and time |

For `emailDomains`, add one array element per allowed domain. The value must be
the part **after the @** in the student's email, in lowercase, with no `@` and no
wildcards. For example:

- `ananya@youruniversity.edu` → add `youruniversity.edu`
- `rahul@students.youruniversity.edu` → add `students.youruniversity.edu`

A sample document looks like this:

```text
Collection: colleges
Document ID: demo-university

name:          "Demo University"
emailDomains:  ["youruniversity.edu", "students.youruniversity.edu"]
createdAt:     November 23, 2026 at 9:00:00 PM UTC+5:30
```

Add one document for every college. The app shows them alphabetically.

The domains are not just a hint for the picker: when a student finishes
onboarding, rules check that the email address on the verified account belongs to
the chosen college's `emailDomains`. A profile cannot be created for a college the
student's email does not match.

> **Testing tip:** add a test college whose `emailDomains` contains the domain of
> an email address you can actually open, so you can receive the verification
> email. Firebase must be able to send email to that address.

Do **not** create `users` or `usernames` documents by hand. The app creates them
during onboarding and when usernames change.

## 4. Create message boards

Boards are console data; the app cannot create them. Every board belongs to one
college.

1. Open **Firestore Database → Data**.
2. Click **Start collection** and use the collection ID `boards`.
3. Use a short lowercase document ID such as `general`.
4. Add these fields:

| Field | Type | Example value |
|---|---|---|
| `collegeId` | string | `demo-university` (must match the `/colleges` document ID exactly) |
| `name` | string | `General` |
| `description` | string | `Anything campus-related.` |
| `moderatorIds` | array | your UID, or `[]` when nobody should moderate |
| `createdAt` | timestamp | the current date and time |

5. Add one document per board. A useful starter set:

| Document ID | `name` |
|---|---|
| `general` | `General` |
| `hostel` | `Hostel` |
| `placements` | `Placements` |
| `memes` | `Memes` |

6. To make someone a moderator, open **Authentication → Users**, copy their UID,
   and add it to `moderatorIds`. Moderators can pin any thread, delete any thread
   or comment, restore hidden anonymous items, and reveal anonymous authors on
   that board. Leave the array empty if nobody should.

Students see only boards whose `collegeId` matches their profile. Do **not** create
threads or comments in the console; the app writes those.

## 5. Expire stories without Cloud Functions

Stories last 24 hours. The Spark plan does not include Cloud Functions, so the
project uses Firestore's built-in TTL (time-to-live) policy instead of a
scheduled function. The app already hides expired stories; TTL deletes them.

1. Open **Firestore Database**.
2. Open the **Time-to-live** tab (in some console versions: **TTL**).
3. Click **Create policy**.
4. Collection group: `stories`.
5. Timestamp field: `expiresAt`.
6. Create the policy.

That is the whole cleanup job. Firestore deletes each story shortly after its
`expiresAt` timestamp, with no function, server, or extra billing. Expired
stories are also filtered out on the client, so students never see them even
before the deletion runs.

Add one more policy for queued push jobs so an undrained queue cannot grow:

1. Click **Create policy** again.
2. Collection group: `pushQueue`.
3. Timestamp field: `expireAt`.
4. Create the policy.

The push relay from step 8 deletes the jobs it delivers; this policy removes the
ones it never got to.

## 6. Publish the security rules

1. Open **Firestore Database → Rules**.
2. Replace the editor contents with the contents of the project's
   `firestore.rules` file.
3. Click **Publish**.

The board rules are included: students read boards for their college, write
threads and comments, vote only on their own behalf, and moderators alone can
pin. Boards themselves remain console-only.

Ghost Mode rules are included too. Anonymous threads and comments are stored
without `authorId` or `authorName`; the real UID is in a private
`ghostIdentities` document that only board moderators can read. The daily limit
counter can only go up by one and never above five, and reveal actions must
write a `revealLogs` audit record with a reason before the identity is read.

Colleges are readable by everyone on purpose: the sign-up screen has to show the
college list before the student has an account. Nobody can write colleges from
the app; you add them in the console.

The rules also require a **verified email** for every other collection. An account
that has not opened the verification link cannot read or write profiles, posts, or
chats even if a modified client skips the verification screen. See
[`docs/SECURITY.md`](SECURITY.md) for the full model.

## 7. Create the composite indexes

The feed, story, chat, and board queries need eight composite indexes. You can
either let the app create them on first use (Firebase logs a link in Logcat) or
create them now.

From the project root, with the Firebase CLI installed and logged in:

```bash
firebase deploy --only firestore:indexes
```

Or create them by hand in **Firestore Database → Indexes → Composite**:

| Collection | Fields |
|---|---|
| `posts` | `collegeId` ascending, `createdAt` descending |
| `stories` | `collegeId` ascending, `createdAt` descending |
| `stories` | `collegeId` ascending, `authorId` ascending, `createdAt` ascending |
| `chats` | `members` array-contains, `lastMessageAt` descending |
| `boards` | `collegeId` ascending, `name` ascending |
| `threads` | `collegeId` ascending, `pinned` descending, `createdAt` descending |
| `threads` | `collegeId` ascending, `score` descending, `createdAt` descending |
| `revealLogs` | `boardId` ascending, `createdAt` descending |

Index builds take a few minutes. Until they finish, the feed, story bar, or chat
inbox shows an error with a retry action and Logcat prints the console link.

## 8. Message notifications without Cloud Functions

Chat messages arrive in real time from Firestore without notifications. Push is
optional: the Spark plan cannot run Cloud Functions, and no Android app can send
an FCM message because that needs a trusted credential.

The project works around that with a small queue and a relay you own:

1. The app writes a job to `/pushQueue` next to each message it sends.
2. A relay (Firebase Admin SDK, run by any free scheduler) drains the queue and
   sends data-only FCM messages to the other members' devices.
3. The app receives them, skips muted chats, and opens the conversation on tap.

The client cannot read `/pushQueue`; only the relay can, with credentials that
never enter the app or this repository. Setup, cost, and limits are in
[`docs/PUSH.md`](PUSH.md): enable the TTL policy from step 5, create a service
account, store its JSON as the `FIREBASE_SERVICE_ACCOUNT` secret, and enable
`.github/workflows/push-relay.yml`. The service account needs **Cloud Datastore
User** and **Firebase Cloud Messaging API Admin**; neither the app nor this
repository ever holds the key. The relay reads notification copy from the message
itself, so the queue cannot forge it.

Without the relay nothing is pushed and the TTL policy clears the jobs. Realtime
messages still work normally.

To check the receive side without the relay, copy a device `token` from
`/users/{uid}/devices` in the console and send a **data-only** test message from
**Firebase Console → Messaging** with the keys `chatId`, `senderName`, and
`body`. A notification block would be displayed by Android itself, so the app
could not skip muted chats.

Firebase 25.1 deprecated registration tokens in favour of Firebase Installation
IDs; tokens still work and are accepted by both the relay and the console.

## 9. Test the flow

1. Run the app.
2. Tap **Create account**.
3. Enter a full name, a college email at an allowed domain, a password of at
   least 6 characters, and pick the matching college.
4. Check the email inbox and open the verification link.
5. Return to the app and tap **I verified, continue**.
6. Finish onboarding: username, department, year, graduation year, optional bio,
   skills, and interests.
7. Open the **Home** tab and tap **Share something with your college**.
8. Add text and up to four public image links, then tap **Post**.
9. Like the post, open it, comment, and reply to your own comment.
10. Add a story from the story bar, then open it and check the progress bars.
11. Open a student profile from a post and try **Block** and **Report**.
12. Sign in with a second account in the same college to test likes, comments,
    story "Seen by", and that blocked students disappear from the feed.
13. Open **Messages** from the feed header or the Profile tab. Pick a classmate
    and start a chat. Without a follow edge it arrives as a message request:
    accept it on the other account, then send messages both ways.
14. Follow a classmate from their profile first, then tap **Message**: the chat
    opens immediately. Check the typing indicator, the "Read" label, and that a
    chat with no new messages loses its unread dot.
15. Create a group with a name and two classmates. Check admin rename, add and
    remove member, leave group, and mute (the mute icon appears in the inbox).
16. Share an image by pasting a public `https://` link in the chat composer.
17. Optional: deploy the relay from `docs/PUSH.md`, then send a message from the
    second account and check that the notification arrives and opens the chat.
18. Tap the **boards** icon in the Home header. Open a board you created.
19. Tap **New thread**, add a title and body, and post it. The new thread opens.
20. Upvote and downvote the thread and a comment. The score moves by one, and
    pressing the same arrow again removes your vote. Switch from up to down on
    one item and check that the score moves by two.
21. Reply to a comment, then reply to that reply (up to three levels). Delete your
    own thread. From a moderator account, pin another thread and check it stays
    first under **New**; try pinning from a normal account and confirm the pin
    action is not available.
22. Report a thread or comment and block its author. The content disappears from
    the board for that account.
23. Tap **New thread**, turn on **Post anonymously**, and post. The thread shows a
    random alias with a ghost icon and **OP**, and tapping the alias does not open
    a profile. Reload and check the alias stays the same.
24. Add an anonymous comment in the same thread; it reuses the same alias. From a
    second account, add another anonymous comment and confirm it has a different
    alias.
25. From three different accounts, report the anonymous thread. Normal accounts
    lose it; a moderator sees **Hidden pending review** with **Restore** and
    **Delete**. Restore it and confirm it returns for normal accounts.
26. As a moderator, tap **Reveal author** on an anonymous item, type a reason, and
    confirm the name and username appear. Check that a `revealLogs` document was
    written in the console.
27. Make six anonymous posts or comments in one UTC day. The sixth is blocked with
    the daily-limit message.

## Troubleshooting

- **"No colleges are available yet"** — add a document to `colleges`, then tap
  Refresh.
- **"Use your ... email address"** — the domain after the @ is not in that
  college's `emailDomains` array. Check spelling and lowercase.
- **Permission denied** — publish the latest `firestore.rules`. The account's
  email must be verified (the app refreshes its token after verification), and the
  student's `/users/{uid}` document must exist before viewing the feed or other
  profiles. A profile also requires the email domain to match the chosen college.
- **Feed fails with "The query requires an index"** — create the composite
  indexes from step 7, or open the link in the error. This is expected on a new
  project.
- **Images do not load** — the app stores image links, not uploads. Use a public
  `https://` link that returns an image file.
- **A story stays visible after 24 hours** — check that the TTL policy from
  step 5 exists on `stories.expiresAt`, and that the device clock is correct.
- **Reports** — the app cannot read `/reports`. Open the collection in the
  Firebase console to review them.
- **Chat inbox fails with "The query requires an index"** — create the `chats`
  index from step 7. Firestore needs it for `members` + `lastMessageAt`.
- **Boards are empty, or threads fail with "The query requires an index"** — add
  board documents whose `collegeId` matches the student's college (step 4) and
  create the three board indexes from step 7. A student sees only boards for
  their own college, so a mismatched `collegeId` looks like an empty list.
- **Anonymous posting says the daily limit is reached** — the limit is five
  anonymous threads and comments per UTC day. Wait for the next UTC day, or use a
  non-anonymous post.
- **Reveal author fails** — the account must be in that board's `moderatorIds`,
  and the item must still have its private `ghostIdentities` document. Publish
  the latest rules and check the board document.
- **A reported anonymous item disappeared** — that is expected once three
  students report it. A board moderator can open it from the board list, choose
  **Restore**, or delete it.
- **No push notification** — the relay is not deployed or not running, or the
  queue TTL removed the job. Check the relay log and `/pushQueue` in the console;
  realtime messages still work. Also check the notification permission, that the
  device token exists, and that the chat is not muted. Notifications are skipped
  when the signed-in student is no longer a member of the chat.
- **A chat shows "Message request sent"** — the other student does not follow you
  and you do not follow them. They accept it from the requests section of their
  Messages screen.
- **No verification email** — check spam, then use **Resend email** on the verify
  screen.
- **Username already taken** — usernames are unique and stored in
  `/usernames/{username}`. Pick another one.
