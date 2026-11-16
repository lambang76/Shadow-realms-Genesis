const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const serverTimestamp = () => new Date(Date.now() - 2000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

function validUserPayload(uid) {
  return {
    userId: uid,
    displayName: "Vespera",
    selectedClass: "BLOODBLADE",
    soulEssence: 100,
    highestDepth: 1,
    totalRuns: 0,
    totalKills: 0,
    crimsonMightRank: 0,
    obsidianWardRank: 0,
    forbiddenArcanaRank: 0,
    equippedWeaponId: "starter_bloodblade",
    equippedRelicId: "starter_ash_signet",
    discoveredChronicles: [],
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validModPayload(uid, isPublished = true) {
  return {
    authorUid: uid,
    authorName: "Vespera",
    modType: "WEAPON",
    name: "Crimson Scythe",
    loreEn: "Forged in weeping iron.",
    loreId: "Ditempa dari besi yang menangis.",
    effectTag: "VAMPIRIC",
    powerValue: 45,
    speedValue: 120,
    accentHex: "#9E2A3B",
    isPublished,
    upvotes: 0,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validLobbyPayload(uid, code = "ASH7K9", isPublic = true) {
  return {
    hostUid: uid,
    hostName: "Vespera",
    inviteCode: code,
    isPublic,
    status: "WAITING",
    stageDepth: 1,
    stageTitle: "Cathedral of Ash",
    biomeTheme: "CRIMSON_CRYPT",
    activeModIds: [],
    memberUids: [uid],
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

// --- 1. UNAUTHENTICATED REJECTIONS ---

test("Unauthenticated user cannot read or write users, mods, or lobbies", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("community_mods").where("isPublished", "==", true).get());
  await assertFails(unauthDb.collection("raid_lobbies").where("isPublic", "==", true).get());
});

// --- 2. USER PROFILE TESTS ---

test("User can create, read, and update own profile, but cannot spoof or shadow-update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  // Cross-user read & write blocked
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).set(validUserPayload(BOB_UID)));

  // Shadow update with ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      soulEssence: 999,
      isAdmin: true,
      updatedAt: serverTimestamp(),
    })
  );

  // Valid progression update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      soulEssence: 250,
      crimsonMightRank: 1,
      updatedAt: serverTimestamp(),
    })
  );
});

// --- 3. COMMUNITY MODS TESTS ---

test("Community mods enforce query filters, author ownership, and +1 upvote tier", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("community_mods").doc("mod_crimson_1").set(validModPayload(ALICE_UID, true))
  );

  // Blanket list without filter fails
  await assertFails(bobDb.collection("community_mods").get());

  // Filtered list for published mods succeeds
  await assertSucceeds(
    bobDb.collection("community_mods").where("isPublished", "==", true).get()
  );

  // Bob cannot edit Alice's mod stats
  await assertFails(
    bobDb.collection("community_mods").doc("mod_crimson_1").update({
      powerValue: 500,
      updatedAt: serverTimestamp(),
    })
  );

  // Bob can upvote by +1
  await assertSucceeds(
    bobDb.collection("community_mods").doc("mod_crimson_1").update({
      upvotes: 1,
      updatedAt: serverTimestamp(),
    })
  );

  // Bob cannot upvote by +5
  await assertFails(
    bobDb.collection("community_mods").doc("mod_crimson_1").update({
      upvotes: 6,
      updatedAt: serverTimestamp(),
    })
  );
});

// --- 4. CO-OP RAID LOBBIES & SUBCOLLECTIONS ---

test("Raid lobbies support matchmaking query, friend join, subcollections, and terminal lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("raid_lobbies").doc("ASH7K9").set(validLobbyPayload(ALICE_UID, "ASH7K9", true))
  );

  // Blanket list fails; matchmaking query succeeds
  await assertFails(bobDb.collection("raid_lobbies").get());
  await assertSucceeds(
    bobDb.collection("raid_lobbies")
      .where("isPublic", "==", true)
      .where("status", "==", "WAITING")
      .get()
  );

  // Bob joins lobby
  await assertSucceeds(
    bobDb.collection("raid_lobbies").doc("ASH7K9").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: serverTimestamp(),
    })
  );

  // Alice and Bob write their live player state; Charlie (non-member) is blocked
  const bobPlayerPayload = {
    playerId: BOB_UID,
    displayName: "Kael",
    archetype: "NECROMANCER",
    posX: 120.0,
    posY: 200.0,
    facingAngle: 1.5,
    hp: 100,
    maxHp: 100,
    isAttacking: false,
    pingText: "Ready!",
    memberUids: [ALICE_UID, BOB_UID],
    updatedAt: serverTimestamp(),
  };

  await assertSucceeds(
    bobDb.collection("raid_lobbies").doc("ASH7K9").collection("players").doc(BOB_UID).set(bobPlayerPayload)
  );
  await assertFails(
    charlieDb.collection("raid_lobbies").doc("ASH7K9").collection("players").doc(CHARLIE_UID).set({
      ...bobPlayerPayload,
      playerId: CHARLIE_UID,
    })
  );
  await assertFails(
    charlieDb.collection("raid_lobbies").doc("ASH7K9").collection("players").doc(BOB_UID).get()
  );
  await assertFails(
    charlieDb.collection("raid_lobbies").doc("ASH7K9").collection("players").get()
  );
  await assertFails(
    charlieDb.collection("raid_lobbies").doc("ASH7K9").collection("players")
      .where("memberUids", "array-contains", ALICE_UID).get()
  );
  await assertSucceeds(
    aliceDb.collection("raid_lobbies").doc("ASH7K9").collection("players")
      .where("memberUids", "array-contains", ALICE_UID).get()
  );

  // Host marks lobby COMPLETED -> terminal lock prevents further updates
  await assertSucceeds(
    aliceDb.collection("raid_lobbies").doc("ASH7K9").update({
      status: "COMPLETED",
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    aliceDb.collection("raid_lobbies").doc("ASH7K9").update({
      status: "WAITING",
      updatedAt: serverTimestamp(),
    })
  );
});
