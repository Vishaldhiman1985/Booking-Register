import {
  inspectStage5OfflineInventory,
  stage5Sha256,
  Stage5OfflineSnapshot,
} from "./Stage5OfflineBackupEvidence";

/**
 * This is a dry-run comparison of OFFLINE representations. It never restores
 * any Firestore record and never authorizes a real migration or recovery write.
 */
export interface Stage5RecoveryDryRunInput {
  readonly original: Stage5OfflineSnapshot;
  readonly rehearsalCopy: Stage5OfflineSnapshot;
  /** Exact bytes of a separately captured sample artifact; not a native export. */
  readonly artifactBytes: Uint8Array;
  readonly artifactClaimedSha256: string;
}

export interface Stage5RecoveryDryRunReport {
  readonly offlineIntegrityChecksPassed: boolean;
  readonly offlineRepresentationsMatch: boolean;
  readonly missingInRehearsal: readonly string[];
  readonly unexpectedInRehearsal: readonly string[];
  readonly changedInRehearsal: readonly string[];
  readonly issues: readonly string[];
  /** No real backup, production write lock, or restore drill is verified. */
  readonly migrationAuthorized: false;
  readonly recoveryWritesAuthorized: false;
  readonly productionBackupVerified: false;
}

const NOT_PROVEN = [
  "NATIVE_FIRESTORE_EXPORT_NOT_VERIFIED",
  "IMMUTABLE_OFF_DEVICE_COPY_NOT_VERIFIED",
  "REAL_RESTORE_REHEARSAL_NOT_VERIFIED",
  "FULL_BOOKING_LOCK_AND_ACCOUNT_SCOPE_NOT_VERIFIED",
  "CROSS_DEVICE_WRITE_EXCLUSION_NOT_ENFORCED",
] as const;

export function compareStage5OfflineRecovery(
  input: Stage5RecoveryDryRunInput
): Stage5RecoveryDryRunReport {
  const source = inspectStage5OfflineInventory(input.original);
  const rehearsal = inspectStage5OfflineInventory(input.rehearsalCopy);
  const issues: string[] = [];
  if (!source.inventoryWellFormed) issues.push("SOURCE_INVENTORY_INVALID", ...source.issues);
  if (!rehearsal.inventoryWellFormed) issues.push("REHEARSAL_INVENTORY_INVALID", ...rehearsal.issues);
  if (input.original.hotelId !== input.rehearsalCopy.hotelId) {
    issues.push("HOTEL_SCOPE_MISMATCH");
  }
  if (input.original.projectId === input.rehearsalCopy.projectId) {
    issues.push("REHEARSAL_MUST_USE_A_DIFFERENT_PROJECT");
  }
  if (!(input.artifactBytes instanceof Uint8Array) || input.artifactBytes.byteLength === 0 ||
      !/^[0-9a-f]{64}$/.test(input.artifactClaimedSha256) ||
      stage5Sha256(input.artifactBytes) !== input.artifactClaimedSha256) {
    issues.push("SAMPLE_ARTIFACT_HASH_MISMATCH");
  }

  // Do not compare partially validated inventories: a bad hash or duplicate
  // ID could otherwise silently remove an entry from the comparison map.
  const missing: string[] = [];
  const unexpected: string[] = [];
  const changed: string[] = [];
  if (source.inventoryWellFormed && rehearsal.inventoryWellFormed) {
    for (const [key, hash] of source.fingerprints) {
      const copyHash = rehearsal.fingerprints.get(key);
      if (copyHash === undefined) missing.push(key);
      else if (copyHash !== hash) changed.push(key);
    }
    for (const key of rehearsal.fingerprints.keys()) {
      if (!source.fingerprints.has(key)) unexpected.push(key);
    }
    if (missing.length) issues.push("DOCUMENTS_MISSING_FROM_REHEARSAL");
    if (unexpected.length) issues.push("UNEXPECTED_REHEARSAL_DOCUMENTS");
    if (changed.length) issues.push("REHEARSAL_DOCUMENT_CONTENT_CHANGED");
  }
  const offlineIntegrityChecksPassed = issues.length === 0;
  return {
    offlineIntegrityChecksPassed,
    offlineRepresentationsMatch: offlineIntegrityChecksPassed &&
      missing.length === 0 && unexpected.length === 0 && changed.length === 0,
    missingInRehearsal: missing.sort(),
    unexpectedInRehearsal: unexpected.sort(),
    changedInRehearsal: changed.sort(),
    issues: [...new Set([...issues, ...NOT_PROVEN])],
    migrationAuthorized: false,
    recoveryWritesAuthorized: false,
    productionBackupVerified: false,
  };
}
