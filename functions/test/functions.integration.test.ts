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

async function seedMembership(uid: string, options: { active?: boolean; expired?: boolean } = {}): Promise<void> {
  const db = getFirestore(adminApp);
  await db.doc("hotelAccounts/hotel-a").set({
    status: "ACTIVE",
    accessUntil: Timestamp.fromMillis(Date.now() + (options.expired ? -60_000 : 86_400_000)),
  });
  await db.doc(`hotelAccounts/hotel-a/members/${uid}`).set({ uid, role: "STAFF", active: options.active ?? true });
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
  });

  test("rejects users without active hotel membership", async () => {
    const client = await createClient();
    await expectFunctionError(client.call("applyBookingChangeSetServer", {
      hotelId: "hotel-a", operationId: "op", deviceId: "device", changeSet: {},
    }), "permission-denied");
  });

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
});
