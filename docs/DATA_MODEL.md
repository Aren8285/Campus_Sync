# Campus Sync — Data model

This document describes the Firestore collections used by Iterations 1, 2, 6, and 7
(message boards, including Ghost Mode).

## `/colleges/{collegeId}`

Added manually in the Firebase console.

| Field | Type | Notes |
|---|---|---|
| `name` | string | Shown in the sign-up college picker. |
| `emailDomains` | array of strings | Domains allowed for that college, without `@`. |
| `createdAt` | timestamp | When the document was added. |

`collegeId` is the document ID. It is stored on the student's profile as
`collegeId` and never changes after onboarding.

Colleges are readable by everyone because the sign-up screen needs the list
before an account exists. They are never writable from the app.

Example:

```text
colleges/demo-university
  name: "Demo University"
  emailDomains: ["youruniversity.edu", "students.youruniversity.edu"]
  createdAt: <timestamp>
```

## `/users/{uid}`

Created during onboarding. The document ID is the Firebase Auth UID; there is no
separate `uid` field.

| Field | Type | Notes |
|---|---|---|
| `name` | string | Full name. Editable. |
| `username` | string | Lowercase, unique, 3–20 chars. Editable. |
| `collegeId` | string | Document ID from `/colleges`. Immutable. |
| `department` | string | Editable. |
| `year` | string | For example `2nd year` or `Graduate`. Editable. |
| `graduationYear` | number | Four-digit year. Editable. |
| `bio` | string | Optional, up to 300 characters; empty when not set. Editable. |
| `avatarUrl` | string | Optional public `https://` profile photo link, up to 500 characters. Empty shows initials. Editable. |
| `skills` | array of strings | Editable. |
| `interests` | array of strings | Editable. |
| `role` | string | Always `student`. Immutable from the client. |
| `badges` | array of strings | Starts empty. Immutable from the client. |
| `karma` | number | Starts at `0`. Immutable from the client. |
| `createdAt` | timestamp | Set by the server on creation. |

Example:

```text
users/<auth-uid>
  name: "Ananya Rao"
  username: "ananya_rao"
  collegeId: "demo-university"
  department: "Computer Science"
  year: "2nd year"
  graduationYear: 2029
  bio: "Android developer and campus event regular."
  avatarUrl: ""
  skills: ["Kotlin", "Figma", "Public speaking"]
  interests: ["Technology", "Music", "Volunteering"]
  role: "student"
  badges: []
  karma: 0
  createdAt: <server timestamp>
```

Only the owner can create the document, and only with `role = "student"`,
`karma = 0`, and `badges = []`. Creation also requires a **verified email** and a
`collegeId` whose `emailDomains` array contains the account's email domain, so the
college cannot be chosen freely from the client. The owner can update the personal
fields, but cannot change `collegeId`, `role`, `karma`, `badges`, or `createdAt`.

## `/usernames/{username}`

A reservation document that keeps usernames unique.

| Field | Type | Notes |
|---|---|---|
| `uid` | string | The user who owns the username. |

`username` is the normalized document ID (lowercase, trimmed). Signing up creates
the user document and the username reservation in one Firestore batch, so both
succeed or both fail. Changing a username updates the user document, creates the
new reservation, and deletes the old one in the same batch.

## `/posts/{postId}`

A feed post. The feed query is
`where collegeId == my college, order by createdAt desc, limit 10`, paged with
`startAfter(createdAt)`.

| Field | Type | Notes |
|---|---|---|
| `authorId` | string | Firebase Auth UID. Immutable. |
| `authorName` | string | Copied from the author's profile so the feed needs no extra reads. |
| `authorUsername` | string | Copied from the author's profile. |
| `collegeId` | string | Author's college; the feed is scoped to it. |
| `text` | string | Up to 1000 characters. May be empty when images exist. |
| `imageUrls` | array of strings | Up to 4 public image links. Files are never uploaded. |
| `likedBy` | array of strings | UIDs that liked the post. The like count is `likedBy.size()`. |
| `commentCount` | number | Denormalized count, incremented by one in the comment batch. |
| `createdAt` | timestamp | Server timestamp. |

