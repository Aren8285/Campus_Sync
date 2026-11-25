# Push relay

The Android client cannot send FCM pushes on the Spark plan. It writes a small job
to `/pushQueue` next to each message; this relay drains that queue and sends the
notifications. It uses the Firebase Admin SDK, so no Cloud Functions are needed.

Full context and trade-offs: [`docs/PUSH.md`](../../docs/PUSH.md).

## What you need

1. A Google Cloud service account with **Cloud Datastore User**
   (`roles/datastore.user`, for Firestore) and **Firebase Cloud Messaging API
   Admin** (`roles/firebasecloudmessaging.admin`, for sending). The Firestore role
   alone cannot send FCM.
2. The service account JSON, stored as a secret named `FIREBASE_SERVICE_ACCOUNT`.
3. Node.js 20 or newer on whatever host runs the schedule.

Never commit the JSON, and never put it in the Android app. Only this relay reads
the queue, and only with these credentials.

## Run it locally

```bash
cd tools/push-relay
npm install
FIREBASE_SERVICE_ACCOUNT="$(cat /path/to/service-account.json)" npm start
```

The relay prints how many jobs it processed. An empty queue is normal.

## Schedule it for free

### GitHub Actions (included in this repo)

`.github/workflows/push-relay.yml` runs every 5 minutes.

1. Open the repository on GitHub.
2. **Settings → Secrets and variables → Actions → New repository secret**.
3. Name it `FIREBASE_SERVICE_ACCOUNT` and paste the JSON.
4. **Actions → Push relay → Run workflow** to test it once.

Scheduled workflows only run from the default branch. GitHub cron has a 5-minute
minimum and can be delayed, and private repositories spend Actions minutes
(2,000/month on the free plan), so a 15-minute schedule may be a better fit there.
For 1-minute latency, run `npm start` from any always-on free scheduler or a small
VM with the same environment variable.

### Any other scheduler

The relay is a plain Node script with a single environment variable. Any host that
can run it on a schedule and has outbound HTTPS works: a cron entry, a container
job, or a scheduled serverless runtime.

## Notes

- A job ID is the message ID, and the relay reads that message for the sender name
  and preview. Nothing in the queue document is trusted as notification copy.
- Only the newest 50 jobs are processed per run (`BATCH_SIZE`).
- Jobs that keep failing are retried; after 5 attempts they are deleted. The
  `/pushQueue` TTL policy would remove them anyway.
- FCM tokens reported as unregistered are deleted from `/users/{uid}/devices`.
- Delivery is best-effort. Chat itself is realtime and never depends on the relay.
