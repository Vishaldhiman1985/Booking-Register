import { createHash } from "node:crypto";

/**
 * Stage 5 is an OFFLINE, local-memory test harness; NOT a production backup tool.
 * No Firestore, Firebase, network, file writes, or Cloud Function exports here.
 * The caller must independently and safely obtain complete source data.
 */
export const STAGE5_INVENTORY_COLLECTIONS = [
  "rooms",
  "managedProperties",
  "bookings",
  "bookingPayments",
  "bookingSources",
  "bookingFinancialLines",
  "bookingRoomNightAssignments",
  "bookingAccountingCharges",
  "foodOrders",
  "foodOrderItems",
  "foodBills",
  "foodBillItems",
  "foodMenuItems",
  "serviceMenuItems",
] as const;

const EXPECTED_COLLECTIONS = new Set<string>(STAGE5_INVENTORY_COLLECTIONS);
const MAX_RECORDS_PER_COLLECTION = 10_000;
const MAX_DOCUMENT_BYTES = 5 * 1024 * 1024;
const SHA256 = /^[0-9a-f]{64}$/;
const SAFE_ID = /^[^/\\\u0000-\u001f\u007f]{1,256}$/;

/**
 * Bytes are supplied by an independent OFFLINE fixture/export reader.
 * This code does NOT know whether the encoding is lossless for Firestore values.
 * The claimed SHA-256 is useful only to detect byte changes after its capture.
 */
export interface Stage5OfflineDocument {
  readonly hotelId: string;
  readonly collection: string;
  readonly documentId: string;
  readonly bytes: Uint8Array;
  readonly claimedSha256: string;
}

export interface Stage5OfflineSnapshot {
  readonly projectId: string;
  readonly hotelId: string;
  /** Must explicitly enumerate every collection, including empty ones. */
  readonly fullyEnumeratedCollections: readonly string[];
  readonly documents: readonly Stage5OfflineDocument[];
}

export interface Stage5OfflineInventoryAssessment {
  readonly inventoryWellFormed: boolean;
  readonly issues: readonly string[];
  readonly documentCount: number;
  readonly counts: Readonly<Record<string, number>>;
  /** Not proof of live completeness, restorability, or immutable storage. */
  readonly fingerprints: ReadonlyMap<string, string>;
  readonly migrationAuthorized: false;
}

/** Local byte hashing, not a Firestore native-export verifier. */
export function stage5Sha256(bytes: Uint8Array): string {
  return createHash("sha256").update(bytes).digest("hex");
}

function validSegment(value: string): boolean {
  return typeof value === "string" && SAFE_ID.test(value) &&
    value !== "." && value !== ".." && value.trim().length > 0;
}

export function inspectStage5OfflineInventory(
  snapshot: Stage5OfflineSnapshot
): Stage5OfflineInventoryAssessment {
  const issues: string[] = [];
  const fingerprints = new Map<string, string>();
  const counts: Record<string, number> = Object.fromEntries(
    STAGE5_INVENTORY_COLLECTIONS.map((name) => [name, 0])
  );
  if (!validSegment(snapshot.projectId)) issues.push("INVALID_PROJECT_ID");
  if (!validSegment(snapshot.hotelId)) issues.push("INVALID_HOTEL_ID");

  const seenCollections = new Set<string>();
  for (const name of snapshot.fullyEnumeratedCollections) {
    if (!EXPECTED_COLLECTIONS.has(name)) issues.push("UNKNOWN_COLLECTION_CLAIM");
    if (seenCollections.has(name)) issues.push("DUPLICATE_COLLECTION_CLAIM");
    seenCollections.add(name);
  }
  for (const required of STAGE5_INVENTORY_COLLECTIONS) {
    if (!seenCollections.has(required)) {
      issues.push(`COLLECTION_NOT_ENUMERATED:${required}`);
    }
  }

  for (const record of snapshot.documents) {
    if (!EXPECTED_COLLECTIONS.has(record.collection)) {
      issues.push("UNKNOWN_RECORD_COLLECTION");
      continue;
    }
    if (!validSegment(record.documentId)) {
      issues.push(`INVALID_DOCUMENT_ID:${record.collection}`);
      continue;
    }
    if (record.hotelId !== snapshot.hotelId) {
      issues.push(`CROSS_HOTEL_RECORD:${record.collection}`);
      continue;
    }
    const key = `${record.collection}/${record.documentId}`;
    if (fingerprints.has(key)) {
      issues.push(`DUPLICATE_RECORD_ID:${record.collection}`);
      continue;
    }
    counts[record.collection]++;
    if (counts[record.collection] > MAX_RECORDS_PER_COLLECTION) {
      issues.push(`COLLECTION_LIMIT_EXCEEDED:${record.collection}`);
    }
    if (!(record.bytes instanceof Uint8Array) || record.bytes.byteLength === 0 ||
        record.bytes.byteLength > MAX_DOCUMENT_BYTES) {
      issues.push(`INVALID_DOCUMENT_BYTES:${record.collection}`);
      continue;
    }
    if (!SHA256.test(record.claimedSha256) ||
        stage5Sha256(record.bytes) !== record.claimedSha256) {
      issues.push(`DOCUMENT_HASH_MISMATCH:${record.collection}`);
      continue;
    }
    fingerprints.set(key, record.claimedSha256);
  }
  if (counts.rooms === 0) issues.push("NO_ROOM_DOCUMENTS");
  return {
    inventoryWellFormed: issues.length === 0,
    issues: [...new Set(issues)],
    documentCount: snapshot.documents.length,
    counts,
    fingerprints,
    migrationAuthorized: false,
  };
}