Example:

```text
posts/<post-id>
  authorId: "<auth-uid>"
  authorName: "Ananya Rao"
  authorUsername: "ananya_rao"
  collegeId: "demo-university"
  text: "Study group for data structures this Thursday at 6pm in the library."
  imageUrls: ["https://example.com/library.jpg"]
  likedBy: ["<other-uid>"]
  commentCount: 2
  createdAt: <server timestamp>
```

Rules ensure the author fields match the caller's own profile, likes can only
add or remove the caller's own UID, and only the author can edit or delete.

## `/posts/{postId}/comments/{commentId}`

Threaded comments, one level deep.

| Field | Type | Notes |
|---|---|---|
| `authorId` | string | Firebase Auth UID. |
| `authorName` | string | Copied from the author's profile. |
| `authorUsername` | string | Copied from the author's profile. |
| `text` | string | 1–500 characters. |
| `parentId` | string | Empty for a top-level comment, otherwise the top-level comment ID. |
| `createdAt` | timestamp | Server timestamp. |

A reply always points at a top-level comment, so the UI renders two levels. A
reply to a reply is attached to the same top-level comment.

Adding a comment writes the comment and increments the post's `commentCount` in
one batch. Deleting a post does not cascade: its subcollection documents become
unreachable because the rules require the parent post to exist.

## `/stories/{storyId}`

A 24-hour story. The story bar query is
`where collegeId == my college, order by createdAt desc, limit 50`.

| Field | Type | Notes |
|---|---|---|
| `authorId` | string | Firebase Auth UID. |
| `authorName` | string | Copied from the author's profile. |
| `authorUsername` | string | Copied from the author's profile. |
| `collegeId` | string | Author's college. |
| `type` | string | `text` or `image`. |
| `text` | string | Up to 200 characters. Required for text stories, optional caption for image stories. |
| `imageUrl` | string | Public `https://` link; empty for text stories. |
| `seenBy` | array of strings | UIDs that viewed the story. The size is the "Seen by" count. |
| `expiresAt` | timestamp | Set to now + 24 hours when created. |
| `createdAt` | timestamp | Server timestamp. |

### How expiry works without Cloud Functions

The Spark plan does not allow Cloud Functions, so there is no scheduled cleanup
job. Expiry uses two mechanisms instead:

1. **Client filtering.** The app only shows stories whose `expiresAt` is in the
   future, both in the story bar and in the viewer.
2. **Firestore TTL policy.** Set a TTL policy on the `stories` collection with the
   `expiresAt` timestamp field. Firestore deletes expired documents automatically
   (typically within 24 hours of expiry), with no function, server, or billing.

See `docs/SETUP.md` for the two-minute console setup.

Read rules intentionally do not compare `expiresAt` with `request.time`: list
rules cannot be filtered by time, and doing so would make the whole story query
fail. Expiry is therefore enforced by the two mechanisms above.

## `/boards/{boardId}`

A message board for one college. Boards are created by the owner in the Firebase
console (see `docs/SETUP.md`); the app only reads them.

| Field | Type | Notes |
|---|---|---|
| `collegeId` | string | Must match the document ID in `/colleges`. Only that college can read the board. |
| `name` | string | Shown in the board list, sorted alphabetically. |
| `description` | string | Optional one-line explanation. |
| `moderatorIds` | array of strings | UIDs allowed to pin, delete, restore hidden anonymous items, and reveal anonymous authors on this board. May be empty. |
| `createdAt` | timestamp | When the board was added in the console. |

Suggested starter boards: `General`, `Hostel`, `Placements`, `Memes`.

Example:

```text
boards/general
  collegeId: "demo-university"
  name: "General"
  description: "Anything campus-related."
  moderatorIds: ["<auth-uid>"]
  createdAt: <timestamp>
```

Reads are limited to students in `collegeId`. Nobody can write boards from the
app; the console bypasses rules, which is how the owner manages them.

## `/boards/{boardId}/threads/{threadId}`

A discussion thread. The board query orders pinned threads first, then by
`createdAt` (New) or `score` (Top), 10 per page.

