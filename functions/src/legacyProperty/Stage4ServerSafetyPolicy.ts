/**
 * Code 21 / Stage 4. A conservative, pure policy for a server-only READ-ONLY
 * diagnostic. It is NOT migration authorization, even when baseline checks pass.
 * No caller can set proof flags to bypass the absent migration write barriers.
 */
export interface Stage4ServerEvidence {
  accountFound: boolean;
  accountOwnerMatchesAuthenticatedUid: boolean;
  activeOwnerMembership: boolean;
  hotelDocumentFound: boolean;
  noPropertyDocuments: boolean;
  noMigrationMarker: boolean;
  atLeastOneActiveLegacyRoom: boolean;
  roomInventoryWithinLimit: boolean;
  everyRoomBelongsToHotel: boolean;
  noRoomHasPropertyId: boolean;
}

export interface Stage4ServerSafetyReport {
  readonly baselineChecksPassed: boolean;
  /** This phase can NEVER authorize migration or issue a migration token. */
  readonly migrationAuthorized: false;
  readonly blockers: readonly string[];
}

/**
 * Separate and intentionally UNVERIFIED requirements. Stage 4 does not infer
 * these from client booleans, local syncState, a matching row count, or a marker.
 */
const NOT_YET_PROVEN = [
  "ORIGINAL_PLAY_103_ORIGIN_NOT_SERVER_PROVEN",
  "IMMUTABLE_OFF_DEVICE_BACKUP_NOT_VERIFIED",
  "ALL_BOOKINGS_PAYMENTS_AND_TAX_VALUES_NOT_SERVER_RECONCILED",
  "ALL_DEPENDENT_REFERENCES_AND_LOCKS_NOT_VERIFIED",
  "ALL_CLIENTS_WRITE_EXCLUSION_NOT_ENFORCED",
  "ROLLBACK_REHEARSAL_NOT_COMPLETED",
] as const;

export function evaluateStage4ServerSafety(
  e: Stage4ServerEvidence
): Stage4ServerSafetyReport {
  const failures: string[] = [];
  if (!e.accountFound) failures.push("HOTEL_ACCOUNT_NOT_FOUND");
  if (!e.accountOwnerMatchesAuthenticatedUid) failures.push("ACCOUNT_OWNER_MISMATCH");
  if (!e.activeOwnerMembership) failures.push("ACTIVE_OWNER_MEMBERSHIP_NOT_VERIFIED");
  if (!e.hotelDocumentFound) failures.push("HOTEL_DOCUMENT_NOT_FOUND");
  if (!e.noPropertyDocuments) failures.push("EXISTING_PROPERTY_FOUND");
  if (!e.noMigrationMarker) failures.push("MIGRATION_MARKER_ALREADY_EXISTS");
  if (!e.atLeastOneActiveLegacyRoom) failures.push("NO_ACTIVE_LEGACY_ROOMS");
  if (!e.roomInventoryWithinLimit) failures.push("ROOM_INVENTORY_LIMIT_EXCEEDED");
  if (!e.everyRoomBelongsToHotel) failures.push("CROSS_HOTEL_ROOM_FOUND");
  if (!e.noRoomHasPropertyId) failures.push("ROOM_PROPERTY_ALREADY_ASSIGNED");
  return {
    baselineChecksPassed: failures.length === 0,
    migrationAuthorized: false,
    blockers: [...failures, ...NOT_YET_PROVEN],
  };
}

export function blockedStage4ServerSafety(reason: string): Stage4ServerSafetyReport {
  return {
    baselineChecksPassed: false,
    migrationAuthorized: false,
    blockers: [reason, ...NOT_YET_PROVEN],
  };
}
