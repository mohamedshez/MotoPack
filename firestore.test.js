const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_rider_1";
const BOB_UID = "bob_rider_2";
const CHARLIE_UID = "charlie_outsider_3";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const pastNow = () => new Date(Date.now() - 2000);

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

test("Unauthenticated caller cannot read or list users or packs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("packs").get());
});

test("User profile: owner can create and read own profile, cross-user read/write denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastNow();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Tracer",
      bikeModel: "Tracer 9 GT",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked", updatedAt: pastNow() })
  );
});

test("User profile: shadow update with ghost field is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastNow();

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      isAdmin: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Packs: member can query packs with array-contains, outsider cannot read or list", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastNow();

  await assertSucceeds(
    aliceDb.collection("packs").doc("pack_peak_1").set({
      name: "Peak District Pack",
      inviteCode: "PEAK99",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      encryptionSalt: "salt_1234567890",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("packs").where("memberUids", "array-contains", ALICE_UID).get()
  );
  await assertFails(charlieDb.collection("packs").doc("pack_peak_1").get());
  await assertFails(charlieDb.collection("packs").get());
});

test("Pack members telemetry: rider can write own encrypted telemetry and pack member can list", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastNow();

  await assertSucceeds(
    aliceDb.collection("packs").doc("pack_1").set({
      name: "Alps Run",
      inviteCode: "ALPS26",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      encryptionSalt: "salt_abcdef1234",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("packs").doc("pack_1").collection("members").doc(ALICE_UID).set({
      userId: ALICE_UID,
      packId: "pack_1",
      displayName: "Alice",
      encryptedTelemetry: "ciphertext_base64_here",
      batteryPercent: 84,
      isCharging: false,
      isOnline: true,
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    bobDb.collection("packs").doc("pack_1").collection("members")
      .where("memberUids", "array-contains", BOB_UID)
      .get()
  );

  await assertFails(
    bobDb.collection("packs").doc("pack_1").collection("members").doc(ALICE_UID).update({
      batteryPercent: 10,
      updatedAt: pastNow(),
    })
  );

  await assertFails(
    charlieDb.collection("packs").doc("pack_1").collection("members").get()
  );
  await assertFails(
    charlieDb.collection("packs").doc("pack_1").collection("members").doc(ALICE_UID).get()
  );
  const charlieSnap = await assertSucceeds(
    charlieDb.collection("packs").doc("pack_1").collection("members")
      .where("memberUids", "array-contains", CHARLIE_UID)
      .get()
  );
  assert.equal(charlieSnap.size, 0);
});

test("Challenges: terminal state locking prevents updates once completed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastNow();

  await assertSucceeds(
    aliceDb.collection("packs").doc("pack_c").set({
      name: "Climb Pack",
      inviteCode: "CLIMB1",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID],
      encryptionSalt: "salt_challenge_123",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  const challengeRef = aliceDb.collection("packs").doc("pack_c").collection("challenges").doc("ch_1");
  await assertSucceeds(
    challengeRef.set({
      packId: "pack_c",
      creatorId: ALICE_UID,
      title: "Peak 500 Climb",
      challengeType: "ELEVATION",
      targetValue: 500,
      currentValue: 200,
      unit: "m",
      status: "active",
      memberUids: [ALICE_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    challengeRef.update({
      currentValue: 500,
      status: "completed",
      updatedAt: pastNow(),
    })
  );

  await assertFails(
    challengeRef.update({
      currentValue: 600,
      updatedAt: pastNow(),
    })
  );
});

test("Pack markers and gallery photos: members can create/read/confirm, outsiders denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastNow();

  await assertSucceeds(
    aliceDb.collection("packs").doc("pack_m").set({
      name: "Hazard Watch",
      inviteCode: "HAZ999",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      encryptionSalt: "salt_markers_1234",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  const markerRef = aliceDb.collection("packs").doc("pack_m").collection("markers").doc("pin_1");
  await assertSucceeds(
    markerRef.set({
      packId: "pack_m",
      creatorId: ALICE_UID,
      creatorName: "Alice",
      pinType: "HAZARD_WARNING",
      title: "Gravel on Hairpin",
      description: "Loose chippings past bridge",
      lat: 53.4339,
      lng: -1.8732,
      severity: "DANGER",
      confirmedByUids: [ALICE_UID],
      status: "active",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    bobDb.collection("packs").doc("pack_m").collection("markers").doc("pin_1").update({
      confirmedByUids: [ALICE_UID, BOB_UID],
      updatedAt: pastNow(),
    })
  );
  await assertFails(
    charlieDb.collection("packs").doc("pack_m").collection("markers").doc("pin_1").get()
  );

  const photoRef = bobDb.collection("packs").doc("pack_m").collection("photos").doc("ph_1");
  await assertSucceeds(
    photoRef.set({
      packId: "pack_m",
      uploaderId: BOB_UID,
      uploaderName: "Bob",
      caption: "Summit cafe break",
      locationLabel: "Snake Pass",
      imageDataBase64: "AAAA_BASE64_TEST_IMAGE_DATA_012345",
      lat: 53.43,
      lng: -1.87,
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("packs").doc("pack_m").collection("photos")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );
  await assertFails(
    charlieDb.collection("packs").doc("pack_m").collection("photos").doc("ph_1").get()
  );
});

test("Emergency SOS: member can broadcast EMERGENCY_SOS ping and SOS pin, outsider denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastNow();

  await assertSucceeds(
    aliceDb.collection("packs").doc("pack_sos").set({
      name: "SOS Test Pack",
      inviteCode: "SOS911",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      encryptionSalt: "salt_sos_123456",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("packs").doc("pack_sos").collection("pings").doc("sos_ping_1").set({
      senderId: ALICE_UID,
      senderName: "Alice",
      targetUserId: "ALL",
      packId: "pack_sos",
      alertType: "EMERGENCY_SOS",
      encryptedNote: "SOS_CIPHER_PAYLOAD",
      acknowledged: false,
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("packs").doc("pack_sos").collection("markers").doc("sos_pin_1").set({
      packId: "pack_sos",
      creatorId: ALICE_UID,
      creatorName: "Alice",
      pinType: "EMERGENCY_SOS",
      title: "SOS: Alice Needs Help",
      description: "Crash / Rider Down",
      lat: 53.3811,
      lng: -1.4701,
      severity: "DANGER",
      confirmedByUids: [ALICE_UID],
      status: "active",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    bobDb.collection("packs").doc("pack_sos").collection("pings").doc("sos_ping_1").get()
  );
  await assertSucceeds(
    bobDb.collection("packs").doc("pack_sos").collection("pings").doc("sos_ping_1").update({
      acknowledged: true,
      updatedAt: pastNow(),
    })
  );
  await assertFails(
    charlieDb.collection("packs").doc("pack_sos").collection("pings").doc("sos_ping_1").get()
  );
});

test("Push-to-Talk voice messages: pack member can send valid AMR_NB voice clip, oversized or outsider rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastNow();

  await assertSucceeds(
    aliceDb.collection("packs").doc("pack_ptt").set({
      name: "PTT Ridge Convoy",
      inviteCode: "PTT888",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      encryptionSalt: "salt_ptt_123456",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("packs").doc("pack_ptt").collection("messages").doc("voice_1").set({
      senderId: ALICE_UID,
      senderName: "Alice",
      packId: "pack_ptt",
      recipientId: "",
      encryptedBody: "ENC_SUMMARY_VOICE_6S",
      messageType: "voice",
      encryptedAudio: "ENC_AMR_NB_AUDIO_BASE64_PAYLOAD_OK",
      audioCodec: "AMR_NB",
      audioDurationMs: 6200,
      audioSizeBytes: 9400,
      audioPeaks: "2,4,7,8,6,4,3,5,8,9,6,4,3,5,4,2",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts,
    })
  );

  await assertSucceeds(
    bobDb.collection("packs").doc("pack_ptt").collection("messages").doc("voice_1").get()
  );

  await assertFails(
    charlieDb.collection("packs").doc("pack_ptt").collection("messages").doc("voice_1").get()
  );

  await assertFails(
    bobDb.collection("packs").doc("pack_ptt").collection("messages").doc("voice_oversized").set({
      senderId: BOB_UID,
      senderName: "Bob",
      packId: "pack_ptt",
      encryptedBody: "ENC_SUMMARY",
      messageType: "voice",
      encryptedAudio: "X".repeat(66000),
      audioCodec: "AMR_NB",
      audioDurationMs: 12000,
      audioSizeBytes: 18000,
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: ts,
    })
  );
});

test("Biker & Brew Passport stamps: owner can stamp and list, cross-user read/write denied, ghost field rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastNow();

  const stampRef = aliceDb.collection("users").doc(ALICE_UID).collection("passport_stamps").doc("brew_peak_roasters");
  await assertSucceeds(
    stampRef.set({
      userId: ALICE_UID,
      locationId: "brew_peak_roasters",
      locationName: "Peak Roasters Moto Café",
      stampCode: "BB-01",
      brewNote: "Double Piston Cortado",
      lat: 53.3832,
      lng: -1.4728,
      verifiedGps: true,
      stampedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("passport_stamps").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("passport_stamps").doc("brew_peak_roasters").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("passport_stamps").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("passport_stamps").doc("bad_stamp").set({
      userId: ALICE_UID,
      locationId: "brew_2",
      locationName: "Test",
      stampCode: "BB-02",
      lat: 53.3,
      lng: -1.4,
      verifiedGps: true,
      freeCoffeeForever: true,
      stampedAt: ts,
    })
  );
});

test("Biker Events: pack members can create/RSVP, terminal locking works, outsiders denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastNow();

  await assertSucceeds(
    aliceDb.collection("packs").doc("pack_ev").set({
      name: "Espresso Convoy",
      inviteCode: "BREW26",
      ownerId: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      encryptionSalt: "salt_events_1234",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  const eventRef = aliceDb.collection("packs").doc("pack_ev").collection("events").doc("ev_1");
  await assertSucceeds(
    eventRef.set({
      packId: "pack_ev",
      creatorId: ALICE_UID,
      creatorName: "Alice",
      title: "Sunday Peak Roasters Meet",
      description: "Early ride out & espresso",
      eventType: "BIKER_BREW",
      locationId: "brew_peak_roasters",
      locationName: "Peak Roasters Moto Café",
      brewSpecialty: "Cortado",
      scheduledLabel: "Sun 08:30",
      lat: 53.3832,
      lng: -1.4728,
      attendeeUids: [ALICE_UID],
      memberUids: [ALICE_UID, BOB_UID],
      isPassportEvent: true,
      status: "upcoming",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    bobDb.collection("packs").doc("pack_ev").collection("events").doc("ev_1").update({
      attendeeUids: [ALICE_UID, BOB_UID],
      updatedAt: pastNow(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("packs").doc("pack_ev").collection("events").doc("ev_1").update({
      status: "completed",
      updatedAt: pastNow(),
    })
  );

  await assertFails(
    bobDb.collection("packs").doc("pack_ev").collection("events").doc("ev_1").update({
      attendeeUids: [ALICE_UID],
      updatedAt: pastNow(),
    })
  );

  await assertFails(
    charlieDb.collection("packs").doc("pack_ev").collection("events").doc("ev_1").get()
  );
});

test("Garage: bikes, maintenance items, and service logs enforce strict owner isolation and bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastNow();

  const bikeRef = aliceDb.collection("users").doc(ALICE_UID).collection("garage_bikes").doc("bike_1");
  await assertSucceeds(
    bikeRef.set({
      userId: ALICE_UID,
      name: "Tracer 9",
      makeModel: "Yamaha Tracer 9 GT",
      year: 2024,
      currentOdometerKm: 12450.0,
      engineCc: 890,
      tirePsiFront: 36,
      tirePsiRear: 42,
      isPrimary: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Cross-user read & write denied
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("garage_bikes").doc("bike_1").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("garage_bikes").doc("bike_bob").set({
      userId: BOB_UID,
      name: "Fake",
      makeModel: "Fake",
      year: 2024,
      currentOdometerKm: 100.0,
      isPrimary: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Odometer update succeeds for owner; arbitrary unallowed field fails
  await assertSucceeds(
    bikeRef.update({
      currentOdometerKm: 12610.0,
      updatedAt: pastNow(),
    })
  );
  await assertFails(
    bikeRef.update({
      currentOdometerKm: -10.0,
      updatedAt: pastNow(),
    })
  );

  // Maintenance reminder create & update
  const itemRef = aliceDb.collection("users").doc(ALICE_UID).collection("maintenance_items").doc("maint_oil");
  await assertSucceeds(
    itemRef.set({
      userId: ALICE_UID,
      bikeId: "bike_1",
      bikeName: "Tracer 9",
      taskType: "OIL_CHANGE",
      title: "Engine Oil & Filter Change",
      notes: "Yamalube 10W-40",
      intervalKm: 5000.0,
      lastServiceOdometerKm: 10000.0,
      nextDueOdometerKm: 15000.0,
      enabled: true,
      lastServicedAtMs: Date.now() - 86400000,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Service log is append-only (update denied)
  const logRef = aliceDb.collection("users").doc(ALICE_UID).collection("service_logs").doc("log_1");
  await assertSucceeds(
    logRef.set({
      userId: ALICE_UID,
      bikeId: "bike_1",
      bikeName: "Tracer 9",
      taskType: "OIL_CHANGE",
      title: "Engine Oil & Filter Change",
      odometerKm: 12610.0,
      costAmount: 68.5,
      shopOrDiy: "DIY Garage",
      notes: "Replaced OEM filter",
      performedAtMs: Date.now(),
      createdAt: ts,
    })
  );
  await assertFails(
    logRef.update({
      odometerKm: 99999.0,
    })
  );
});