| Field | Type | Notes |
|---|---|---|
| `boardId` | string | Must equal the board document ID in the path. |
| `collegeId` | string | Author's college; the read rule is scoped to it. |
| `authorId` | string | Firebase Auth UID. Present only on normal threads; anonymous threads must omit it. |
| `authorName` | string | Copied from the author's profile. Present only on normal threads. |
| `title` | string | 1–120 characters. |
| `body` | string | Up to 2000 characters. |
| `score` | number | Always `upvotedBy.size() - downvotedBy.size()`. |
| `upvotedBy` | array of strings | UIDs that upvoted. |
| `downvotedBy` | array of strings | UIDs that downvoted. |
| `commentCount` | number | Incremented by one in the comment batch. |
| `createdAt` | timestamp | Server timestamp. |
| `pinned` | boolean | Starts `false`; only a board moderator can change it. |
| `isAnonymous` | boolean | `true` for a Ghost Mode thread, `false` for a normal one. |
| `anonAlias` | string | The alias shown instead of a name. Only present when anonymous. |
| `reportCount` | number | Anonymous reports on this thread. At 3 the app hides it from normal users. |

Normal threads require `authorId` to equal the caller and `authorName` to match
their profile. Anonymous threads must not contain either field at all; rules
reject them. The real author is stored separately in
`/ghostIdentities/{threadId}` (see below).

A student has at most one vote: they can appear in `upvotedBy` or
`downvotedBy`, never both. Voting runs in a transaction that rewrites both lists
and recomputes `score`. Rules require the score to equal the list difference and
only allow the caller's own UID to be added or removed, so a modified client
cannot change another student's vote or fake the score.

## `/boards/{boardId}/threads/{threadId}/comments/{commentId}`

A nested reply. Depth 0 is a top-level comment; replies go up to depth 3.

| Field | Type | Notes |
|---|---|---|
| `authorId` | string | Firebase Auth UID. Present only on normal comments; anonymous comments must omit it. |
| `authorName` | string | Copied from the author's profile. Present only on normal comments. |
| `body` | string | 1–500 characters. |
| `parentId` | string or null | `null` for a top-level comment, otherwise the parent comment ID. |
| `depth` | number | `0`–`3`. Must be the parent's depth plus one. |
| `score` | number | Always `upvotedBy.size() - downvotedBy.size()`. |
| `upvotedBy` | array of strings | UIDs that upvoted. |
| `downvotedBy` | array of strings | UIDs that downvoted. |
| `createdAt` | timestamp | Server timestamp. |
| `isAnonymous` | boolean | `true` for a Ghost Mode comment. |
| `anonAlias` | string | Alias shown instead of a name. Only present when anonymous. |
| `reportCount` | number | Anonymous reports on this comment. At 3 the app hides it from normal users. |

Anonymous comments omit `authorId` and `authorName`; the private identity is
`/ghostIdentities/{commentId}`. The alias is reused from
`/ghostAliases/{threadId}_{uid}`, so a student keeps one alias per thread.

Rules check that a reply's parent exists in the same thread and is exactly one
level shallower, so the depth field cannot be faked. Adding a comment writes the
comment and increments the thread's `commentCount` in one batch.

Deleting a comment with replies is blocked in the repository so replies are not
orphaned; delete the replies first. Deleting a thread leaves its comments
unreachable because the read rule requires the thread to exist.

## Ghost Mode records

Ghost Mode keeps the public thread or comment authorless and stores the private
link in separate documents. The app writes the public item and its private
records in one Firestore batch. Rules can enforce the shape and access of each
document, but they cannot read another document written in the same batch, so
the pairing itself is a client responsibility (see the limitations at the end).

### `/ghostIdentities/{itemId}`

The private link between an anonymous item and its real author. The document ID
is the thread ID for an anonymous thread and the comment ID for an anonymous
comment.

| Field | Type | Notes |
|---|---|---|
| `uid` | string | Real author's Firebase Auth UID. |
| `collegeId` | string | Author's college; must match the caller. |
| `boardId` | string | Board that contains the item. |
| `threadId` | string | The containing thread; equal to `itemId` for a thread. |
| `type` | string | `thread` or `comment`. |
| `createdAt` | timestamp | Server timestamp, written with the public item. |

