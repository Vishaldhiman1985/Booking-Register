import { FirebaseApp, deleteApp, initializeApp } from "firebase/app";
import { Auth, connectAuthEmulator, createUserWithEmailAndPassword, getAuth } from "firebase/auth";
import { connectFunctionsEmulator, getFunctions, httpsCallable } from "firebase/functions";
import { App, deleteApp as deleteAdminApp, initializeApp as initializeAdminApp } from "firebase-admin/app";
import { Timestamp, getFirestore } from "firebase-admin/firestore";
import { afterAll, beforeAll, describe, expect, test } from "vitest";

const PROJECT_ID = "demo-booking-register";
const REGION = "asia-south1";
let adminApp: App;
let sequence = 0;
const clientApps: FirebaseApp[] = [];

function assertEmulatorOnly(): void {
  if (
    process.env.GCLOUD_PROJECT !== PROJECT_ID ||
    !process.env.FIRESTORE_EMULATOR_HOST ||
    !process.env.FIREBASE_AUTH_EMULATOR_HOST
  ) {
    throw new Error("Safety stop: OTA settlement tests require local Firebase emulators.");
  }
}

async function createClient(): Promise<{
  auth: Auth;
  call: (name: string, data: unknown) => Promise<any>;
}> {
  const id = ++sequence;
  const app = initializeApp(
    { projectId: PROJECT_ID, apiKey: "demo-api-key", authDomain: "localhost" },
    `ota-settlement-${id}`
  );
  clientApps.push(app);
  const auth = getAuth(app);
  connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
  await createUserWithEmailAndPassword(auth, `ota-${id}@example.test`, "password123");
  const functions = getFunctions(app, REGION);
  connectFunctionsEmulator(functions, "127.0.0.1", 5001);
  return {
    auth,
    call: async (name, data) => (await httpsCallable(functions, name)(data)).data,
  };
}

async function seedHotelAccess(hotelId: string, uid: string): Promise<void> {
  const db = getFirestore(adminApp);
  await db.doc(`hotelAccounts/${hotelId}`).set({
    status: "ACTIVE",
    accessUntil: Timestamp.fromMillis(Date.now() + 86_400_000),
  });
  await db.doc(`hotelAccounts/${hotelId}/members/${uid}`).set({
    uid,
    role: "STAFF",
    active: true,
  });
  await db.doc(`hotels/${hotelId}`).set({ hotelRemoteId: hotelId, hotelName: hotelId });
}

async function seedSource(
  hotelId: string,
  sourceRemoteId: string,
  propertyRemoteId: string
): Promise<void> {
  await getFirestore(adminApp)
    .doc(`hotels/${hotelId}/bookingSources/${sourceRemoteId}`)
    .set({
      hotelRemoteId: hotelId,
      propertyRemoteId,
      sourceName: "Agoda",
      sourceType: "OTA",
      isActive: true,
      isDeleted: false,
    });
}

async function seedBooking(
  hotelId: string,
  bookingRemoteId: string,
  propertyRemoteId: string,
  sourceRemoteId: string,
  expectedPayout: number
): Promise<void> {
  await getFirestore(adminApp)
    .doc(`hotels/${hotelId}/bookings/${bookingRemoteId}`)
    .set({
      hotelRemoteId: hotelId,
      propertyRemoteId,
      bookingUuid: bookingRemoteId,
      guestName: bookingRemoteId,
      sourceName: "Agoda",
      sourceRemoteId,
      sourceType: "OTA",
      bookingStatus: "RESERVED",
      expectedPayout,
      receivable: expectedPayout,
      rate: expectedPayout,
      isDeleted: false,
      revision: 1,
    });
}

async function expectFunctionError(action: Promise<unknown>, code: string): Promise<void> {
  await expect(action).rejects.toMatchObject({ code: `functions/${code}` });
}

