import { FirebaseApp, deleteApp, initializeApp } from "firebase/app";
import { Auth, connectAuthEmulator, createUserWithEmailAndPassword, getAuth } from "firebase/auth";
import { connectFunctionsEmulator, getFunctions, httpsCallable } from "firebase/functions";
import { App, deleteApp as deleteAdminApp, getApps, initializeApp as initializeAdminApp } from "firebase-admin/app";
import { Timestamp, getFirestore } from "firebase-admin/firestore";
import { afterAll, beforeAll, beforeEach, describe, expect, test } from "vitest";

const PROJECT_ID = "demo-booking-register";
const REGION = "asia-south1";
// Midnight 10 Jan 2030 in Asia/Kolkata, represented as an epoch value.
// This intentionally differs from UTC midnight to catch business-date identity drift.
const START = Date.UTC(2030, 0, 9, 18, 30);
const END = START + 86_400_000;
let adminApp: App;
let sequence = 0;
const clientApps: FirebaseApp[] = [];

function assertEmulatorOnly(): void {
  if (process.env.GCLOUD_PROJECT !== PROJECT_ID || !process.env.FIRESTORE_EMULATOR_HOST || !process.env.FIREBASE_AUTH_EMULATOR_HOST) {
    throw new Error("Safety stop: integration tests require the demo project and local emulators.");
  }
}

async function clearFirestore(): Promise<void> {
  assertEmulatorOnly();
  const response = await fetch(
    `http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents`,
    { method: "DELETE" }
  );
  if (!response.ok) throw new Error(`Failed to clear Firestore emulator: ${response.status}`);
}

async function createClient(): Promise<{ auth: Auth; call: (name: string, data: unknown) => Promise<unknown> }> {
  const id = ++sequence;
  const app = initializeApp({ projectId: PROJECT_ID, apiKey: "demo-api-key", authDomain: "localhost" }, `test-${id}`);
  clientApps.push(app);
  const auth = getAuth(app);
  connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
  await createUserWithEmailAndPassword(auth, `user-${id}@example.test`, "password123");
  const functions = getFunctions(app, REGION);
  connectFunctionsEmulator(functions, "127.0.0.1", 5001);
  return { auth, call: async (name, data) => (await httpsCallable(functions, name)(data)).data };
}

function unauthenticatedCaller(): (name: string, data: unknown) => Promise<unknown> {
  const id = ++sequence;
  const app = initializeApp({ projectId: PROJECT_ID, apiKey: "demo-api-key", authDomain: "localhost" }, `anonymous-${id}`);
  clientApps.push(app);
  const functions = getFunctions(app, REGION);
  connectFunctionsEmulator(functions, "127.0.0.1", 5001);
  return async (name, data) => (await httpsCallable(functions, name)(data)).data;
}

async function seedMembership(
  uid: string,
  options: { active?: boolean; expired?: boolean; role?: "OWNER" | "MANAGER" | "STAFF" } = {}
): Promise<void> {
  const db = getFirestore(adminApp);
  await db.doc("hotelAccounts/hotel-a").set({
    status: "ACTIVE",
    accessUntil: Timestamp.fromMillis(Date.now() + (options.expired ? -60_000 : 86_400_000)),
  });
  await db.doc(`hotelAccounts/hotel-a/members/${uid}`).set({
    uid,
    role: options.role ?? "STAFF",
    active: options.active ?? true,
  });
  await db.doc("hotels/hotel-a").set({ hotelRemoteId: "hotel-a", hotelName: "Hotel A" });
}

async function seedRoom(roomId: string, propertyRemoteId: string | null = "property-a"): Promise<void> {
  await getFirestore(adminApp).doc(`hotels/hotel-a/rooms/${roomId}`).set({
    hotelRemoteId: "hotel-a", propertyRemoteId, roomName: roomId, lifecycleStatus: "ACTIVE", isDeleted: false,
  });
}

async function seedCloudBooking(
  remoteId = "booking-a",
  overrides: Record<string, unknown> = {}
): Promise<void> {
  await getFirestore(adminApp).doc(`hotels/hotel-a/bookings/${remoteId}`).set({
    hotelRemoteId: "hotel-a",
    bookingUuid: remoteId,
    guestName: "Test Guest",
    sourceType: "DIRECT",
    bookingStatus: "RESERVED",
    cancellationSettlementStatus: "NOT_APPLICABLE",
    checkInMillis: START,
    checkOutMillis: END,
    roomRemoteIds: ["room-a"],
    isDeleted: false,
    revision: 1,
    ...overrides,
  });
}

function booking(remoteId: string, roomRemoteIds = ["room-a"], baseRevision = 0) {
  return {
    remoteId, bookingUuid: remoteId, baseRevision, guestName: "Test Guest", roomRemoteIds,
    checkInMillis: START, checkOutMillis: END, bookingStatus: "RESERVED", rate: 1000,
    receivable: 1000, grossCharges: 1000, roomRevenue: 952.38, propertyTax: 47.62,
    balance: 1000, updatedAt: START,
  };
}

function financialLine(remoteId: string, bookingRemoteId: string, roomRemoteId = "room-a", baseRevision = 0) {
  return {
    remoteId, bookingRemoteId, roomRemoteId, propertyRemoteId: "property-a", businessDateMillis: START,
    grossAmount: 1000, taxableAmount: 952.38, gstRatePercent: 5, gstAmount: 47.62,
    baseRevision, updatedAt: START,
  };
}

function payment(remoteId: string, overrides: Record<string, unknown> = {}) {
  return {
    remoteId, bookingRemoteId: "booking-a", paymentType: "PAYMENT", paymentCategory: "AUTO",
    amount: 1000, allocatedStayAmount: 600, allocatedFoodAmount: 200,
    allocatedServiceAmount: 100, allocatedDamageAmount: 50, unappliedAmount: 50,
    baseRevision: 0, updatedAt: START, ...overrides,
  };
}

function order(remoteId: string, baseRevision = 0, billRemoteId: string | null = null) {
  return {
    remoteId, baseRevision, billRemoteId, linkedFinalBillId: billRemoteId, guestName: "Test Guest",
    orderNumber: remoteId, status: billRemoteId ? "BILLED" : "OPEN", subtotal: 100,
    taxableAmount: 100, gstAmount: 5, totalAmount: 105, updatedAt: START,
  };
}