Readable only by a moderator of `boardId`. The author cannot read their own
identity document; rules still use it to prove ownership for deletes. The app
deletes it in the same batch as the public item. If it is missing, only a
moderator can delete the orphaned public item.

### `/ghostAliases/{threadId}_{uid}`

One alias per student per thread, so anonymous comments in the same thread keep
the same name.

| Field | Type | Notes |
|---|---|---|
| `uid` | string | Must equal the caller. |
| `threadId` | string | Thread the alias belongs to. |
| `alias` | string | Random name such as `Quiet Panda 42`, 3–40 characters. |
| `createdAt` | timestamp | Server timestamp. |

Readable and creatable only by the owner; never updated or deleted by the app.
The alias is also copied onto each anonymous item because list queries cannot
join collections.

### `/ghostLimits/{uid}_{yyyyMMdd}`

The daily anonymous allowance: at most five anonymous threads and comments per
student per UTC day.

| Field | Type | Notes |
|---|---|---|
| `uid` | string | Must equal the caller. |
| `date` | string | UTC date as `yyyyMMdd`. |
| `count` | number | Starts at 1; each anonymous write adds exactly 1, never above 5. |
| `updatedAt` | timestamp | Server timestamp. |

Readable only by the owner. Rules allow create with `count == 1` and update
with `count == old + 1`, capped at 5. The app reads the counter and writes the
increment in the same batch as the anonymous item.

### `/boards/{boardId}/threads/{threadId}/ghostReports/{reporterUid}`

A report of an anonymous thread or comment. The document ID is the reporter's
UID, so each student can report an item once.

| Field | Type | Notes |
|---|---|---|
| `reporterId` | string | Must equal the caller. |
| `itemId` | string | Thread ID or comment ID. |
| `itemType` | string | `thread` or `comment`. |
| `reason` | string | `spam`, `harassment`, `hate_speech`, `misinformation`, or `other`. |
| `details` | string | Optional text, up to 500 characters. |
| `createdAt` | timestamp | Server timestamp. |

The reporter must be in the same college, and the reported item must be
anonymous. The app also increments the item's `reportCount` by 1 in the same
batch. At `reportCount >= 3` the item is labelled **Hidden pending review** in
moderator views and filtered out for everyone else. Reports are write-only from
the app. A moderator can restore the item (reset `reportCount` to 0) or delete
it. Restoring keeps the report documents, so the same reporters cannot report it
again.

### `/revealLogs/{logId}`

An audit record for a moderator revealing an anonymous author. The document ID
is auto-generated.

| Field | Type | Notes |
|---|---|---|
| `moderatorUid` | string | Must equal the caller. |
| `boardId` | string | Board where the reveal happened. |
| `itemId` | string | Anonymous thread or comment ID. |
| `reason` | string | Required, 1–500 characters. |
| `createdAt` | timestamp | Server timestamp. |

Create-only for a moderator of `boardId`, and only when the matching ghost
identity exists. Readable only by board moderators; never updated or deleted.
The app writes the log first and only then reads the identity and profile, so a
failed audit write reveals nothing.

## `/blocks/{blockId}`

A one-way block created by a student. Document ID is
`{blockerUid}_{blockedUid}`, which makes create/delete idempotent.

| Field | Type | Notes |
|---|---|---|
| `blockerId` | string | The student who blocked someone. Must equal the caller. |
| `blockedId` | string | The blocked student. |
| `createdAt` | timestamp | Server timestamp. |

Blocks are private: only the blocker can read them. The feed, story bar, post
detail, board thread list, and thread comments hide content from blocked students
on the client.

## `/reports/{reportId}`

A moderation report. Reports are write-only from the app; read them in the
Firebase console.