describe.sequential("Atomic OTA settlement", () => {
  beforeAll(() => {
    assertEmulatorOnly();
    adminApp = initializeAdminApp({ projectId: PROJECT_ID }, `ota-admin-${Date.now()}`);
  });

  afterAll(async () => {
    await Promise.all(clientApps.map((app) => deleteApp(app)));
    await deleteAdminApp(adminApp);
  });

  test("records only user-selected Agoda bookings and is idempotent", async () => {
    const client = await createClient();
    const hotelId = `hotel-ota-selected-${sequence}`;
    const propertyId = "property-a";
    const sourceId = "agoda-a";
    await seedHotelAccess(hotelId, client.auth.currentUser!.uid);
    await seedSource(hotelId, sourceId, propertyId);
    await seedBooking(hotelId, "booking-1", propertyId, sourceId, 10_000);
    await seedBooking(hotelId, "booking-2", propertyId, sourceId, 14_000);
    await seedBooking(hotelId, "booking-3", propertyId, sourceId, 5_000);

    const request = {
      hotelId,
      operationId: "ota_selected_operation_001",
      propertyRemoteId: propertyId,
      sourceRemoteId: sourceId,
      sourceName: "Agoda",
      settlementMillis: Date.now(),
      settlementReference: "UTR-123",
      selections: [
        { bookingRemoteId: "booking-1", expectedOutstanding: 10_000 },
        { bookingRemoteId: "booking-2", expectedOutstanding: 14_000 },
      ],
    };

    const first = await client.call("recordOtaSettlementServer", request);
    expect(first.totalAmount).toBe(24_000);
    expect(first.bookingCount).toBe(2);
    expect(first.alreadyApplied).toBe(false);

    const db = getFirestore(adminApp);
    const payments = await db.collection(`hotels/${hotelId}/bookingPayments`).get();
    expect(payments.size).toBe(2);
    expect(payments.docs.every((doc) => doc.get("method") === "OTA_SETTLEMENT")).toBe(true);
    expect(payments.docs.reduce((sum, doc) => sum + Number(doc.get("amount") || 0), 0)).toBe(24_000);

    const unselectedPayment = payments.docs.find((doc) => doc.get("bookingRemoteId") === "booking-3");
    expect(unselectedPayment).toBeUndefined();

    const second = await client.call("recordOtaSettlementServer", request);
    expect(second.alreadyApplied).toBe(true);
    const paymentsAfterRetry = await db.collection(`hotels/${hotelId}/bookingPayments`).get();
    expect(paymentsAfterRetry.size).toBe(2);
  }, 20_000);

  test("stale client outstanding aborts the whole settlement before any new payment is written", async () => {
    const client = await createClient();
    const hotelId = `hotel-ota-stale-${sequence}`;
    const propertyId = "property-a";
    const sourceId = "agoda-a";
    await seedHotelAccess(hotelId, client.auth.currentUser!.uid);
    await seedSource(hotelId, sourceId, propertyId);
    await seedBooking(hotelId, "booking-stale", propertyId, sourceId, 10_000);

    const db = getFirestore(adminApp);
    await db.doc(`hotels/${hotelId}/bookingPayments/existing-payment`).set({
      hotelRemoteId: hotelId,
      bookingRemoteId: "booking-stale",
      paymentType: "PAYMENT",
      paymentCategory: "STAY",
      amount: 4_000,
      allocatedStayAmount: 4_000,
      allocatedFoodAmount: 0,
      allocatedServiceAmount: 0,
      allocatedDamageAmount: 0,
      unappliedAmount: 0,
      isDeleted: false,
      revision: 1,
    });

    await expectFunctionError(
      client.call("recordOtaSettlementServer", {
        hotelId,
        operationId: "ota_stale_operation_001",
        propertyRemoteId: propertyId,
        sourceRemoteId: sourceId,
        sourceName: "Agoda",
        selections: [{ bookingRemoteId: "booking-stale", expectedOutstanding: 10_000 }],
      }),
      "aborted"
    );

    const payments = await db.collection(`hotels/${hotelId}/bookingPayments`).get();
    expect(payments.size).toBe(1);
    const settlements = await db.collection(`hotels/${hotelId}/otaSettlements`).get();
    expect(settlements.empty).toBe(true);
  }, 20_000);

  test("cross-property booking rejects the entire batch", async () => {
    const client = await createClient();
    const hotelId = `hotel-ota-property-${sequence}`;
    const sourceId = "agoda-a";
    await seedHotelAccess(hotelId, client.auth.currentUser!.uid);
    await seedSource(hotelId, sourceId, "property-a");
    await seedBooking(hotelId, "booking-a", "property-a", sourceId, 5_000);
    await seedBooking(hotelId, "booking-b", "property-b", sourceId, 7_000);

    await expectFunctionError(
      client.call("recordOtaSettlementServer", {
        hotelId,
        operationId: "ota_property_operation_001",
        propertyRemoteId: "property-a",
        sourceRemoteId: sourceId,
        sourceName: "Agoda",
        selections: [
          { bookingRemoteId: "booking-a", expectedOutstanding: 5_000 },
          { bookingRemoteId: "booking-b", expectedOutstanding: 7_000 },
        ],
      }),
      "failed-precondition"
    );

    const db = getFirestore(adminApp);
    const payments = await db.collection(`hotels/${hotelId}/bookingPayments`).get();
    expect(payments.empty).toBe(true);
    const settlements = await db.collection(`hotels/${hotelId}/otaSettlements`).get();
    expect(settlements.empty).toBe(true);
  }, 20_000);
});