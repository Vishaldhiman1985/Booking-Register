import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import {
  RulesTestEnvironment,
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import { Timestamp, deleteDoc, doc, getDoc, setDoc } from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, test } from "vitest";

const PROJECT_ID = "demo-booking-register";
let env: RulesTestEnvironment;

function assertEmulatorOnly(): void {
  if (process.env.GCLOUD_PROJECT !== PROJECT_ID || !process.env.FIRESTORE_EMULATOR_HOST) {
    throw new Error("Safety stop: Rules tests require the demo project and Firestore emulator.");
  }
}

async function seedHotel(
  hotelId: string,
  uid: string,
  options: { active?: boolean; role?: string; expired?: boolean } = {}
): Promise<void> {
  await env.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "hotelAccounts", hotelId), {
      status: "ACTIVE",
      accessUntil: Timestamp.fromMillis(Date.now() + (options.expired ? -60_000 : 86_400_000)),
    });
    await setDoc(doc(db, "hotelAccounts", hotelId, "members", uid), {
      active: options.active ?? true,
      role: options.role ?? "STAFF",
    });
    await setDoc(doc(db, "hotels", hotelId), { hotelRemoteId: hotelId, hotelName: hotelId });
    await setDoc(doc(db, "hotels", hotelId, "rooms", "room-existing"), {
      hotelRemoteId: hotelId,
      roomName: "Existing",
    });
  });
}

describe("firestore.rules", () => {
  beforeAll(async () => {
    assertEmulatorOnly();
    env = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: {
        host: "127.0.0.1",
        port: 8080,
        rules: readFileSync(resolve(__dirname, "../../firestore.rules"), "utf8"),
      },
    });
  });

  beforeEach(async () => env.clearFirestore());
  afterAll(async () => env.cleanup());

  test("unauthenticated access is denied", async () => {
    await seedHotel("hotel-a", "owner-a", { role: "OWNER" });
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), "hotels", "hotel-a")));
  });

  test("inactive member is denied", async () => {
    await seedHotel("hotel-a", "inactive", { active: false });
    await assertFails(getDoc(doc(env.authenticatedContext("inactive").firestore(), "hotels", "hotel-a")));
  });

  test("expired hotel account is denied", async () => {
    await seedHotel("hotel-a", "expired", { expired: true });
    await assertFails(getDoc(doc(env.authenticatedContext("expired").firestore(), "hotels", "hotel-a")));
  });

  test("Hotel A member cannot read Hotel B", async () => {
    await seedHotel("hotel-a", "user-a");
    await seedHotel("hotel-b", "user-b");
    await assertFails(getDoc(doc(env.authenticatedContext("user-a").firestore(), "hotels", "hotel-b")));
  });

  test("authorised member can read own hotel data", async () => {
    await seedHotel("hotel-a", "staff-a");
    const db = env.authenticatedContext("staff-a").firestore();
    await assertSucceeds(getDoc(doc(db, "hotels", "hotel-a")));
    await assertSucceeds(getDoc(doc(db, "hotels", "hotel-a", "rooms", "room-existing")));
  });

  test("clients cannot write server-owned financial collections and locks", async () => {
    await seedHotel("hotel-a", "owner-a", { role: "OWNER" });
    const db = env.authenticatedContext("owner-a").firestore();
    for (const collectionName of [
      "bookingPayments", "bookingFinancialLines", "bookingAccountingCharges",
      "foodBills", "foodBillItems", "bookingLocks",
      "bookingAuditEvents",
    ]) {
      await assertFails(setDoc(doc(db, "hotels", "hotel-a", collectionName, "client-write"), {
        hotelRemoteId: "hotel-a",
      }));
    }
  });

  test("owner and manager can create correctly scoped rooms and menu items", async () => {
    for (const [uid, role] of [["owner-a", "OWNER"], ["manager-a", "MANAGER"]]) {
      await seedHotel("hotel-a", uid, { role });
      const db = env.authenticatedContext(uid).firestore();
      await assertSucceeds(setDoc(doc(db, "hotels", "hotel-a", "rooms", `room-${uid}`), {
        hotelRemoteId: "hotel-a", roomName: `Room ${uid}`,
      }));
      await assertSucceeds(setDoc(doc(db, "hotels", "hotel-a", "foodMenuItems", `food-${uid}`), {
        hotelRemoteId: "hotel-a", itemName: "Tea",
      }));
      await assertSucceeds(setDoc(doc(db, "hotels", "hotel-a", "serviceMenuItems", `service-${uid}`), {
        hotelRemoteId: "hotel-a", itemName: "Laundry",
      }));
    }
  });

  test("staff cannot write rooms or menu items", async () => {
    await seedHotel("hotel-a", "staff-a", { role: "STAFF" });
    const db = env.authenticatedContext("staff-a").firestore();
    await assertFails(setDoc(doc(db, "hotels", "hotel-a", "rooms", "room-new"), { hotelRemoteId: "hotel-a" }));
    await assertFails(setDoc(doc(db, "hotels", "hotel-a", "foodMenuItems", "food-new"), { hotelRemoteId: "hotel-a" }));
  });

  test("manager cannot create room or menu data scoped to another hotel", async () => {
    await seedHotel("hotel-a", "manager-a", { role: "MANAGER" });
    const db = env.authenticatedContext("manager-a").firestore();
    await assertFails(setDoc(doc(db, "hotels", "hotel-a", "rooms", "wrong-hotel"), { hotelRemoteId: "hotel-b" }));
    await assertFails(setDoc(doc(db, "hotels", "hotel-a", "foodMenuItems", "wrong-hotel"), { hotelRemoteId: "hotel-b" }));
  });

  test("manager cannot bypass protected room lifecycle fields or hard-delete rooms", async () => {
    await seedHotel("hotel-a", "manager-a", { role: "MANAGER" });
    const db = env.authenticatedContext("manager-a").firestore();
    const room = doc(db, "hotels", "hotel-a", "rooms", "room-existing");
    await assertFails(setDoc(room, {
      hotelRemoteId: "hotel-a",
      roomName: "Existing",
      lifecycleStatus: "RETIRED",
      lifecycleReason: "Client bypass",
      retiredAtMillis: Date.now(),
      isDeleted: false,
    }));
    await assertFails(deleteDoc(room));
  });

  test("manager can still edit ordinary room details without changing lifecycle", async () => {
    await seedHotel("hotel-a", "manager-a", { role: "MANAGER" });
    const db = env.authenticatedContext("manager-a").firestore();
    await assertSucceeds(setDoc(doc(db, "hotels", "hotel-a", "rooms", "room-existing"), {
      hotelRemoteId: "hotel-a",
      roomName: "Renamed safely",
    }));
  });
});
