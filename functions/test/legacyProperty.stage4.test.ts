import { describe, expect, test, vi } from "vitest";
import {
  evaluateStage4ServerSafety,
  Stage4ServerEvidence,
} from "../src/legacyProperty/Stage4ServerSafetyPolicy";
import {
  inspectStage4ServerBaseline,
  Stage4ReadOnlyFirestore,
} from "../src/legacyProperty/Stage4ServerReadOnlyPreflight";

const safeBaseline: Stage4ServerEvidence = {
  accountFound: true,
  accountOwnerMatchesAuthenticatedUid: true,
  activeOwnerMembership: true,
  hotelDocumentFound: true,
  noPropertyDocuments: true,
  noMigrationMarker: true,
  atLeastOneActiveLegacyRoom: true,
  roomInventoryWithinLimit: true,
  everyRoomBelongsToHotel: true,
  noRoomHasPropertyId: true,
};

function mockFirestore(opts: {
  ownerUid?: string;
  role?: string;
  active?: boolean;
  properties?: number;
  migrated?: boolean;
  rooms?: Array<Record<string, unknown>>;
  shouldThrow?: boolean;
} = {}) {
  const paths: string[] = [];
  const snapshots: Record<string, Record<string, unknown> | undefined> = {
    "hotelAccounts/hotel-one": { ownerUid: opts.ownerUid ?? "owner-one" },
    "hotelAccounts/hotel-one/members/owner-one": {
      role: opts.role ?? "OWNER", active: opts.active ?? true,
    },
    "hotels/hotel-one": { hotelRemoteId: "hotel-one" },
    "hotels/hotel-one/legacyPropertyMigrations/play103":
      opts.migrated ? { completed: true } : undefined,
  };
  const rooms = opts.rooms ?? [{ hotelRemoteId: "hotel-one", roomName: "101", propertyRemoteId: null }];
  const store: Stage4ReadOnlyFirestore = {
    doc(path: string) {
      paths.push(path);
      return { get: vi.fn(async () => {
        if (opts.shouldThrow) throw Error("offline");
        const data = snapshots[path];
        return { exists: data !== undefined, data: () => data };
      }) };
    },
    collection(path: string) {
      paths.push(path);
      return { limit: (limit: number) => ({ get: vi.fn(async () => {
        if (opts.shouldThrow) throw Error("offline");
        const rows = path.endsWith("/managedProperties")
          ? (opts.properties ? [{ remoteId: "already" }] : [])
          : rooms;
        return { docs: rows.slice(0, limit).map((data) => ({ exists: true, data: () => data })) };
      }) }) };
    },
  };
  return { store, paths };
}