| Field | Type | Notes |
|---|---|---|
| `reporterId` | string | Must equal the caller. |
| `reportedUserId` | string | Author of the reported content. |
| `targetType` | string | `post`, `comment`, `story`, `user`, `thread`, or `board_comment`. |
| `targetId` | string | Document ID of the reported item (or the UID for a user). |
| `reason` | string | `spam`, `harassment`, `hate_speech`, `misinformation`, or `other`. |
| `details` | string | Optional text, up to 500 characters. |
| `status` | string | Always `open` on create. |
| `createdAt` | timestamp | Server timestamp. |

Named threads and board comments use `/reports` with `targetType` `thread` or
`board_comment`. Anonymous items use the separate `/ghostReports` path below so
the real author is never needed for the write.

## `/follows/{followId}`

The follow graph. Document ID is `{followerUid}_{followeeUid}`.

| Field | Type | Notes |
|---|---|---|
| `followerId` | string | Must equal the caller. |
| `followeeId` | string | The student being followed. |
| `createdAt` | timestamp | Server timestamp. |

Following is the app's "known contact" signal for messaging. A direct chat opens
immediately when either student follows the other; otherwise it starts as a
message request. Each student can read only edges they are on (as follower or
followee); rules check both directions with `exists()`, which does not need read
access.

## `/chats/{chatId}`

A one-to-one conversation or a group chat. Direct chats use the deterministic ID
`dm_{uidA}_{uidB}` with the two UIDs sorted, so a pair cannot have two
conversations. Creating a direct chat requires both students to have the same
`collegeId`; group members are checked by the client only (see
[`docs/SECURITY.md`](SECURITY.md)).

| Field | Type | Notes |
|---|---|---|
| `type` | string | `direct` or `group`. |
| `title` | string | Group name, 1–60 characters. Empty for direct chats. |
| `members` | array of strings | The whole access model: only these UIDs can read or write. Maximum 50. |
| `memberNames` | map of string to string | Display names by UID, so the inbox needs no profile reads. |
| `admins` | array of strings | Group admins. Always a subset of `members`, never empty for a group. Empty for direct chats. The creator starts as the only admin and can promote others. |
| `createdBy` | string | Creator UID. |
| `status` | string | `accepted` or `request`. |
| `requestedBy` | string | UID that started a pending request, otherwise empty. |
| `lastMessage` | map | `{senderId, senderName, type, text, at}`; `text` is a 140-character preview. |
| `lastMessageAt` | timestamp | Sorts the inbox. Set on create so a new chat appears immediately. |
| `readAt` | map of string to timestamp | Per-member read marker used for receipts and the unread dot. |
| `mutedBy` | array of strings | UIDs that muted the chat. |

The inbox query is `whereArrayContains("members", myUid),
orderBy("lastMessageAt", desc), limit 50`.

### Message requests

A direct chat starts as `status: "request"` when neither student follows the
other. Only `requestedBy` can write messages while it is pending. The recipient
accepts (status becomes `accepted`) or declines (the chat document is deleted, so
the pair can start again later; the messages under it become unreachable). Rules require a pending request to be created by
its sender and an accepted direct chat to have a follow edge in either direction,
so the request flow cannot be skipped by writing the status directly.

### Read receipts and typing

Read receipts use `readAt[myUid]`, set to the server timestamp when the student
opens the conversation or a new message arrives. A message counts as read by a
member when their marker is at or after that message's `createdAt`. The inbox
shows an unread dot when the newest message is newer than the marker.

Typing is a short-lived subcollection document per member with a single
`updatedAt` timestamp. Clients show a name for 8 seconds after the last update and
ignore stale markers. There is no Cloud Function to clean escaped markers, and
none is needed: they are ignored once stale and reused on the next update.

## `/chats/{chatId}/messages/{messageId}`

| Field | Type | Notes |
|---|---|---|
| `senderId` | string | Firebase Auth UID. |
| `senderName` | string | Copied from the sender's profile when the message is sent. |
| `type` | string | `text` or `image`. |
| `text` | string | Up to 2000 characters; empty only for image messages. |
| `imageUrl` | string | External `https://` link. Files are never uploaded. |
| `createdAt` | timestamp | Server timestamp. |

A message is exactly one kind: `text` needs non-empty `text` and an empty
`imageUrl`; `image` needs an `https://` link (the caption may be empty). Rules
reject cleartext links.

