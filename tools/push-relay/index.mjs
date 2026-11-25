/**
 * Campus Sync push relay.
 *
 * The Android app cannot send FCM pushes on the Spark plan, so it writes a small
 * job to /pushQueue next to every message. This process drains that queue with a
 * service account and sends data-only FCM messages to the other chat members.
 *
 * Run it from any scheduler (see tools/push-relay/README.md). Credentials come
 * from the environment and are never part of the Android app.
 */
import { cert, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";

const QUEUE = "pushQueue";
const BATCH_SIZE = 50;
const MAX_ATTEMPTS = 5;

function serviceAccount() {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (!raw) {
    throw new Error(
      "FIREBASE_SERVICE_ACCOUNT is missing. Set it to the service account JSON."
    );
  }
  return JSON.parse(raw);
}

const app = initializeApp({ credential: cert(serviceAccount()) });
const db = getFirestore(app);
const messaging = getMessaging(app);

/** Tokens for the recipients, skipping muted members. */
async function collectTokens(recipients, mutedUids) {
  const tokens = new Map();

  for (const uid of recipients) {
    if (mutedUids.has(uid)) continue;
    const devices = await db
      .collection("users")
      .doc(uid)
      .collection("devices")
      .get();

    for (const device of devices.docs) {
      const token = device.get("token");
      if (typeof token === "string" && token.length > 0) {
        tokens.set(token, uid);
      }
    }
  }

  return tokens;
}

/** Removes device documents FCM says are no longer valid. */
async function removeStaleTokens(entries, responses) {
  const stale = [];

  responses.forEach((result, index) => {
    if (result.success) return;
    const code = result.error?.code ?? "";
    if (
      code === "messaging/registration-token-not-registered" ||
      code === "messaging/invalid-argument"
    ) {
      stale.push(entries[index]);
    }
  });

  await Promise.all(
    stale.map(([token, uid]) =>
      db
        .collection("users")
        .doc(uid)
        .collection("devices")
        .doc(token)
        .delete()
        .catch(() => undefined)
    )
  );
}

async function handleJob(job) {
  const data = job.data();
  const chatId = data.chatId;

  if (typeof chatId !== "string" || chatId.length === 0) {
    await job.ref.delete();
    return 0;
  }

  const chat = await db.collection("chats").doc(chatId).get();
  const members = chat.get("members");
  const senderId = data.senderId;

  // The chat is gone, the sender left, or the job points at another chat.
  if (
    !chat.exists ||
    !Array.isArray(members) ||
    typeof senderId !== "string" ||
    !members.includes(senderId)
  ) {
    await job.ref.delete();
    return 0;
  }

  // The queue is write-only to clients, and its rules tie each job to a message
  // the sender actually wrote. Read that message so notification copy can never
  // be injected independently of the conversation.
  const message = await db
    .collection("chats")
    .doc(chatId)
    .collection("messages")
    .doc(job.id)
    .get();

  if (!message.exists || message.get("senderId") !== senderId) {
    await job.ref.delete();
    return 0;
  }

  const senderName =
    String(message.get("senderName") ?? "").trim().slice(0, 80) || "New message";
  const text = String(message.get("text") ?? "").trim();
  const body = text.length > 0 ? text.slice(0, 140) : "Photo";
  if (body.length === 0) {
    await job.ref.delete();
    return 0;
  }

  const mutedUids = new Set(chat.get("mutedBy") ?? []);
  const recipients = members.filter((uid) => uid !== senderId);
  const tokens = await collectTokens(recipients, mutedUids);

  let delivered = 0;
  if (tokens.size > 0) {
    const entries = [...tokens.entries()];
    const response = await messaging.sendEachForMulticast({
      tokens: entries.map(([token]) => token),
      // Data-only: the app decides whether to notify, and muted chats would be
      // impossible to skip if Android displayed the message itself.
      data: { chatId, senderId, senderName, body },
      android: { priority: "high" }
    });

    delivered = response.successCount;
    await removeStaleTokens(entries, response.responses);
  }

  await job.ref.delete();
  return delivered;
}

async function drainQueue() {
  const jobs = await db
    .collection(QUEUE)
    .orderBy("createdAt", "asc")
    .limit(BATCH_SIZE)
    .get();

  if (jobs.empty) {
    console.log("Push queue is empty.");
    return 0;
  }

  let delivered = 0;

  for (const job of jobs.docs) {
    try {
      delivered += await handleJob(job);
    } catch (error) {
      const attempts = (job.get("attempts") ?? 0) + 1;
      console.error(`Push job ${job.id} failed (attempt ${attempts}):`, error);

      if (attempts >= MAX_ATTEMPTS) {
        // Stop retrying a job that always fails; the TTL would remove it anyway.
        await job.ref.delete().catch(() => undefined);
      } else {
        // Only the relay writes this counter; clients cannot update the queue.
        await job.ref
          .update({ attempts, lastError: String(error?.message ?? error) })
          .catch(() => undefined);
      }
    }
  }

  console.log(`Processed ${jobs.size} push job(s), delivered ${delivered}.`);
  return delivered;
}

try {
  await drainQueue();
} catch (error) {
  console.error(error);
  process.exitCode = 1;
}