describe("Code 21 Stage 4 server-side read-only migration guards", () => {
  test("all baseline facts still NEVER authorize writes", () => {
    const report = evaluateStage4ServerSafety(safeBaseline);
    expect(report.baselineChecksPassed).toBe(true);
    expect(report.migrationAuthorized).toBe(false);
    expect(report.blockers).toContain("ALL_CLIENTS_WRITE_EXCLUSION_NOT_ENFORCED");
    expect(report.blockers).toContain("IMMUTABLE_OFF_DEVICE_BACKUP_NOT_VERIFIED");
  });
  test.each([
    ["accountFound", "HOTEL_ACCOUNT_NOT_FOUND"],
    ["accountOwnerMatchesAuthenticatedUid", "ACCOUNT_OWNER_MISMATCH"],
    ["activeOwnerMembership", "ACTIVE_OWNER_MEMBERSHIP_NOT_VERIFIED"],
    ["hotelDocumentFound", "HOTEL_DOCUMENT_NOT_FOUND"],
    ["noPropertyDocuments", "EXISTING_PROPERTY_FOUND"],
    ["noMigrationMarker", "MIGRATION_MARKER_ALREADY_EXISTS"],
    ["atLeastOneActiveLegacyRoom", "NO_ACTIVE_LEGACY_ROOMS"],
    ["roomInventoryWithinLimit", "ROOM_INVENTORY_LIMIT_EXCEEDED"],
    ["everyRoomBelongsToHotel", "CROSS_HOTEL_ROOM_FOUND"],
    ["noRoomHasPropertyId", "ROOM_PROPERTY_ALREADY_ASSIGNED"],
  ] as const)("false %s blocks with %s", (flag, message) => {
    const report = evaluateStage4ServerSafety({ ...safeBaseline, [flag]: false });
    expect(report.baselineChecksPassed).toBe(false);
    expect(report.blockers).toContain(message);
    expect(report.migrationAuthorized).toBe(false);
  });
  test("owner can inspect hotel baseline but still cannot migrate", async () => {
    const { store, paths } = mockFirestore();
    const report = await inspectStage4ServerBaseline(store, { uid: "owner-one", hotelId: "hotel-one" });
    expect(report.baselineChecksPassed).toBe(true);
    expect(report.migrationAuthorized).toBe(false);
    expect(paths).toContain("hotels/hotel-one/rooms");
  });
  test("a manager cannot inspect hotel inventory", async () => {
    const { store, paths } = mockFirestore({ role: "MANAGER" });
    const report = await inspectStage4ServerBaseline(store, { uid: "owner-one", hotelId: "hotel-one" });
    expect(report.baselineChecksPassed).toBe(false);
    expect(paths).not.toContain("hotels/hotel-one/rooms");
  });
  test("a different claimed owner is rejected before inventory reads", async () => {
    const { store, paths } = mockFirestore({ ownerUid: "another-user" });
    const report = await inspectStage4ServerBaseline(store, { uid: "owner-one", hotelId: "hotel-one" });
    expect(report.baselineChecksPassed).toBe(false);
    expect(paths).not.toContain("hotels/hotel-one/rooms");
  });
  test("inactive owner is rejected before inventory reads", async () => {
    const { store, paths } = mockFirestore({ active: false });
    const report = await inspectStage4ServerBaseline(store, { uid: "owner-one", hotelId: "hotel-one" });
    expect(report.baselineChecksPassed).toBe(false);
    expect(paths).not.toContain("hotels/hotel-one/rooms");
  });
  test("an existing property prevents baseline eligibility", async () => {
    const { store } = mockFirestore({ properties: 1 });
    const report = await inspectStage4ServerBaseline(store, { uid: "owner-one", hotelId: "hotel-one" });
    expect(report.blockers).toContain("EXISTING_PROPERTY_FOUND");
  });
  test("a previous migration marker blocks", async () => {
    const { store } = mockFirestore({ migrated: true });
    const report = await inspectStage4ServerBaseline(store, { uid: "owner-one", hotelId: "hotel-one" });
    expect(report.blockers).toContain("MIGRATION_MARKER_ALREADY_EXISTS");
  });
  test("rooms already associated with a property block", async () => {
    const { store } = mockFirestore({ rooms: [{ hotelRemoteId: "hotel-one", propertyRemoteId: "prop-1" }] });
    const report = await inspectStage4ServerBaseline(store, { uid: "owner-one", hotelId: "hotel-one" });
    expect(report.blockers).toContain("ROOM_PROPERTY_ALREADY_ASSIGNED");
  });
  test("cross-hotel room blocks", async () => {
    const { store } = mockFirestore({ rooms: [{ hotelRemoteId: "other-hotel", propertyRemoteId: null }] });
    const report = await inspectStage4ServerBaseline(store, { uid: "owner-one", hotelId: "hotel-one" });
    expect(report.blockers).toContain("CROSS_HOTEL_ROOM_FOUND");
  });
  test("more than 500 room docs must block rather than sample", async () => {
    const rooms = Array.from({ length: 501 }, (_, i) => ({ hotelRemoteId: "hotel-one", roomName: String(i) }));
    const { store } = mockFirestore({ rooms });
    const report = await inspectStage4ServerBaseline(store, { uid: "owner-one", hotelId: "hotel-one" });
    expect(report.blockers).toContain("ROOM_INVENTORY_LIMIT_EXCEEDED");
  });
  test("server read error blocks without throwing sensitive details", async () => {
    const { store } = mockFirestore({ shouldThrow: true });
    const report = await inspectStage4ServerBaseline(store, { uid: "owner-one", hotelId: "hotel-one" });
    expect(report.blockers).toContain("SERVER_READ_FAILED");
    expect(report.migrationAuthorized).toBe(false);
  });
  test("slash-bearing paths rejected with no Firestore reads", async () => {
    const { store, paths } = mockFirestore();
    const report = await inspectStage4ServerBaseline(store, { uid: "owner-one", hotelId: "../other" });
    expect(report.blockers).toContain("INVALID_AUTHENTICATED_IDENTITY");
    expect(paths).toHaveLength(0);
  });
});