function orderItem(remoteId: string, orderRemoteId: string, baseRevision = 0) {
  return {
    remoteId, orderRemoteId, baseRevision, itemName: "Tea", quantity: 1, unitPrice: 100,
    lineSubtotal: 100, lineGst: 5, lineTotal: 105, updatedAt: START,
  };
}

function bill(remoteId: string, billNumber: string, baseRevision = 0) {
  return {
    remoteId, billNumber, baseRevision, billMillis: START, guestName: "Test Guest",
    subtotal: 100, taxableAmount: 100, gstAmount: 5, grandTotal: 105, updatedAt: START,
  };
}

function billItem(remoteId: string, billRemoteId: string, orderRemoteId = "") {
  return {
    remoteId, billRemoteId, orderRemoteId, baseRevision: 0, itemName: "Tea", quantity: 1,
    unitPrice: 100, lineSubtotal: 100, taxableAmount: 100, gstAmount: 5, lineTotal: 105, updatedAt: START,
  };
}

async function expectFunctionError(action: Promise<unknown>, code: string): Promise<void> {
  await expect(action).rejects.toMatchObject({ code: `functions/${code}` });
}

describe("Firebase callable Functions integration", () => {
  beforeAll(() => {
    assertEmulatorOnly();
    adminApp = getApps().find((app) => app.name === "integration-admin") ??
      initializeAdminApp({ projectId: PROJECT_ID }, "integration-admin");
  });
  beforeEach(clearFirestore);
  afterAll(async () => {
    await Promise.all(clientApps.map((app) => deleteApp(app)));
    if (adminApp) await deleteAdminApp(adminApp);
  });

  test("rejects unauthenticated requests", async () => {
    await expectFunctionError(unauthenticatedCaller()("applyBookingChangeSetServer", {
      hotelId: "hotel-a", operationId: "op", deviceId: "device", changeSet: {},
    }), "unauthenticated");
  }, 15_000);

  test("rejects users without active hotel membership", async () => {
    const client = await createClient();
    await expectFunctionError(client.call("applyBookingChangeSetServer", {
      hotelId: "hotel-a", operationId: "op", deviceId: "device", changeSet: {},
    }), "permission-denied");
  }, 15_000);

  test("first device claim activates that device", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid);

    const result = await client.call("claimMyDevice", {
      deviceId: "device-one",
      deviceName: "Test Phone One",
    }) as {
      allowed: boolean;
      deviceId: string;
      deviceStatus: string;
      decision: string;
    };

    expect(result.allowed).toBe(true);
    expect(result.deviceId).toBe("device-one");
    expect(result.deviceStatus).toBe("ACTIVE");
    expect(result.decision).toBe("ACTIVATE");

    const db = getFirestore(adminApp);

    const member = await db
      .doc(`hotelAccounts/hotel-a/members/${client.auth.currentUser!.uid}`)
      .get();

    expect(member.get("activeDeviceId")).toBe("device-one");

    const device = await db
      .doc(
        `hotelAccounts/hotel-a/members/${client.auth.currentUser!.uid}/devices/device-one`
      )
      .get();

    expect(device.exists).toBe(true);
    expect(device.get("status")).toBe("ACTIVE");
  }, 15_000);

  test("same device claim refreshes without creating another active device", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid);

    await client.call("claimMyDevice", {
      deviceId: "device-one",
      deviceName: "Test Phone One",
    });

    const result = await client.call("claimMyDevice", {
      deviceId: "device-one",
      deviceName: "Test Phone One",
    }) as {
      allowed: boolean;
      decision: string;
    };

    expect(result.allowed).toBe(true);
    expect(result.decision).toBe("REFRESH");

    const db = getFirestore(adminApp);

    const devices = await db
      .collection(
        `hotelAccounts/hotel-a/members/${client.auth.currentUser!.uid}/devices`
      )
      .get();

    expect(devices.size).toBe(1);
  }, 15_000);

  test("different device is blocked while first device remains active", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid);

    await client.call("claimMyDevice", {
      deviceId: "device-one",
      deviceName: "Test Phone One",
    });

    const result = await client.call("claimMyDevice", {
      deviceId: "device-two",
      deviceName: "Test Phone Two",
    }) as {
      allowed: boolean;
      reason: string;
    };

    expect(result.allowed).toBe(false);
    expect(result.reason).toBe("DEVICE_ALREADY_ACTIVE");

    const db = getFirestore(adminApp);

    const member = await db
      .doc(`hotelAccounts/hotel-a/members/${client.auth.currentUser!.uid}`)
      .get();

    expect(member.get("activeDeviceId")).toBe("device-one");

    const secondDevice = await db
      .doc(
        `hotelAccounts/hotel-a/members/${client.auth.currentUser!.uid}/devices/device-two`
      )
      .get();

    expect(secondDevice.exists).toBe(false);
  }, 15_000);
  test("active device logout releases the user for another device", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid);

    await client.call("claimMyDevice", {
      deviceId: "device-one",
      deviceName: "Test Phone One",
    });

    const logout = await client.call("logoutMyDevice", {
      hotelId: "hotel-a",
      deviceId: "device-one",
    }) as {
      released: boolean;
      deviceId: string;
      deviceStatus: string;
    };

    expect(logout.released).toBe(true);
    expect(logout.deviceId).toBe("device-one");
    expect(logout.deviceStatus).toBe("LOGGED_OUT");

    const db = getFirestore(adminApp);

    const memberAfterLogout = await db
      .doc(`hotelAccounts/hotel-a/members/${client.auth.currentUser!.uid}`)
      .get();

    expect(memberAfterLogout.get("activeDeviceId")).toBeUndefined();

    const oldDevice = await db
      .doc(
        `hotelAccounts/hotel-a/members/${client.auth.currentUser!.uid}/devices/device-one`
      )
      .get();

    expect(oldDevice.get("status")).toBe("LOGGED_OUT");

    const secondClaim = await client.call("claimMyDevice", {
      deviceId: "device-two",
      deviceName: "Test Phone Two",
    }) as {
      allowed: boolean;
      deviceId: string;
      decision: string;
    };

    expect(secondClaim.allowed).toBe(true);
    expect(secondClaim.deviceId).toBe("device-two");
    expect(secondClaim.decision).toBe("ACTIVATE");
  }, 15_000);

  test("stale device logout cannot release the newer active device", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid);

    await client.call("claimMyDevice", {
      deviceId: "device-one",
      deviceName: "Test Phone One",
    });

    await client.call("logoutMyDevice", {
      hotelId: "hotel-a",
      deviceId: "device-one",
    });

    await client.call("claimMyDevice", {
      deviceId: "device-two",
      deviceName: "Test Phone Two",
    });

    const staleLogout = await client.call("logoutMyDevice", {
      hotelId: "hotel-a",
      deviceId: "device-one",
    }) as {
      released: boolean;
      reason: string;
    };

    expect(staleLogout.released).toBe(false);
    expect(staleLogout.reason).toBe("NOT_ACTIVE_DEVICE");

    const db = getFirestore(adminApp);

    const member = await db
      .doc(`hotelAccounts/hotel-a/members/${client.auth.currentUser!.uid}`)
      .get();

    expect(member.get("activeDeviceId")).toBe("device-two");

    const activeDevice = await db
      .doc(
        `hotelAccounts/hotel-a/members/${client.auth.currentUser!.uid}/devices/device-two`
      )
      .get();

    expect(activeDevice.get("status")).toBe("ACTIVE");
  }, 15_000);

  test("booking change sets merge price and room changes without revision conflicts or payment duplication", async () => {
    const deviceA = await createClient();
    const deviceB = await createClient();
    await seedMembership(deviceA.auth.currentUser!.uid);
    await getFirestore(adminApp).doc(`hotelAccounts/hotel-a/members/${deviceB.auth.currentUser!.uid}`).set({
      uid: deviceB.auth.currentUser!.uid, role: "STAFF", active: true,
    });
    await seedRoom("H101", "property-a");
    await seedRoom("H102", "property-a");

    const createChange = {
      bookingRemoteId: "booking-command",
      create: true,
      setFields: {
        bookingUuid: "booking-command", guestName: "Command Guest", checkInMillis: START,
        checkOutMillis: END, bookingStatus: "RESERVED", pricingStatus: "CONFIRMED", grossCharges: 3000,
      },
      addRoomRemoteIds: ["H101"], removeRoomRemoteIds: [], rebuildFinancialLines: true,
      financialLineTemplate: { gstRatePercent: 5, cgstRatePercent: 2.5, sgstRatePercent: 2.5, source: "MANUAL" },
      financialLineRemoteIdsByKey: { [`H101|${START}`]: "device-a-provisional-H101" },
    };
    await deviceA.call("applyBookingChangeSetServer", {
      hotelId: "hotel-a", operationId: "create-command", deviceId: "device-a", changeSet: createChange,
    });
    await deviceA.call("saveBookingPaymentServer", {
      hotelId: "hotel-a", operationId: "advance-command", entity: payment("command-advance", {
        bookingRemoteId: "booking-command", paymentType: "ADVANCE", amount: 200,
        allocatedStayAmount: 200, allocatedFoodAmount: 0, allocatedServiceAmount: 0,
        allocatedDamageAmount: 0, unappliedAmount: 0,
      }),
    });
    await deviceA.call("applyBookingChangeSetServer", {
      hotelId: "hotel-a", operationId: "price-command", deviceId: "device-a",
      changeSet: { ...createChange, create: false, setFields: { grossCharges: 3200 }, addRoomRemoteIds: [] },
    });
    const roomPayload = {
      hotelId: "hotel-a", operationId: "room-command", deviceId: "device-a",
      changeSet: { ...createChange, create: false, setFields: {}, addRoomRemoteIds: ["H102"] },
    };
    await deviceA.call("applyBookingChangeSetServer", roomPayload);
    const duplicateRetry = await deviceA.call("applyBookingChangeSetServer", roomPayload) as Record<string, unknown>;
    expect(duplicateRetry.alreadyApplied).toBe(true);

    const db = getFirestore(adminApp);
    const cloudBooking = await db.doc("hotels/hotel-a/bookings/booking-command").get();
    expect(new Set(cloudBooking.get("roomRemoteIds"))).toEqual(new Set(["H101", "H102"]));
    expect(cloudBooking.get("grossCharges")).toBe(3200);
    const lines = await db.collection("hotels/hotel-a/bookingFinancialLines")
      .where("bookingRemoteId", "==", "booking-command").get();
    const activeLines = lines.docs.filter((line) => !line.get("isDeleted"));
    expect(activeLines.reduce((sum, line) => sum + line.get("grossAmount"), 0)).toBe(3200);
    expect(activeLines.some((line) => line.id === "device-a-provisional-H101")).toBe(true);
    expect(activeLines.filter((line) => line.get("roomRemoteId") === "H101" && line.get("businessDateMillis") === START)).toHaveLength(1);
    const payments = await db.collection("hotels/hotel-a/bookingPayments")
      .where("bookingRemoteId", "==", "booking-command").get();
    expect(payments.size).toBe(1);
    expect(payments.docs[0].get("amount")).toBe(200);
    expect(3200 - payments.docs[0].get("allocatedStayAmount")).toBe(3000);
    expect((await db.collection("hotels/hotel-a/bookingAuditEvents").get()).size).toBe(3);
  }, 15_000);

  test("booking change set rejects a real room-lock conflict without partial writes", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid);
    await seedRoom("H101", "property-a");
    const baseChange = {
      create: true,
      setFields: { guestName: "Guest", checkInMillis: START, checkOutMillis: END, grossCharges: 3000 },
      addRoomRemoteIds: ["H101"], removeRoomRemoteIds: [], rebuildFinancialLines: true,
      financialLineTemplate: { gstRatePercent: 5 }, financialLineRemoteIdsByKey: {},
    };
    await client.call("applyBookingChangeSetServer", {
      hotelId: "hotel-a", operationId: "first-room", deviceId: "device-a",
      changeSet: { ...baseChange, bookingRemoteId: "booking-first" },
    });
    await expectFunctionError(client.call("applyBookingChangeSetServer", {
      hotelId: "hotel-a", operationId: "blocked-room", deviceId: "device-b",
      changeSet: { ...baseChange, bookingRemoteId: "booking-blocked" },
    }), "already-exists");
    expect((await getFirestore(adminApp).doc("hotels/hotel-a/bookings/booking-blocked").get()).exists).toBe(false);
  }, 10_000);

  test("a missing cloud booking can be recovered with the same operation ID without partial writes", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid);
    await seedRoom("H101", "property-a");
    const db = getFirestore(adminApp);
    const operationId = "recover-missing-booking";
    const updateChange = {
      bookingRemoteId: "booking-recovered",
      create: false,
      setFields: { grossCharges: 3200 },
      addRoomRemoteIds: [],
      removeRoomRemoteIds: [],
      rebuildFinancialLines: true,
      financialLineTemplate: { gstRatePercent: 5 },
      financialLineRemoteIdsByKey: {},
    };

    await expectFunctionError(client.call("applyBookingChangeSetServer", {
      hotelId: "hotel-a", operationId, deviceId: "device-a", changeSet: updateChange,
    }), "not-found");
    expect((await db.doc("hotels/hotel-a/bookings/booking-recovered").get()).exists).toBe(false);
    expect((await db.doc(`hotels/hotel-a/appliedBookingChangeSets/${operationId}`).get()).exists).toBe(false);
    expect((await db.doc(`hotels/hotel-a/bookingAuditEvents/${operationId}`).get()).exists).toBe(false);

    const recovered = await client.call("applyBookingChangeSetServer", {
      hotelId: "hotel-a",
      operationId,
      deviceId: "device-a",
      changeSet: {
        ...updateChange,
        create: true,
        setFields: {
          bookingUuid: "booking-recovered",
          guestName: "Recovered Guest",
          checkInMillis: START,
          checkOutMillis: END,
          bookingStatus: "RESERVED",
          pricingStatus: "CONFIRMED",
          grossCharges: 3200,
        },
        addRoomRemoteIds: ["H101"],
        financialLineRemoteIdsByKey: {
          [`H101|${START}`]: "recovered-H101-line",
        },
      },
    }) as Record<string, unknown>;

    expect(recovered.alreadyApplied).toBe(false);
    const booking = await db.doc("hotels/hotel-a/bookings/booking-recovered").get();
    expect(booking.exists).toBe(true);
    expect(booking.get("grossCharges")).toBe(3200);
    expect(booking.get("roomRemoteIds")).toEqual(["H101"]);
    const lines = await db.collection("hotels/hotel-a/bookingFinancialLines")
      .where("bookingRemoteId", "==", "booking-recovered").get();
    expect(lines.docs.filter((line) => !line.get("isDeleted"))).toHaveLength(1);
    expect((await db.doc(`hotels/hotel-a/appliedBookingChangeSets/${operationId}`).get()).exists).toBe(true);
    expect((await db.doc(`hotels/hotel-a/bookingAuditEvents/${operationId}`).get()).exists).toBe(true);
  }, 10_000);

  test("a new cancellation without a receptionist reason remains rejected", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid);
    await seedRoom("room-a");
    await seedCloudBooking();
    const db = getFirestore(adminApp);

    await expectFunctionError(client.call("applyBookingChangeSetServer", {
      hotelId: "hotel-a",
      operationId: "missing-new-cancellation-reason",
      deviceId: "current-device",
      changeSet: {
        bookingRemoteId: "booking-a",
        create: false,
        setFields: {
          bookingStatus: "CANCELLED",
          cancellationSettlementStatus: "NOT_REQUIRED",
        },
        addRoomRemoteIds: [],
        removeRoomRemoteIds: [],
        rebuildFinancialLines: false,
        financialLineTemplate: null,
        financialLineRemoteIdsByKey: {},
      },
    }), "invalid-argument");

    expect((await db.doc("hotels/hotel-a/bookings/booking-a").get()).get("bookingStatus")).toBe("RESERVED");
    expect((await db.doc(
      "hotels/hotel-a/appliedBookingChangeSets/missing-new-cancellation-reason"
    ).get()).exists).toBe(false);
  });

  test("a full legacy cancelled aggregate missing its historical reason is recovered honestly", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid);
    await seedRoom("room-a");
    const db = getFirestore(adminApp);

    await client.call("applyBookingChangeSetServer", {
      hotelId: "hotel-a",
      operationId: "recover-legacy-cancellation",
      deviceId: "upgraded-device",
      changeSet: {
        bookingRemoteId: "legacy-cancelled-booking",
        create: true,
        setFields: {
          bookingUuid: "legacy-cancelled-booking",
          guestName: "Legacy Cancelled Guest",
          checkInMillis: START,
          checkOutMillis: END,
          bookingStatus: "CANCELLED",
          pricingStatus: "CONFIRMED",
          grossCharges: 3000,
          cancellationSettlementStatus: "NOT_REQUIRED",
          cancellationApprovedRefundAmount: 0,
          cancellationFeeAmount: 0,
          cancellationRefundBaselineAmount: 0,
        },
        addRoomRemoteIds: ["room-a"],
        removeRoomRemoteIds: [],
        rebuildFinancialLines: true,
        financialLineTemplate: { gstRatePercent: 5 },
        financialLineRemoteIdsByKey: {
          [`room-a|${START}`]: "legacy-cancelled-line",
        },
      },
    });

    const booking = await db.doc("hotels/hotel-a/bookings/legacy-cancelled-booking").get();
    expect(booking.get("bookingStatus")).toBe("CANCELLED");
    expect(booking.get("cancellationReason")).toBe("Legacy cancellation — reason not recorded");
    expect((await db.doc(
      "hotels/hotel-a/appliedBookingChangeSets/recover-legacy-cancellation"
    ).get()).exists).toBe(true);

    await client.call("applyBookingChangeSetServer", {
      hotelId: "hotel-a",
      operationId: "retry-same-legacy-settlement",
      deviceId: "upgraded-device",
      changeSet: {
        bookingRemoteId: "legacy-cancelled-booking",
        create: false,
        setFields: {
          cancellationSettlementStatus: "NOT_REQUIRED",
          cancellationSettlementOutcome: null,
          cancellationApprovedRefundAmount: 0,
          cancellationFeeAmount: 0,
          cancellationRefundBaselineAmount: 0,
        },
        addRoomRemoteIds: [],
        removeRoomRemoteIds: [],
        rebuildFinancialLines: false,
        financialLineTemplate: null,
        financialLineRemoteIdsByKey: {},
      },
    });
    const afterRetry = await db.doc("hotels/hotel-a/bookings/legacy-cancelled-booking").get();
    expect(afterRetry.get("cancellationSettlementStatus")).toBe("NOT_REQUIRED");
    expect(afterRetry.get("cancellationApprovedRefundAmount")).toBe(0);
    expect((await db.doc(
      "hotels/hotel-a/appliedBookingChangeSets/retry-same-legacy-settlement"
    ).get()).exists).toBe(true);
  });

  test("older cancellation command defaults safely to pending and a Direct decision becomes immutable", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid);
    await seedRoom("room-a");
    await seedCloudBooking();
    const base = {
      bookingRemoteId: "booking-a",
      create: false,
      addRoomRemoteIds: [],
      removeRoomRemoteIds: [],
      rebuildFinancialLines: false,
      financialLineTemplate: null,
      financialLineRemoteIdsByKey: {},
    };
    await client.call("applyBookingChangeSetServer", {
      hotelId: "hotel-a",
      operationId: "legacy-cancel-op",
      deviceId: "old-device",
      changeSet: {
        ...base,
        setFields: {
          bookingStatus: "CANCELLED",
          cancellationReason: "Guest cancelled",
        },
      },
    });
    const bookingRef = getFirestore(adminApp).doc("hotels/hotel-a/bookings/booking-a");
    expect((await bookingRef.get()).get("cancellationSettlementStatus")).toBe("PENDING");

    await client.call("applyBookingChangeSetServer", {
      hotelId: "hotel-a",
      operationId: "direct-decision-op",
      deviceId: "new-device",
      changeSet: {
        ...base,
        setFields: {
          cancellationSettlementStatus: "DECIDED",
          cancellationSettlementOutcome: "NO_REFUND",
          cancellationApprovedRefundAmount: 0,
          cancellationFeeAmount: 200,
          cancellationRefundBaselineAmount: 0,
        },
      },
    });
    expect((await bookingRef.get()).get("cancellationSettlementOutcome")).toBe("NO_REFUND");

    await expectFunctionError(client.call("applyBookingChangeSetServer", {
      hotelId: "hotel-a",
      operationId: "rewrite-decision-op",
      deviceId: "another-device",
      changeSet: {
        ...base,
        setFields: {
          cancellationSettlementStatus: "DECIDED",
          cancellationSettlementOutcome: "FULL_REFUND",
          cancellationApprovedRefundAmount: 200,
          cancellationFeeAmount: 0,
          cancellationRefundBaselineAmount: 0,
        },
      },
    }), "failed-precondition");
    expect((await bookingRef.get()).get("cancellationSettlementOutcome")).toBe("NO_REFUND");
    expect((await getFirestore(adminApp).doc(
      "hotels/hotel-a/appliedBookingChangeSets/rewrite-decision-op"
    ).get()).exists).toBe(false);
  });

  test("payment retry is idempotent and does not duplicate", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid);
    await seedCloudBooking();
    const payload = { hotelId: "hotel-a", operationId: "payment-op", entity: payment("payment-a") };
    const first = await client.call("saveBookingPaymentServer", payload) as Record<string, unknown>;
    const retry = await client.call("saveBookingPaymentServer", payload) as Record<string, unknown>;
    expect(first.alreadyApplied).toBe(false);
    expect(retry.alreadyApplied).toBe(true);
    expect((await getFirestore(adminApp).collection("hotels/hotel-a/bookingPayments").get()).size).toBe(1);
  });

  test("refund requires linkage and reverses original allocation", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid);
    await seedCloudBooking();
    await client.call("saveBookingPaymentServer", { hotelId: "hotel-a", operationId: "original-op", entity: payment("payment-original") });
    await expectFunctionError(client.call("saveBookingPaymentServer", {
      hotelId: "hotel-a", operationId: "missing-link", entity: payment("refund-bad", {
        paymentType: "REFUND", amount: 500, allocatedStayAmount: 500, allocatedFoodAmount: 0,
        allocatedServiceAmount: 0, allocatedDamageAmount: 0, unappliedAmount: 0,
      }),
    }), "invalid-argument");
    await client.call("saveBookingPaymentServer", {
      hotelId: "hotel-a", operationId: "refund-op", entity: payment("refund-good", {
        paymentType: "REFUND", originalPaymentRemoteId: "payment-original", amount: 500,
        allocatedStayAmount: 500, allocatedFoodAmount: 0, allocatedServiceAmount: 0,
        allocatedDamageAmount: 0, unappliedAmount: 0,
      }),
    });
    const refund = await getFirestore(adminApp).doc("hotels/hotel-a/bookingPayments/refund-good").get();
    expect(refund.get("originalPaymentRemoteId")).toBe("payment-original");
    expect(refund.get("allocatedStayAmount")).toBe(300);
    expect(refund.get("allocatedFoodAmount")).toBe(100);
    expect(refund.get("allocatedServiceAmount")).toBe(50);
    expect(refund.get("allocatedDamageAmount")).toBe(25);
    expect(refund.get("unappliedAmount")).toBe(25);
  });

  test("correction requires the original payment and reverses its full remaining allocation", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid);
    await seedCloudBooking();
    await client.call("saveBookingPaymentServer", {
      hotelId: "hotel-a",
      operationId: "correction-original-op",
      entity: payment("correction-original"),
    });

    await expectFunctionError(client.call("saveBookingPaymentServer", {
      hotelId: "hotel-a",
      operationId: "correction-missing-link-op",
      entity: payment("correction-missing-link", {
        paymentType: "ADJUSTMENT",
        amount: 1000,
        allocatedStayAmount: 1000,
        allocatedFoodAmount: 0,
        allocatedServiceAmount: 0,
        allocatedDamageAmount: 0,
        unappliedAmount: 0,
      }),
    }), "invalid-argument");

    await expectFunctionError(client.call("saveBookingPaymentServer", {
      hotelId: "hotel-a",
      operationId: "correction-partial-op",
      entity: payment("correction-partial", {
        paymentType: "ADJUSTMENT",
        originalPaymentRemoteId: "correction-original",
        amount: 500,
        allocatedStayAmount: 500,
        allocatedFoodAmount: 0,
        allocatedServiceAmount: 0,
        allocatedDamageAmount: 0,
        unappliedAmount: 0,
      }),
    }), "failed-precondition");

    await client.call("saveBookingPaymentServer", {
      hotelId: "hotel-a",
      operationId: "correction-full-op",
      entity: payment("correction-full", {
        paymentType: "ADJUSTMENT",
        originalPaymentRemoteId: "correction-original",
        amount: 1000,
        allocatedStayAmount: 1000,
        allocatedFoodAmount: 0,
        allocatedServiceAmount: 0,
        allocatedDamageAmount: 0,
        unappliedAmount: 0,
      }),
    });

    const correction = await getFirestore(adminApp)
      .doc("hotels/hotel-a/bookingPayments/correction-full")
      .get();
    expect(correction.get("originalPaymentRemoteId")).toBe("correction-original");
    expect(correction.get("allocatedStayAmount")).toBe(600);
    expect(correction.get("allocatedFoodAmount")).toBe(200);
    expect(correction.get("allocatedServiceAmount")).toBe(100);
    expect(correction.get("allocatedDamageAmount")).toBe(50);
    expect(correction.get("unappliedAmount")).toBe(50);
  });

  test("correction safely reverses a legacy payment without explicit allocation fields", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid);
    await seedCloudBooking();
    const db = getFirestore(adminApp);
    await db.doc("hotels/hotel-a/bookingPayments/legacy-original").set({
      hotelRemoteId: "hotel-a",
      bookingRemoteId: "booking-a",
      paymentType: "PAYMENT",
      paymentCategory: "STAY",
      amount: 2000,
      allocatedStayAmount: 0,
      allocatedFoodAmount: 0,
      allocatedServiceAmount: 0,
      allocatedDamageAmount: 0,
      unappliedAmount: 0,
      isDeleted: false,
      revision: 1,
      updatedAt: START,
    });

    await client.call("saveBookingPaymentServer", {
      hotelId: "hotel-a",
      operationId: "legacy-correction-op",
      entity: payment("legacy-correction", {
        paymentType: "ADJUSTMENT",
        originalPaymentRemoteId: "legacy-original",
        paymentCategory: "STAY",
        amount: 2000,
        allocatedStayAmount: 2000,
        allocatedFoodAmount: 0,
        allocatedServiceAmount: 0,
        allocatedDamageAmount: 0,
        unappliedAmount: 0,
      }),
    });
    const correction = await db
      .doc("hotels/hotel-a/bookingPayments/legacy-correction")
      .get();
    expect(correction.get("allocatedStayAmount")).toBe(2000);
    expect(correction.get("unappliedAmount")).toBe(0);
  });

  test("cancelled booking accepts only an approved Direct refund and leaves rejected writes absent", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid);
    await seedCloudBooking();
    await client.call("saveBookingPaymentServer", {
      hotelId: "hotel-a",
      operationId: "cancel-original-op",
      entity: payment("cancel-original"),
    });
    const db = getFirestore(adminApp);
    const bookingRef = db.doc("hotels/hotel-a/bookings/booking-a");
    await bookingRef.update({
      bookingStatus: "CANCELLED",
      cancellationReason: "Guest cancelled",
      cancellationSettlementStatus: "PENDING",
    });

    await expectFunctionError(client.call("saveBookingPaymentServer", {
      hotelId: "hotel-a",
      operationId: "cancel-new-payment-op",
      entity: payment("cancel-new-payment"),
    }), "failed-precondition");
    expect((await db.doc("hotels/hotel-a/bookingPayments/cancel-new-payment").get()).exists).toBe(false);

    const refundEntity = payment("cancel-refund", {
      paymentType: "REFUND",
      originalPaymentRemoteId: "cancel-original",
      amount: 400,
      allocatedStayAmount: 400,
      allocatedFoodAmount: 0,
      allocatedServiceAmount: 0,
      allocatedDamageAmount: 0,
      unappliedAmount: 0,
    });
    await expectFunctionError(client.call("saveBookingPaymentServer", {
      hotelId: "hotel-a",
      operationId: "cancel-pending-refund-op",
      entity: refundEntity,
    }), "failed-precondition");
    expect((await db.doc("hotels/hotel-a/bookingPayments/cancel-refund").get()).exists).toBe(false);

    await bookingRef.update({
      cancellationSettlementStatus: "DECIDED",
      cancellationSettlementOutcome: "PARTIAL_REFUND",
      cancellationApprovedRefundAmount: 500,
      cancellationRefundBaselineAmount: 0,
      cancellationFeeAmount: 500,
    });
    await client.call("saveBookingPaymentServer", {
      hotelId: "hotel-a",
      operationId: "cancel-approved-refund-op",
      entity: refundEntity,
    });
    expect((await db.doc("hotels/hotel-a/bookingPayments/cancel-refund").get()).exists).toBe(true);

    await expectFunctionError(client.call("saveBookingPaymentServer", {
      hotelId: "hotel-a",
      operationId: "cancel-excess-refund-op",
      entity: payment("cancel-excess-refund", {
        paymentType: "REFUND",
        originalPaymentRemoteId: "cancel-original",
        amount: 200,
        allocatedStayAmount: 200,
        allocatedFoodAmount: 0,
        allocatedServiceAmount: 0,
        allocatedDamageAmount: 0,
        unappliedAmount: 0,
      }),
    }), "failed-precondition");
    expect((await db.doc("hotels/hotel-a/bookingPayments/cancel-excess-refund").get()).exists).toBe(false);
  });

  test("cancelled booking rejects new charges food and final bill without partial documents", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid);
    await seedCloudBooking("booking-a", {
      bookingStatus: "CANCELLED",
      cancellationReason: "Guest cancelled",
      cancellationSettlementStatus: "PENDING",
    });
    const db = getFirestore(adminApp);

    await expectFunctionError(client.call("saveBookingAccountingChargeServer", {
      hotelId: "hotel-a",
      operationId: "cancel-charge-op",
      entity: {
        remoteId: "cancel-charge",
        bookingRemoteId: "booking-a",
        chargeType: "SERVICE_CHARGE",
        amount: 100,
        description: "Laundry",
        baseRevision: 0,
      },
    }), "failed-precondition");
    expect((await db.doc("hotels/hotel-a/bookingAccountingCharges/cancel-charge").get()).exists).toBe(false);

    await expectFunctionError(client.call("saveFoodOrderAggregateServer", {
      hotelId: "hotel-a",
      operationId: "cancel-food-op",
      order: { ...order("cancel-order"), bookingRemoteId: "booking-a" },
      orderItems: [orderItem("cancel-order-item", "cancel-order")],
    }), "failed-precondition");
    expect((await db.doc("hotels/hotel-a/foodOrders/cancel-order").get()).exists).toBe(false);
    expect((await db.doc("hotels/hotel-a/foodOrderItems/cancel-order-item").get()).exists).toBe(false);

    const finalBillId = "booking-a_final_bill_test";
    await expectFunctionError(client.call("saveFoodBillAggregateServer", {
      hotelId: "hotel-a",
      operationId: "cancel-final-bill-op",
      bill: bill(finalBillId, "INV-CANCELLED"),
      billItems: [billItem("cancel-final-item", finalBillId)],
      orders: [],
      orderItems: [],
      accountingCharges: [],
    }), "failed-precondition");
    expect((await db.doc(`hotels/hotel-a/foodBills/${finalBillId}`).get()).exists).toBe(false);
    expect((await db.doc("hotels/hotel-a/foodBillItems/cancel-final-item").get()).exists).toBe(false);
  }, 15_000);

  test("food-order aggregate is atomic and idempotent", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid);
    const payload = { hotelId: "hotel-a", operationId: "order-op", order: order("order-a"), orderItems: [orderItem("order-item-a", "order-a")] };
    const first = await client.call("saveFoodOrderAggregateServer", payload) as Record<string, unknown>;
    const retry = await client.call("saveFoodOrderAggregateServer", payload) as Record<string, unknown>;
    expect(first.alreadyApplied).toBe(false);
    expect(retry.alreadyApplied).toBe(true);
    const db = getFirestore(adminApp);
    expect((await db.doc("hotels/hotel-a/foodOrders/order-a").get()).exists).toBe(true);
    expect((await db.doc("hotels/hotel-a/foodOrderItems/order-item-a").get()).exists).toBe(true);
  });

  test("failed food-order transaction leaves no partial order", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid);
    const db = getFirestore(adminApp);
    await db.doc("hotels/hotel-a/foodOrderItems/order-item-a").set({ revision: 3, lineTotal: 999 });
    await expectFunctionError(client.call("saveFoodOrderAggregateServer", {
      hotelId: "hotel-a", operationId: "order-fail", order: order("order-a"), orderItems: [orderItem("order-item-a", "order-a")],
    }), "aborted");
    expect((await db.doc("hotels/hotel-a/foodOrders/order-a").get()).exists).toBe(false);
    expect((await db.doc("hotels/hotel-a/foodOrderItems/order-item-a").get()).get("lineTotal")).toBe(999);
  });

  test("food-bill aggregate writes all linked documents atomically", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid);
    await client.call("saveFoodBillAggregateServer", {
      hotelId: "hotel-a", operationId: "bill-op", bill: bill("bill-a", "INV-0001"),
      billItems: [billItem("bill-item-a", "bill-a", "order-a")], orders: [order("order-a", 0, "bill-a")],
      orderItems: [orderItem("order-item-a", "order-a")],
      accountingCharges: [{ remoteId: "charge-a", bookingRemoteId: "booking-a", linkedFinalBillId: "bill-a", chargeType: "SERVICE_CHARGE", amount: 50, description: "Laundry", baseRevision: 0 }],
    });
    const db = getFirestore(adminApp);
    for (const path of ["foodBills/bill-a", "foodBillItems/bill-item-a", "foodOrders/order-a", "foodOrderItems/order-item-a", "bookingAccountingCharges/charge-a"]) {
      expect((await db.doc(`hotels/hotel-a/${path}`).get()).exists).toBe(true);
    }
  });

  test("duplicate order billing is rejected without a partial second bill", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid);
    await client.call("saveFoodBillAggregateServer", {
      hotelId: "hotel-a", operationId: "bill-one-op", bill: bill("bill-one", "INV-0001"),
      billItems: [billItem("bill-item-one", "bill-one", "order-a")], orders: [order("order-a", 0, "bill-one")], orderItems: [], accountingCharges: [],
    });
    await expectFunctionError(client.call("saveFoodBillAggregateServer", {
      hotelId: "hotel-a", operationId: "bill-two-op", bill: bill("bill-two", "INV-0002"),
      billItems: [billItem("bill-item-two", "bill-two", "order-a")], orders: [order("order-a", 1, "bill-two")], orderItems: [], accountingCharges: [],
    }), "already-exists");
    const db = getFirestore(adminApp);
    expect((await db.doc("hotels/hotel-a/foodBills/bill-two").get()).exists).toBe(false);
    expect((await db.doc("hotels/hotel-a/foodBillItems/bill-item-two").get()).exists).toBe(false);
  });

  test("duplicate bill number is rejected without partial documents", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid);
    await client.call("saveFoodBillAggregateServer", {
      hotelId: "hotel-a", operationId: "bill-one-op", bill: bill("bill-one", "INV-0001"),
      billItems: [billItem("bill-item-one", "bill-one")], orders: [], orderItems: [], accountingCharges: [],
    });
    await expectFunctionError(client.call("saveFoodBillAggregateServer", {
      hotelId: "hotel-a", operationId: "bill-duplicate-op", bill: bill("bill-two", "INV-0001"),
      billItems: [billItem("bill-item-two", "bill-two")], orders: [], orderItems: [], accountingCharges: [],
    }), "already-exists");
    const db = getFirestore(adminApp);
    expect((await db.doc("hotels/hotel-a/foodBills/bill-two").get()).exists).toBe(false);
    expect((await db.doc("hotels/hotel-a/foodBillItems/bill-item-two").get()).exists).toBe(false);
  });

  test("server detects a financial edit even when the client rebuild flag is false", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid);
    await seedRoom("room-a");
    await seedCloudBooking("booking-a", { grossCharges: 1000 });
    const db = getFirestore(adminApp);
    await db.doc("hotels/hotel-a/bookingFinancialLines/line-a").set(
      financialLine("line-a", "booking-a")
    );
    await db.doc("hotels/hotel-a/foodBills/booking-a_final_bill_issued").set({
      hotelRemoteId: "hotel-a",
      billNumber: "INV-LOCKED",
      isDeleted: false,
    });

    await expectFunctionError(client.call("applyBookingChangeSetServer", {
      hotelId: "hotel-a",
      operationId: "bypass-final-bill-lock",
      deviceId: "modified-client",
      changeSet: {
        bookingRemoteId: "booking-a",
        create: false,
        setFields: { grossCharges: 2000 },
        addRoomRemoteIds: [],
        removeRoomRemoteIds: [],
        rebuildFinancialLines: false,
      },
    }), "failed-precondition");

    expect((await db.doc("hotels/hotel-a/bookings/booking-a").get()).get("grossCharges")).toBe(1000);
    expect((await db.doc("hotels/hotel-a/appliedBookingChangeSets/bypass-final-bill-lock").get()).exists)
      .toBe(false);
  });

  test("non-financial guest correction remains allowed after final billing", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid);
    await seedRoom("room-a");
    await seedCloudBooking("booking-a", { grossCharges: 1000 });
    const db = getFirestore(adminApp);
    await db.doc("hotels/hotel-a/foodBills/booking-a_final_bill_issued").set({
      hotelRemoteId: "hotel-a",
      billNumber: "INV-LOCKED",
      isDeleted: false,
    });

    await client.call("applyBookingChangeSetServer", {
      hotelId: "hotel-a",
      operationId: "guest-name-correction",
      deviceId: "device-a",
      changeSet: {
        bookingRemoteId: "booking-a",
        create: false,
        setFields: { guestName: "Corrected Guest" },
        addRoomRemoteIds: [],
        removeRoomRemoteIds: [],
        rebuildFinancialLines: false,
      },
    });

    expect((await db.doc("hotels/hotel-a/bookings/booking-a").get()).get("guestName"))
      .toBe("Corrected Guest");
  });

  test("only owner or manager may change room lifecycle", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid, { role: "STAFF" });
    await seedRoom("room-a");
    await expectFunctionError(client.call("changeRoomLifecycleServer", {
      hotelId: "hotel-a",
      operationId: "staff-room-change",
      roomRemoteId: "room-a",
      action: "DISABLE",
      reason: "Maintenance",
    }), "permission-denied");
  });

  test("room with no history can be hard-deleted idempotently", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid, { role: "MANAGER" });
    await seedRoom("room-a");
    const payload = {
      hotelId: "hotel-a",
      operationId: "delete-unused-room",
      roomRemoteId: "room-a",
      action: "DELETE",
    };
    const first = await client.call("changeRoomLifecycleServer", payload) as Record<string, unknown>;
    const retry = await client.call("changeRoomLifecycleServer", payload) as Record<string, unknown>;
    expect(first.deleted).toBe(true);
    expect(retry.alreadyApplied).toBe(true);
    expect((await getFirestore(adminApp).doc("hotels/hotel-a/rooms/room-a").get()).exists).toBe(false);
  });

  test("room history blocks hard delete without partial audit documents", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid, { role: "MANAGER" });
    await seedRoom("room-a");
    await seedCloudBooking();
    const db = getFirestore(adminApp);

    await expectFunctionError(client.call("changeRoomLifecycleServer", {
      hotelId: "hotel-a",
      operationId: "delete-used-room",
      roomRemoteId: "room-a",
      action: "DELETE",
    }), "failed-precondition");

    expect((await db.doc("hotels/hotel-a/rooms/room-a").get()).exists).toBe(true);
    expect((await db.doc("hotels/hotel-a/roomLifecycleAuditEvents/delete-used-room").get()).exists)
      .toBe(false);
    expect((await db.doc("hotels/hotel-a/appliedRoomLifecycleOperations/delete-used-room").get()).exists)
      .toBe(false);
  });

  test("active or future booking blocks room disable and retirement", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid, { role: "MANAGER" });
    await seedRoom("room-a");
    await seedCloudBooking();

    for (const action of ["DISABLE", "RETIRE"]) {
      await expectFunctionError(client.call("changeRoomLifecycleServer", {
        hotelId: "hotel-a",
        operationId: `blocked-${action.toLowerCase()}`,
        roomRemoteId: "room-a",
        action,
        reason: "Maintenance",
      }), "failed-precondition");
    }
    expect((await getFirestore(adminApp).doc("hotels/hotel-a/rooms/room-a").get()).get("lifecycleStatus"))
      .toBe("ACTIVE");
  });

  test("cancelled future booking no longer blocks room retirement", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid, { role: "MANAGER" });
    await seedRoom("room-a");
    await seedCloudBooking("cancelled-future", {
      bookingStatus: "CANCELLED",
      cancellationReason: "Guest cancelled",
      cancellationSettlementStatus: "NOT_REQUIRED",
    });

    await client.call("changeRoomLifecycleServer", {
      hotelId: "hotel-a",
      operationId: "retire-after-cancellation",
      roomRemoteId: "room-a",
      action: "RETIRE",
      reason: "Permanent renovation",
    });

    expect((await getFirestore(adminApp).doc("hotels/hotel-a/rooms/room-a").get()).get("lifecycleStatus"))
      .toBe("RETIRED");
  });

  test("unbilled past booking blocks room retirement", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid, { role: "MANAGER" });
    await seedRoom("room-a");
    const pastCheckout = Date.now() - 86_400_000;
    await seedCloudBooking("unbilled-past", {
      checkInMillis: pastCheckout - 86_400_000,
      checkOutMillis: pastCheckout,
      bookingStatus: "CHECKED_OUT",
    });

    await expectFunctionError(client.call("changeRoomLifecycleServer", {
      hotelId: "hotel-a",
      operationId: "retire-unbilled-past",
      roomRemoteId: "room-a",
      action: "RETIRE",
      reason: "Permanent renovation",
    }), "failed-precondition");

    expect((await getFirestore(adminApp).doc("hotels/hotel-a/rooms/room-a").get()).get("lifecycleStatus"))
      .toBe("ACTIVE");
  });

  test("room with only past billed history can be retired and remains in history", async () => {
    const client = await createClient();
    await seedMembership(client.auth.currentUser!.uid, { role: "MANAGER" });
    await seedRoom("room-a");
    const pastCheckout = Date.now() - 86_400_000;
    await seedCloudBooking("past-booking", {
      checkInMillis: pastCheckout - 86_400_000,
      checkOutMillis: pastCheckout,
      bookingStatus: "CHECKED_OUT",
    });
    const db = getFirestore(adminApp);
    await db.doc("hotels/hotel-a/foodBills/past-booking_final_bill_issued").set({
      hotelRemoteId: "hotel-a",
      billNumber: "INV-PAST",
      isDeleted: false,
    });

    await client.call("changeRoomLifecycleServer", {
      hotelId: "hotel-a",
      operationId: "retire-past-room",
      roomRemoteId: "room-a",
      action: "RETIRE",
      reason: "Permanent renovation",
    });

    const room = await db.doc("hotels/hotel-a/rooms/room-a").get();
    expect(room.exists).toBe(true);
    expect(room.get("lifecycleStatus")).toBe("RETIRED");
    expect(room.get("lifecycleReason")).toBe("Permanent renovation");
    expect((await db.doc("hotels/hotel-a/bookings/past-booking").get()).exists).toBe(true);
  });
});