A thread loads the newest 100 messages, ordered newest first in the query and
displayed oldest first. Sending writes the message, the chat preview, and deletes
the sender's typing marker in one batch. Messages are immutable.

## `/chats/{chatId}/typing/{memberUid}`

| Field | Type | Notes |
|---|---|---|
| `updatedAt` | timestamp | Server timestamp, rewritten while the member types. |

Only the owner of the path can write it, and only members of the chat can read it.

## `/users/{uid}/devices/{registrationId}`

An FCM registration token for one device, written by the app so a trusted sender
can target it. Owner-only.

| Field | Type | Notes |
|---|---|---|
| `token` | string | FCM registration token; equal to the document ID. |
| `platform` | string | Always `android`. |
| `updatedAt` | timestamp | Last time the token was registered. |

## `/pushQueue/{jobId}`

A pending push notification. The sender writes it right after a message commit,
using the message ID as the document ID so the same message cannot be queued
twice. Rules require that message to exist and to have been sent by the caller in
this chat, so a job cannot notify a chat without a message.

| Field | Type | Notes |
|---|---|---|
| `chatId` | string | Chat the message belongs to. |
| `senderId` | string | Must equal the caller and be a chat member. |
| `createdAt` | timestamp | Server timestamp; the relay drains oldest first. |
| `expireAt` | timestamp | Now + 24 hours; a TTL policy removes undrained jobs. |

There is deliberately no notification text here: the relay reads the message
named by the job ID for the sender name and preview, so a queue write cannot forge
notification copy. Clients cannot read the queue. The trusted relay uses the Admin
SDK, which bypasses rules, so no client needs read access to the queue or to other
students' device tokens. See `docs/PUSH.md`.

## Access summary

| Collection | Read | Create | Update | Delete |
|---|---|---|---|---|
| `/colleges` | anyone | nobody | nobody | nobody |
| `/users/{uid}` | owner + same college | owner, student defaults | owner, editable fields only | nobody |
| `/usernames/{username}` | signed-in | signed-in, if free and owned | nobody | owner |
| `/posts/{postId}` | same college | author, valid author fields | author edit; likes; comment count +1 | author |
| `/posts/{postId}/comments` | same college | same college, one-level parent | nobody | nobody |
| `/stories/{storyId}` | same college | author, expiry within 25h | viewers add only themselves to `seenBy` | author |
| `/boards/{boardId}` | same college | nobody (console only) | nobody | nobody |
| `/boards/{boardId}/threads` | same college | same college, named or anonymous author fields, score 0, unpinned | own vote; comment count +1; moderator pin; ghost report +1 | author (by `authorId` or ghost identity) or board moderator |
| `/boards/{boardId}/threads/{threadId}/comments` | same college | same college, depth = parent + 1, max 3, named or anonymous | own vote; ghost report +1 | author (by `authorId` or ghost identity) or board moderator |
| `/boards/{boardId}/threads/{threadId}/ghostReports` | nobody | same college, anonymous item, one per reporter | nobody | nobody |
| `/ghostIdentities/{itemId}` | board moderators | same college, identity fields, batch-written | nobody | author or board moderator |
| `/ghostAliases/{threadId_uid}` | owner only | owner only, ID must match thread and UID | nobody | nobody |
| `/ghostLimits/{uid_date}` | owner only | owner only, `count` starts at 1 | owner only, +1 and max 5 | nobody |
| `/revealLogs/{logId}` | board moderators | board moderator, identity must exist, reason required | nobody | nobody |
| `/blocks/{blockId}` | blocker only | blocker only | nobody | blocker only |
| `/reports/{reportId}` | nobody | signed-in, status `open` | nobody | nobody |
| `/follows/{followId}` | edges involving caller | follower only, ID must match | nobody | follower only |
| `/chats/{chatId}` | members only | creator, direct or group rules | members: own read marker, own mute, preview, admin/member changes, accept request | pending requests only |
| `/chats/{chatId}/messages` | members only | members with valid sender fields; request sender while pending | nobody | nobody |
| `/chats/{chatId}/typing` | members only | owner only | owner only | owner only |
| `/users/{uid}/devices` | owner only | owner only | owner only | owner only |
| `/pushQueue/{jobId}` | nobody | chat member, job ID must be their own message | nobody | nobody |

