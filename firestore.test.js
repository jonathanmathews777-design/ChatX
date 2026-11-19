const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_jonathan";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function adminContext() {
  return testEnv.authenticatedContext(ADMIN_UID, {
    email: "jonathan.mathews777@gmail.com",
    email_verified: true,
  });
}

function unverifiedSpoofContext() {
  return testEnv.authenticatedContext(BOB_UID, {
    email: "jonathan.mathews777@gmail.com",
    email_verified: false,
  });
}

function userContext(uid, email = `${uid}@example.com`) {
  return testEnv.authenticatedContext(uid, {
    email,
    email_verified: true,
  });
}

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

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("members").get());
  await assertFails(unauthDb.collection("invitations").get());
  await assertFails(unauthDb.collection("conversations").get());
  await assertFails(
    unauthDb.collection("members").doc(ALICE_UID).set({
      uid: ALICE_UID,
      username: "alice",
      displayName: "Alice",
      role: "MEMBER",
      status: "ACTIVE",
      publicKeyBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1234567890",
      keyFingerprint: "11223344556677889900",
      online: true,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Email spoofing test: unverified admin email cannot create invite or self-promote", async () => {
  const spoofDb = unverifiedSpoofContext().firestore();
  const now = new Date(Date.now() - 1000);
  const future = new Date(Date.now() + 86400000);

  await assertFails(
    spoofDb.collection("invitations").doc("CX-SPOOF-0001").set({
      createdByUid: BOB_UID,
      label: "Spoof",
      autoApprove: true,
      used: false,
      revoked: false,
      expiresAt: future,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    spoofDb.collection("members").doc(BOB_UID).set({
      uid: BOB_UID,
      username: "spoof_admin",
      displayName: "Spoof",
      role: "SUPER_ADMIN",
      status: "ACTIVE",
      publicKeyBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1234567890",
      keyFingerprint: "11223344556677889900",
      online: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Public registration without valid invitation code is strictly rejected", async () => {
  const aliceDb = userContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(
    aliceDb.collection("members").doc(ALICE_UID).set({
      uid: ALICE_UID,
      username: "alice_public",
      displayName: "Alice",
      role: "MEMBER",
      status: "ACTIVE",
      publicKeyBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1234567890",
      keyFingerprint: "11223344556677889900",
      online: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Super Admin bootstrap, invite generation, and single-use atomic invite redemption succeed", async () => {
  const adminDb = adminContext().firestore();
  const aliceDb = userContext(ALICE_UID).firestore();
  const bobDb = userContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);
  const expires = new Date(Date.now() + 86400000);

  // 1. Super Admin creates own profile + secret
  const adminBatch = adminDb.batch();
  adminBatch.set(adminDb.collection("members").doc(ADMIN_UID), {
    uid: ADMIN_UID,
    username: "jonathan_admin",
    displayName: "Jonathan (Admin)",
    role: "SUPER_ADMIN",
    status: "ACTIVE",
    publicKeyBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEADMINKEY123456",
    keyFingerprint: "AABBCCDDEEFF001122334455",
    online: true,
    createdAt: now,
    updatedAt: now,
  });
  adminBatch.set(adminDb.collection("member_secrets").doc(ADMIN_UID), {
    uid: ADMIN_UID,
    username: "jonathan_admin",
    passwordHash: "A".repeat(44),
    passwordSalt: "B".repeat(24),
    email: "jonathan.mathews777@gmail.com",
    createdAt: now,
    updatedAt: now,
  });
  adminBatch.set(adminDb.collection("username_directory").doc("jonathan_admin"), {
    uid: ADMIN_UID,
    username: "jonathan_admin",
    createdAt: now,
  });
  await assertSucceeds(adminBatch.commit());

  // 2. Super Admin creates single-use invite code
  await assertSucceeds(
    adminDb.collection("invitations").doc("CX-9K4M-7P2Q").set({
      createdByUid: ADMIN_UID,
      label: "Alice Invite",
      autoApprove: true,
      used: false,
      revoked: false,
      expiresAt: expires,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Non-admin cannot list invitations
  await assertFails(aliceDb.collection("invitations").get());

  // 3. Alice atomically redeems the invite code
  const redeemBatch = aliceDb.batch();
  redeemBatch.update(aliceDb.collection("invitations").doc("CX-9K4M-7P2Q"), {
    used: true,
    usedByUid: ALICE_UID,
    usedByUsername: "alice_sec",
    updatedAt: now,
  });
  redeemBatch.set(aliceDb.collection("members").doc(ALICE_UID), {
    uid: ALICE_UID,
    username: "alice_sec",
    displayName: "Alice",
    role: "MEMBER",
    status: "ACTIVE",
    publicKeyBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEALICEKEY123456",
    keyFingerprint: "112233445566778899AABBCC",
    online: true,
    inviteCodeId: "CX-9K4M-7P2Q",
    createdAt: now,
    updatedAt: now,
  });
  redeemBatch.set(aliceDb.collection("member_secrets").doc(ALICE_UID), {
    uid: ALICE_UID,
    username: "alice_sec",
    passwordHash: "C".repeat(44),
    passwordSalt: "D".repeat(24),
    email: "alice_uid_001@example.com",
    createdAt: now,
    updatedAt: now,
  });
  redeemBatch.set(aliceDb.collection("username_directory").doc("alice_sec"), {
    uid: ALICE_UID,
    username: "alice_sec",
    createdAt: now,
  });
  await assertSucceeds(redeemBatch.commit());

  // 4. Bob cannot reuse the already-redeemed single-use invite code
  const bobBatch = bobDb.batch();
  bobBatch.update(bobDb.collection("invitations").doc("CX-9K4M-7P2Q"), {
    used: true,
    usedByUid: BOB_UID,
    usedByUsername: "bob_intruder",
    updatedAt: now,
  });
  bobBatch.set(bobDb.collection("members").doc(BOB_UID), {
    uid: BOB_UID,
    username: "bob_intruder",
    displayName: "Bob",
    role: "MEMBER",
    status: "ACTIVE",
    publicKeyBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEBOBKEY12345678",
    keyFingerprint: "99887766554433221100AABB",
    online: true,
    inviteCodeId: "CX-9K4M-7P2Q",
    createdAt: now,
    updatedAt: now,
  });
  await assertFails(bobBatch.commit());
});

test("PII & password hash isolation: members cannot read other members' secrets", async () => {
  const now = new Date(Date.now() - 1000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("members").doc(ALICE_UID).set({
      uid: ALICE_UID,
      username: "alice_sec",
      displayName: "Alice",
      role: "MEMBER",
      status: "ACTIVE",
      publicKeyBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEALICEKEY123456",
      keyFingerprint: "112233445566778899AABBCC",
      online: true,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("member_secrets").doc(ALICE_UID).set({
      uid: ALICE_UID,
      username: "alice_sec",
      passwordHash: "C".repeat(44),
      passwordSalt: "D".repeat(24),
      email: "alice@example.com",
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = userContext(ALICE_UID).firestore();
  const bobDb = userContext(BOB_UID).firestore();
  const adminDb = adminContext().firestore();

  await assertSucceeds(aliceDb.collection("member_secrets").doc(ALICE_UID).get());
  await assertSucceeds(adminDb.collection("member_secrets").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("member_secrets").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("member_secrets").get());

  // Query alignment on members: filtered active query succeeds, unfiltered fails for non-admin
  await assertSucceeds(bobDb.collection("members").where("status", "==", "ACTIVE").get());
  await assertFails(bobDb.collection("members").get());
});

test("Super Admin can suspend and remove members; members cannot elevate status or role", async () => {
  const now = new Date(Date.now() - 1000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("members").doc(ALICE_UID).set({
      uid: ALICE_UID,
      username: "alice_sec",
      displayName: "Alice",
      role: "MEMBER",
      status: "PENDING",
      publicKeyBase64: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEALICEKEY123456",
      keyFingerprint: "112233445566778899AABBCC",
      online: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = userContext(ALICE_UID).firestore();
  const adminDb = adminContext().firestore();

  // Alice cannot self-approve or become SUPER_ADMIN
  await assertFails(
    aliceDb.collection("members").doc(ALICE_UID).update({
      status: "ACTIVE",
      updatedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("members").doc(ALICE_UID).update({
      role: "SUPER_ADMIN",
      updatedAt: now,
    })
  );

  // Admin can approve, suspend, and remove Alice
  await assertSucceeds(
    adminDb.collection("members").doc(ALICE_UID).update({
      status: "ACTIVE",
      updatedAt: now,
    })
  );
  await assertSucceeds(
    adminDb.collection("members").doc(ALICE_UID).update({
      status: "SUSPENDED",
      updatedAt: now,
    })
  );
  await assertSucceeds(adminDb.collection("members").doc(ALICE_UID).delete());
});

test("Conversations and encrypted messages enforce participant isolation & query alignment", async () => {
  const now = new Date(Date.now() - 1000);
  const adminDb = adminContext().firestore();
  const aliceDb = userContext(ALICE_UID).firestore();
  const bobDb = userContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("conversations").doc("conv_1").set({
      type: "DIRECT",
      title: "Alice & Jonathan",
      participantUids: [ALICE_UID, ADMIN_UID],
      adminUid: ALICE_UID,
      lastMessagePreview: "Encrypted chat started",
      disappearingSeconds: 0,
      typingUids: [],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("conversations").doc("conv_1").collection("messages").doc("msg_1").set({
      conversationId: "conv_1",
      senderUid: ALICE_UID,
      senderUsername: "alice_sec",
      participantUids: [ALICE_UID, ADMIN_UID],
      messageType: "TEXT",
      ciphertextBase64: "ZW5jcnlwdGVkX3BheWxvYWQ=",
      ivBase64: "MTIzNDU2Nzg5MDEy",
      encryptionScheme: "ECDH-P256-HKDF-AES256GCM",
      deliveryStatus: "SENT",
      readByUids: [ALICE_UID],
      edited: false,
      deleted: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Filtered participant queries succeed; unfiltered fail for non-admin
  await assertSucceeds(
    aliceDb.collection("conversations").where("participantUids", "array-contains", ALICE_UID).get()
  );
  await assertFails(aliceDb.collection("conversations").get());

  await assertSucceeds(
    adminDb
      .collection("conversations")
      .doc("conv_1")
      .collection("messages")
      .where("participantUids", "array-contains", ADMIN_UID)
      .get()
  );

  // Outsider Bob cannot read or inject into conversation
  await assertFails(bobDb.collection("conversations").doc("conv_1").get());
  await assertFails(
    bobDb.collection("conversations").doc("conv_1").collection("messages").doc("msg_1").get()
  );
  await assertFails(
    bobDb.collection("conversations").doc("conv_1").collection("messages").doc("msg_2").set({
      conversationId: "conv_1",
      senderUid: BOB_UID,
      senderUsername: "bob",
      participantUids: [ALICE_UID, ADMIN_UID, BOB_UID],
      messageType: "TEXT",
      ciphertextBase64: "aGFja2Vk",
      ivBase64: "MTIzNDU2Nzg5MDEy",
      encryptionScheme: "ECDH-P256-HKDF-AES256GCM",
      deliveryStatus: "SENT",
      readByUids: [BOB_UID],
      edited: false,
      deleted: false,
      createdAt: now,
      updatedAt: now,
    })
  );
});
