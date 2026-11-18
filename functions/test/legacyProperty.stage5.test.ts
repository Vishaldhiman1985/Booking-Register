import { describe, expect, test } from "vitest";
import {
  inspectStage5OfflineInventory,
  stage5Sha256,
  STAGE5_INVENTORY_COLLECTIONS,
  Stage5OfflineDocument,
  Stage5OfflineSnapshot,
} from "../src/legacyProperty/Stage5OfflineBackupEvidence";
import { compareStage5OfflineRecovery } from "../src/legacyProperty/Stage5RecoveryDryRun";

const enc = new TextEncoder();
const bytes = (content: string): Uint8Array => enc.encode(content);
const record = (
  collection: string,
  documentId: string,
  content: string,
  hotelId = "hotel-one"
): Stage5OfflineDocument => {
  const payload = bytes(content);
  return { hotelId, collection, documentId, bytes: payload, claimedSha256: stage5Sha256(payload) };
};

function snapshot(projectId: string): Stage5OfflineSnapshot {
  return {
    projectId,
    hotelId: "hotel-one",
    fullyEnumeratedCollections: [...STAGE5_INVENTORY_COLLECTIONS],
    documents: [
      record("rooms", "room-one", '{"hotelId":"hotel-one","room":"101"}'),
      record("bookings", "booking-one", '{"total":3050,"gst":50,"roomId":"room-one"}'),
      record("bookingPayments", "payment-one", '{"bookingId":"booking-one","amount":2500}'),
      record("bookingFinancialLines", "line-one", '{"bookingId":"booking-one","taxable":3000,"gst":50}'),
      record("bookingRoomNightAssignments", "night-one", '{"bookingId":"booking-one","roomId":"room-one"}'),
    ],
  };
}

const sample = bytes("offline-sample-only-not-a-Firestore-export");
function compare(
  original = snapshot("source-project"),
  rehearsalCopy = snapshot("separate-rehearsal-project"),
  artifact = sample,
  hash = stage5Sha256(sample)
) {
  return compareStage5OfflineRecovery({
    original, rehearsalCopy, artifactBytes: artifact, artifactClaimedSha256: hash,
  });
}

const replaceDoc = (
  original: Stage5OfflineSnapshot,
  which: string,
  replacement: Stage5OfflineDocument
): Stage5OfflineSnapshot => ({
  ...original,
  documents: original.documents.map((doc) => doc.documentId === which ? replacement : doc),
});

