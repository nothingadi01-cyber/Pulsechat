const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const now = () => new Date();

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// --- 1. Unauthenticated Rejections ---
test("Unauthenticated user cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("public_directory").doc(ALICE_UID).get());
  await assertFails(unauth.collection("chats").where("memberUids", "array-contains", ALICE_UID).get());
  await assertFails(unauth.collection("calls").where("memberUids", "array-contains", ALICE_UID).get());
});

// --- 2. Private User Profile & PII Isolation ---
test("Owner can create and read own profile; other user is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      publicKeyFingerprint: "fp_alice_001",
      createdAt: now(),
      updatedAt: now(),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  // Shadow field injection must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: now(),
    })
  );
});

// --- 3. Public Directory (Get by UID, No Blanket List) ---
test("Public directory allows get by signed-in user but blocks blanket list", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("public_directory").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      handle: "alice_pt",
      publicKeyFingerprint: "fp_alice_001",
      createdAt: now(),
      updatedAt: now(),
    })
  );

  await assertSucceeds(bobDb.collection("public_directory").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("public_directory").get());
  await assertSucceeds(
    aliceDb.collection("public_directory").where("userId", "==", ALICE_UID).get()
  );
});

// --- 4. User Subcollections: Contacts, Devices, Backups, Statuses, Channels ---
test("User subcollections enforce strict owner isolation and validation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc("dev_1").set({
      userId: ALICE_UID,
      deviceName: "Pixel 9 Pro",
      platform: "Android 16",
      keyFingerprint: "fp_dev_1",
      status: "active",
      lastSyncAt: now(),
      createdAt: now(),
      updatedAt: now(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("backups").doc("bkp_1").set({
      userId: ALICE_UID,
      label: "Daily E2EE Backup",
      chatCount: 3,
      messageCount: 42,
      sizeBytes: 12040,
      checksumSha256: "abc123checksum",
      encryptedManifest: "encrypted_blob_v1",
      encryptionMode: "E2EE-AES256",
      createdAt: now(),
      updatedAt: now(),
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("devices").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("backups").get());
  // Revoked device cannot be re-activated
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc("dev_1").update({
      status: "revoked",
      updatedAt: now(),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc("dev_1").update({
      status: "active",
      updatedAt: now(),
    })
  );
});

// --- 5. Chats & Messages (Member-Scoped + Query Alignment) ---
test("Chats and messages enforce memberUids membership and prevent cross-user leaks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_1").set({
      title: "Alice Vault",
      chatType: "vault",
      createdBy: ALICE_UID,
      memberUids: [ALICE_UID],
      encryptionKeyId: "key_1",
      createdAt: now(),
      updatedAt: now(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("chats").where("memberUids", "array-contains", ALICE_UID).get()
  );
  // Unfiltered list must fail
  await assertFails(aliceDb.collection("chats").get());
  // Bob cannot read Alice's private chat
  await assertFails(bobDb.collection("chats").doc("chat_1").get());

  // Message creation & reading
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_1").collection("messages").doc("msg_1").set({
      chatId: "chat_1",
      senderId: ALICE_UID,
      senderName: "Alice",
      memberUids: [ALICE_UID],
      ciphertext: "base64ciphertext==",
      iv: "iv123==",
      encryptionScheme: "ECDH-P256-AES256-GCM",
      messageType: "text",
      createdAt: now(),
      updatedAt: now(),
    })
  );

  await assertSucceeds(
    aliceDb
      .collection("chats")
      .doc("chat_1")
      .collection("messages")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );
  await assertFails(
    bobDb.collection("chats").doc("chat_1").collection("messages").doc("msg_1").get()
  );
});

// --- 6. Calls Terminal State Locking ---
test("Calls enforce terminal state locking once ended", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").set({
      callerId: ALICE_UID,
      callerName: "Alice",
      calleeId: "echo_test",
      calleeName: "PulseTalk Echo",
      memberUids: [ALICE_UID],
      status: "ringing",
      callerEphemeralPubKey: "pub_key_1",
      sasCode: "🔐 ⚡ 🌿 🎯",
      cipherSuite: "ECDH-P256 + AES-256-GCM",
      createdAt: now(),
      updatedAt: now(),
    })
  );

  await assertFails(bobDb.collection("calls").doc("call_1").get());

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").update({
      status: "active",
      calleeEphemeralPubKey: "pub_key_2",
      updatedAt: now(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("calls").doc("call_1").update({
      status: "ended",
      durationSeconds: 18,
      updatedAt: now(),
    })
  );

  // Terminal state lock: cannot transition out of 'ended'
  await assertFails(
    aliceDb.collection("calls").doc("call_1").update({
      status: "active",
      calleeEphemeralPubKey: "pub_key_3",
      updatedAt: now(),
    })
  );
});
