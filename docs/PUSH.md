# Campus Sync — push without Cloud Functions

## The problem

The project runs on the Firebase **Spark** plan:

- Cloud Functions are not available, so no Firestore trigger can send a push.
- An Android app cannot send FCM messages itself: the FCM HTTP v1 API needs a
  service-account credential, and a credential shipped in an APK is public.
- Firebase Storage is not used either. Chat images are external `https://` links.

The client can **receive** pushes, but something outside the app has to send them.

## The workaround

A small queue plus a relay you own. Nothing in the app needs a secret, and the
relay uses only the Firebase Admin SDK, no Cloud Functions.

```text
sender's app ── message + chat preview (batch) ──► Firestore
             └─ best-effort job ────────────────► /pushQueue/{messageId}

free scheduler (every N minutes)
      └─ tools/push-relay (Admin SDK)
            ├─ reads the oldest 50 jobs
            ├─ reads the chat (members, mutedBy)
            ├─ reads the message named by each job (sender, text)
            ├─ reads members' /users/{uid}/devices tokens
            ├─ sends data-only FCM to every member except the sender
            └─ deletes the job
```

Why a queue instead of polling chats:

- An idle queue costs no reads. Polling chats would read the same documents on
  every run and could burn the Spark daily read quota.
- The job ID is the message ID, so the same message cannot be queued twice.
- Processing is one document per notification, and the relay deletes it.

The queue is write-only for clients (`firestore.rules`), and rules accept a job
only when its ID is a message the caller already sent in that chat. The job stores
no notification text: the relay reads the message for the sender name and preview,
so the queue cannot be used to send forged notification copy. The relay uses the
Admin SDK, which bypasses rules, so device tokens are never exposed to other
students.

## What the app does

- Registers the FCM token under `/users/{uid}/devices/{token}`.
- Writes one `/pushQueue` job next to each sent message, containing only `chatId`,
  `senderId`, `createdAt`, and `expireAt`. This write is best-effort: the message
  and chat preview are already committed, and a queue failure never turns a sent
  message into an error.
- Displays incoming data-only messages, skipping muted chats and chats the
  signed-in student is not a member of, and opens the conversation on tap.

Without the relay, chat still works in real time. Only the notification is missing.

## Setup

1. **Enable the queue TTL** (2 minutes). Firestore → **Time-to-live** → create a
   policy with collection group `pushQueue` and timestamp field `expireAt`. This
   removes jobs that are never drained. It does not delete delivered jobs; the
   relay does that itself.
2. **Create a service account.** Google Cloud console → **IAM & Admin → Service
   accounts → Create**. Grant **Cloud Datastore User** (`roles/datastore.user`, for
   Firestore) and **Firebase Cloud Messaging API Admin**
   (`roles/firebasecloudmessaging.admin`, for sending). Firestore access alone
   cannot send FCM; do not use an Editor key. Create a JSON key and keep it out of
   the repository.
3. **Store the JSON as a secret.** For the included GitHub workflow: repository →
   **Settings → Secrets and variables → Actions → New repository secret**, name
   `FIREBASE_SERVICE_ACCOUNT`, paste the JSON.
4. **Enable the scheduler.** The workflow `.github/workflows/push-relay.yml` runs
   every 5 minutes. Run it once from the **Actions** tab to check the log.
5. **Send a test message** from another account and confirm the notification
   appears and opens the chat.

Details and alternative hosts: [`tools/push-relay/README.md`](../tools/push-relay/README.md).

## Limits

- **Latency is the schedule interval.** GitHub's minimum is 5 minutes and runs can
  be delayed. For near-instant delivery, run the same script on an always-on free
  scheduler or a small host at a 1-minute interval.
- **Best-effort delivery.** A crash between the message commit and the queue write
  loses that one notification; the message itself is unaffected.
- **Private GitHub repositories** spend Actions minutes, so a 15-minute schedule
  may fit the free allowance better than 5 minutes.
- **Data-only messages.** The relay must not use FCM `notification` payloads, or
  Android displays them itself and the app cannot honour mute.
- The relay needs Firestore and FCM access only. Do not give it broader roles.

## Spark cost

| Operation | Cost |
|---|---|
| Queue job write, per message | 1 write |
| Relay: read a job | 1 read |
| Relay: read the message a job names | 1 read |
| Relay: read chat + device tokens | 1 + members reads |
| Relay: delete a job | 1 delete |

An idle queue is free. With the free Spark quotas (50k reads, 20k writes and
20k deletes per day) this fits a campus-sized app. If the relay is not running,
the TTL policy still cleans up.

## Why not other options

- **Cloud Functions / Blaze:** not allowed by the project constraints.
- **Sending from the app:** would require embedding a service-account key, which
  is a security breach.
- **A push provider SDK (for example OneSignal):** still needs a trusted sender to
  trigger it, and adds a dependency and a second identity system.
- **Polling `/chats` from the relay:** more reads on every run and duplicate
  notifications unless extra state is stored.