describe("Code 21 Stage 5 — offline backup evidence and recovery dry-run", () => {
  test("all fourteen Stage 2 collections are enumerated, including empty sets", () => {
    const result = inspectStage5OfflineInventory(snapshot("source-project"));
    expect(STAGE5_INVENTORY_COLLECTIONS).toHaveLength(14);
    expect(result.inventoryWellFormed).toBe(true);
    expect(result.counts.bookingPayments).toBe(1);
    expect(result.counts.foodBills).toBe(0);
    expect(result.migrationAuthorized).toBe(false);
  });

  test("matching offline fixtures NEVER authorize production backup, recovery or migration", () => {
    const report = compare();
    expect(report.offlineIntegrityChecksPassed).toBe(true);
    expect(report.offlineRepresentationsMatch).toBe(true);
    expect(report.productionBackupVerified).toBe(false);
    expect(report.recoveryWritesAuthorized).toBe(false);
    expect(report.migrationAuthorized).toBe(false);
    expect(report.issues).toContain("IMMUTABLE_OFF_DEVICE_COPY_NOT_VERIFIED");
    expect(report.issues).toContain("CROSS_DEVICE_WRITE_EXCLUSION_NOT_ENFORCED");
  });

  test("a missing payment is explicitly detected", () => {
    const copy = snapshot("other-project");
    const report = compare(undefined, {
      ...copy, documents: copy.documents.filter((r) => r.documentId !== "payment-one"),
    });
    expect(report.missingInRehearsal).toContain("bookingPayments/payment-one");
    expect(report.offlineRepresentationsMatch).toBe(false);
  });

  test("a changed payment amount is detected, even with a self-consistent new hash", () => {
    const copy = snapshot("other-project");
    const altered = replaceDoc(copy, "payment-one", record(
      "bookingPayments", "payment-one", '{"bookingId":"booking-one","amount":2000}'
    ));
    const report = compare(undefined, altered);
    expect(report.changedInRehearsal).toContain("bookingPayments/payment-one");
    expect(report.issues).toContain("REHEARSAL_DOCUMENT_CONTENT_CHANGED");
  });

  test("a changed tax line is detected", () => {
    const copy = snapshot("other-project");
    const altered = replaceDoc(copy, "line-one", record(
      "bookingFinancialLines", "line-one", '{"bookingId":"booking-one","taxable":3000,"gst":0}'
    ));
    expect(compare(undefined, altered).changedInRehearsal).toContain("bookingFinancialLines/line-one");
  });

  test("unexpected restored booking is detected", () => {
    const copy = snapshot("other-project");
    const report = compare(undefined, {
      ...copy, documents: [...copy.documents, record("bookings", "unexpected", "new")],
    });
    expect(report.unexpectedInRehearsal).toContain("bookings/unexpected");
  });

  test("tampered bytes are not compared as a valid inventory", () => {
    const copy = snapshot("other-project");
    const altered = replaceDoc(copy, "payment-one", {
      ...copy.documents[2], bytes: bytes("tampered"),
    });
    const report = compare(undefined, altered);
    expect(report.issues).toContain("REHEARSAL_INVENTORY_INVALID");
    expect(report.issues).toContain("DOCUMENT_HASH_MISMATCH:bookingPayments");
    expect(report.offlineRepresentationsMatch).toBe(false);
  });

  test("bad declared SHA-256 is rejected", () => {
    const copy = snapshot("other-project");
    const altered = replaceDoc(copy, "booking-one", {
      ...copy.documents[1], claimedSha256: "0".repeat(64),
    });
    expect(inspectStage5OfflineInventory(altered).issues).toContain("DOCUMENT_HASH_MISMATCH:bookings");
  });

  test("missing required enumerated collection blocks, even if it has no rows", () => {
    const original = snapshot("source-project");
    const altered = { ...original, fullyEnumeratedCollections: original.fullyEnumeratedCollections.filter(
      (collection) => collection !== "foodBills"
    ) };
    expect(inspectStage5OfflineInventory(altered).issues).toContain("COLLECTION_NOT_ENUMERATED:foodBills");
  });

  test("duplicate collection claim blocks", () => {
    const original = snapshot("source-project");
    const altered = { ...original, fullyEnumeratedCollections: [
      ...original.fullyEnumeratedCollections, "rooms",
    ] };
    expect(inspectStage5OfflineInventory(altered).issues).toContain("DUPLICATE_COLLECTION_CLAIM");
  });

  test("unexpected collection claim blocks", () => {
    const original = snapshot("source-project");
    const altered = { ...original, fullyEnumeratedCollections: [
      ...original.fullyEnumeratedCollections, "otherTenants",
    ] };
    expect(inspectStage5OfflineInventory(altered).issues).toContain("UNKNOWN_COLLECTION_CLAIM");
  });

  test("duplicate remote record ID in the same collection blocks", () => {
    const original = snapshot("source-project");
    const altered = { ...original, documents: [...original.documents, original.documents[2]] };
    expect(inspectStage5OfflineInventory(altered).issues).toContain("DUPLICATE_RECORD_ID:bookingPayments");
  });

  test("same ID in different collections is allowed", () => {
    const original = snapshot("source-project");
    const altered = { ...original, documents: [...original.documents,
      record("foodOrders", "payment-one", "different collection"),
    ] };
    expect(inspectStage5OfflineInventory(altered).inventoryWellFormed).toBe(true);
  });

  test("cross-hotel record blocks", () => {
    const original = snapshot("source-project");
    const altered = replaceDoc(original, "room-one", record("rooms", "room-one", "x", "other-hotel"));
    expect(inspectStage5OfflineInventory(altered).issues).toContain("CROSS_HOTEL_RECORD:rooms");
  });

  test.each(["../foo", "bad/id", "bad\\id", "\u0000", " "])(
    "illegal document segment %j blocks", (id) => {
      const original = snapshot("source-project");
      const altered = replaceDoc(original, "room-one", record("rooms", id, "x"));
      expect(inspectStage5OfflineInventory(altered).issues).toContain("INVALID_DOCUMENT_ID:rooms");
    }
  );

  test("unknown record collection blocks", () => {
    const original = snapshot("source-project");
    const altered = { ...original, documents: [...original.documents, record("other", "abc", "x")] };
    expect(inspectStage5OfflineInventory(altered).issues).toContain("UNKNOWN_RECORD_COLLECTION");
  });

  test("empty room inventory blocks", () => {
    const original = snapshot("source-project");
    const altered = { ...original, documents: original.documents.filter((row) => row.collection !== "rooms") };
    expect(inspectStage5OfflineInventory(altered).issues).toContain("NO_ROOM_DOCUMENTS");
  });

  test("empty bytes block", () => {
    const original = snapshot("source-project");
    const altered = replaceDoc(original, "room-one", { ...original.documents[0], bytes: bytes("") });
    expect(inspectStage5OfflineInventory(altered).issues).toContain("INVALID_DOCUMENT_BYTES:rooms");
  });

  test("more than 10,000 entries in a collection blocks", () => {
    const original = snapshot("source-project");
    const single = record("rooms", "100", "x");
    const extra = Array.from({ length: 10_000 }, (_, index) => ({
      ...single, documentId: `room-${index}`,
    }));
    const altered = { ...original, documents: [...original.documents, ...extra] };
    expect(inspectStage5OfflineInventory(altered).issues).toContain("COLLECTION_LIMIT_EXCEEDED:rooms");
  });

  test("same source and rehearsal project never qualifies", () => {
    const report = compare(snapshot("same-project"), snapshot("same-project"));
    expect(report.offlineRepresentationsMatch).toBe(false);
    expect(report.issues).toContain("REHEARSAL_MUST_USE_A_DIFFERENT_PROJECT");
  });

  test("mismatching hotel identity blocks", () => {
    const copy = snapshot("other-project");
    const report = compare(undefined, { ...copy, hotelId: "hotel-two" });
    expect(report.issues).toContain("HOTEL_SCOPE_MISMATCH");
    expect(report.offlineRepresentationsMatch).toBe(false);
  });

  test("bad artifact hash blocks", () => {
    const report = compare(undefined, undefined, sample, "0".repeat(64));
    expect(report.issues).toContain("SAMPLE_ARTIFACT_HASH_MISMATCH");
    expect(report.offlineRepresentationsMatch).toBe(false);
  });

  test("missing artifact blocks", () => {
    const report = compare(undefined, undefined, new Uint8Array(), stage5Sha256(sample));
    expect(report.issues).toContain("SAMPLE_ARTIFACT_HASH_MISMATCH");
  });

  test("no exposed mutation methods or callable route", () => {
    const report = compare();
    expect(Object.keys(report)).not.toContain("restore");
    expect(Object.keys(report)).not.toContain("commit");
    expect(report.recoveryWritesAuthorized).toBe(false);
  });
});
