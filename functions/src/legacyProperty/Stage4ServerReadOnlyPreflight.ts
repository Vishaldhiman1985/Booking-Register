import {
  blockedStage4ServerSafety,
  evaluateStage4ServerSafety,
  Stage4ServerSafetyReport,
} from "./Stage4ServerSafetyPolicy";

/**
 * Minimal read-only Firestore interface, compatible with firebase-admin's
 * Firestore instance. Deliberately exposes no set/update/delete or transactions.
 */
interface ReadOnlyDocumentSnapshot {
  readonly exists: boolean;
  data(): Record<string, unknown> | undefined;
}
interface ReadOnlyQuerySnapshot {
  readonly docs: readonly ReadOnlyDocumentSnapshot[];
}
export interface Stage4ReadOnlyFirestore {
  doc(path: string): { get(): Promise<ReadOnlyDocumentSnapshot> };
  collection(path: string): {
    limit(maxRecords: number): { get(): Promise<ReadOnlyQuerySnapshot> };
  };
}

/**
 * AuthenticatedIdentity MUST be constructed from a verified server-side auth
 * context (request.auth.uid), NEVER from client-supplied request.data.uid.
 * This function is NOT exported through functions/src/index.ts.
 */
export interface Stage4AuthenticatedIdentity {
  readonly uid: string;
  readonly hotelId: string;
}

/**
 * Strictly read-only, non-transactional snapshot. No migration function is
 * registered. This is diagnostic only: concurrent writes are NOT excluded.
 * On any missing record or Firestore error it fails closed.
 */
export async function inspectStage4ServerBaseline(
  db: Stage4ReadOnlyFirestore,
  identity: Stage4AuthenticatedIdentity
): Promise<Stage4ServerSafetyReport> {
  const { uid, hotelId } = identity;
  // Restrict Firestore path segments before using them in a document path.
  const validSegment = (s: string): boolean =>
    s.length > 0 && s.length <= 256 && s !== "." && s !== ".." &&
    !s.includes("/") && !s.includes("\\") && !s.includes("\u0000");
  if (!validSegment(uid) || !validSegment(hotelId)) {
    return blockedStage4ServerSafety("INVALID_AUTHENTICATED_IDENTITY");
  }
  try {
    // Authorization precedes all property/room reads to avoid disclosing hotel
    // inventory to an arbitrary signed-in user.
    const [account, member] = await Promise.all([
      db.doc(`hotelAccounts/${hotelId}`).get(),
      db.doc(`hotelAccounts/${hotelId}/members/${uid}`).get(),
    ]);
    const accountData = account.data();
    const memberData = member.data();
    if (!account.exists || accountData?.ownerUid !== uid || !member.exists ||
        memberData?.role !== "OWNER" || memberData.active !== true) {
      return blockedStage4ServerSafety("AUTHENTICATED_OWNER_NOT_VERIFIED");
    }

    // We deliberately scan only this organization's room subcollection.
    // Oversized scans block instead of silently checking a subset.
    const MAX_ROOM_SCAN = 500;
    const [hotel, properties, marker, rooms] = await Promise.all([
      db.doc(`hotels/${hotelId}`).get(),
      db.collection(`hotels/${hotelId}/managedProperties`).limit(1).get(),
      db.doc(`hotels/${hotelId}/legacyPropertyMigrations/play103`).get(),
      db.collection(`hotels/${hotelId}/rooms`).limit(MAX_ROOM_SCAN + 1).get(),
    ]);
    const roomRows = rooms.docs.map((room) => room.data());
    const scannedWithinLimit = roomRows.length <= MAX_ROOM_SCAN;
    // A missing data payload is never proof of room eligibility.
    const allRoomRowsValid = roomRows.every((room) => room !== undefined);
    const activeRooms = roomRows.filter((room) => room !== undefined &&
      room.isDeleted !== true && room.isDeleted !== 1 &&
      room.lifecycleStatus !== "RETIRED");

    return evaluateStage4ServerSafety({
      accountFound: account.exists,
      accountOwnerMatchesAuthenticatedUid: accountData?.ownerUid === uid,
      activeOwnerMembership: memberData?.role === "OWNER" && memberData.active === true,
      hotelDocumentFound: hotel.exists,
      noPropertyDocuments: properties.docs.length === 0,
      noMigrationMarker: !marker.exists,
      atLeastOneActiveLegacyRoom: activeRooms.length > 0,
      roomInventoryWithinLimit: scannedWithinLimit && allRoomRowsValid,
      everyRoomBelongsToHotel: allRoomRowsValid && roomRows.every((room) =>
        room?.hotelRemoteId === hotelId),
      noRoomHasPropertyId: allRoomRowsValid && roomRows.every((room) =>
        room?.propertyRemoteId === null || room?.propertyRemoteId === undefined ||
        room?.propertyRemoteId === ""),
    });
  } catch (_error) {
    // No cache fallback and no detailed exception text that might expose data.
    return blockedStage4ServerSafety("SERVER_READ_FAILED");
  }
}