## Indexes

`firestore.indexes.json` contains the composite indexes:

| Collection | Fields | Used by |
|---|---|---|
| `posts` | `collegeId` asc, `createdAt` desc | Paginated college feed |
| `stories` | `collegeId` asc, `createdAt` desc | Story bar |
| `stories` | `collegeId` asc, `authorId` asc, `createdAt` asc | Story viewer |
| `chats` | `members` array-contains, `lastMessageAt` desc | Chat inbox |
| `boards` | `collegeId` asc, `name` asc | Board list |
| `threads` | `collegeId` asc, `pinned` desc, `createdAt` desc | Board threads, New sort |
| `threads` | `collegeId` asc, `score` desc, `createdAt` desc | Board threads, Top sort |
| `revealLogs` | `boardId` asc, `createdAt` desc | Moderation audit review by board and time |

Feed comments and board comments only order by `createdAt`, which Firestore
indexes automatically. Block lookups filter only on `blockerId`. Ghost reports
are written one document at a time and are not queried by the app.

## Future notes

- `avatarUrl` is intentionally empty until avatar support is added. No Firebase
  Storage is used.
- `karma` and `badges` are read-only from the client today.
- Image fields hold external links because the project does not use Firebase
  Storage and does not add an upload library without approval. This includes
  `avatarUrl`: the profile header shows a linked photo when set and falls back to
  initials while it loads, on failure, or when the field is empty.
- A production moderation tool would read `/reports` with trusted credentials.
  The client cannot read them by design.
- Chat history is a fixed newest-100 window. Loading older pages needs a cursor
  query and is a documented follow-up, not an unbounded listener.
- Chat push is receive-only on the client. FCM sending needs a trusted sender
  with a service-account credential, so the app queues jobs in `/pushQueue` and the
  optional relay in `docs/PUSH.md` delivers them with the Admin SDK. Credentials
  never ship in the app. `/users/{uid}/devices` holds the tokens it targets.
- Firebase 25.1 deprecated FCM registration tokens in favour of Firebase
  Installation IDs. The app still stores tokens because they remain co-supported
  and are what a console test sender targets; migrating is a follow-up.

## Ghost Mode limitations

Ghost Mode is enforced by Firestore rules and client batches, not by a trusted
server. The project runs on the Spark plan and does not use Cloud Functions, so
these gaps are intentional and should be closed before a high-stakes deployment:

- **No server-side pairing.** Rules cannot read documents written in the same
  batch. The app creates the public item, identity, alias, limit increment, and
  ghost report together, but rules cannot prove those documents belong together.
  A modified client can skip or alter the private pieces within what each rule
  allows.
- **Daily limit.** Rules allow only +1 and cap the counter at 5, but they cannot
  prove the increment belongs to a public post, and they only check that `date`
  is eight characters. A modified client can skip the limit document or choose a
  different date. The app uses the UTC date so normal clients agree on the day.
- **Hidden is a client filter.** Rules do not hide items with
  `reportCount >= 3`; denying those reads would break list queries. The app
  filters them for normal users and shows **Hidden pending review** to
  moderators, but a same-college modified client could still fetch a hidden
  document directly.
- **Report counter.** A reporter increments `reportCount` by exactly one, but
  rules cannot verify that the matching `ghostReports` document was written. A
  modified client can inflate a counter without a report.
- **Reveal audit.** `/revealLogs` is create-only and reads the identity after the
  log write, so a failed log fails the reveal. The log records the reason and
  item, but nothing can prevent a moderator from revealing without a serious
  report; the audit trail is the accountability mechanism.
- **No audit UI.** Reveal logs and ghost reports are reviewed in the Firebase
  console or with a trusted admin tool, not inside the student app.
- **Alias matching.** The app shows the delete control for your anonymous item by
  comparing aliases. Rules still enforce real ownership through
  `/ghostIdentities`; a rare alias collision could show the control without
  granting permission.
