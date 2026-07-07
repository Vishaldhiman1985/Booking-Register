import { initializeApp } from "firebase-admin/app";
import { DecodedIdToken, getAuth, UserRecord } from "firebase-admin/auth";
import { DocumentSnapshot, FieldValue, Timestamp, getFirestore } from "firebase-admin/firestore";
import { CallableRequest, HttpsError, onCall } from "firebase-functions/v2/https";
import { logger, setGlobalOptions } from "firebase-functions/v2";

initializeApp();
setGlobalOptions({ region: "asia-south1", maxInstances: 10 });

const db = getFirestore();
const auth = getAuth();

const DEFAULT_TRIAL_DAYS = 14;
const DEFAULT_MAX_USERS = 2;
const DEFAULT_PLAN_ID = "starter_199_monthly";

const STATUS_TRIALING = "TRIALING";
const STATUS_ACTIVE = "ACTIVE";
const STATUS_PAST_DUE = "PAST_DUE";
const STATUS_SUSPENDED = "SUSPENDED";

const ROLE_OWNER = "OWNER";
const ROLE_MANAGER = "MANAGER";
const ROLE_STAFF = "STAFF";
const DAY_MILLIS = 24 * 60 * 60 * 1000;

type AccountStatus = typeof STATUS_TRIALING | typeof STATUS_ACTIVE | typeof STATUS_PAST_DUE | typeof STATUS_SUSPENDED;
type MemberRole = typeof ROLE_OWNER | typeof ROLE_MANAGER | typeof ROLE_STAFF;

type AppAuth = {
  uid: string;
  token: DecodedIdToken;
};

async function requireAuth(request: CallableRequest): Promise<AppAuth> {
  if (request.auth) {
    return request.auth;
  }

  const idToken = String(request.data?.idToken || "").trim();
  if (!idToken) {
    throw new HttpsError("unauthenticated", "Please sign in again.");
  }

  try {
    const token = await auth.verifyIdToken(idToken, true);
    return { uid: token.uid, token };
  } catch (error) {
    logger.warn("Manual ID token verification failed", error);
    throw new HttpsError("unauthenticated", "Please sign in again.");
  }
}

function hotelIdForOwner(uid: string): string {
  return `hotel_${uid}`;
}

function nowPlusDays(days: number): Timestamp {
  return Timestamp.fromMillis(Date.now() + days * 24 * 60 * 60 * 1000);
}

function accountRef(hotelId: string) {
  return db.collection("hotelAccounts").doc(hotelId);
}

function memberRef(hotelId: string, uid: string) {
  return accountRef(hotelId).collection("members").doc(uid);
}

function publicHotelRef(hotelId: string) {
  return db.collection("hotels").doc(hotelId);
}

function normalizeEmail(value: unknown): string {
  return String(value || "").trim().toLowerCase();
}

function requireString(value: unknown, field: string): string {
  const text = String(value || "").trim();
  if (!text) {
    throw new HttpsError("invalid-argument", `${field} is required.`);
  }
  return text;
}

function optionalPositiveInt(value: unknown, fallback: number): number {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed <= 0) return fallback;
  return Math.floor(parsed);
}

function parseStatus(value: unknown): AccountStatus {
  const status = String(value || "").trim().toUpperCase();
  if ([STATUS_TRIALING, STATUS_ACTIVE, STATUS_PAST_DUE, STATUS_SUSPENDED].includes(status)) {
    return status as AccountStatus;
  }
  throw new HttpsError("invalid-argument", "Invalid subscription status.");
}

function parseRole(value: unknown): MemberRole {
  const role = String(value || ROLE_STAFF).trim().toUpperCase();
  if ([ROLE_MANAGER, ROLE_STAFF].includes(role)) {
    return role as MemberRole;
  }
  throw new HttpsError("invalid-argument", "Invalid member role.");
}

function platformAdminEmails(): Set<string> {
  return new Set(
    String(process.env.PLATFORM_ADMIN_EMAILS || "")
      .split(",")
      .map((email) => email.trim().toLowerCase())
      .filter(Boolean)
  );
}

function isPlatformAdmin(requestAuth: AppAuth): boolean {
  const email = normalizeEmail(requestAuth.token.email);
  return requestAuth.token.platformAdmin === true || platformAdminEmails().has(email);
}

async function setAppClaims(user: UserRecord, hotelId: string, role: MemberRole): Promise<void> {
  await auth.setCustomUserClaims(user.uid, {
    ...(user.customClaims || {}),
    hotelId,
    hotelRole: role,
  });
}

async function requireOwnerOrManager(requestAuth: AppAuth, hotelId: string): Promise<MemberRole> {
  const memberSnap = await memberRef(hotelId, requestAuth.uid).get();
  if (!memberSnap.exists || memberSnap.get("active") !== true) {
    throw new HttpsError("permission-denied", "You are not active in this hotel account.");
  }

  const role = String(memberSnap.get("role") || "").toUpperCase();
  if (role !== ROLE_OWNER && role !== ROLE_MANAGER) {
    throw new HttpsError("permission-denied", "Only owner or manager can do this.");
  }
  return role as MemberRole;
}

async function requireUsableSubscription(hotelId: string): Promise<void> {
  const account = await accountRef(hotelId).get();
  if (!account.exists) {
    throw new HttpsError("failed-precondition", "Hotel account is not created yet.");
  }

  const status = String(account.get("status") || "");
  const accessUntil = account.get("accessUntil") as Timestamp | undefined;
  const hasTime = accessUntil ? accessUntil.toMillis() >= Date.now() : false;

  if (![STATUS_TRIALING, STATUS_ACTIVE].includes(status) || !hasTime) {
    throw new HttpsError("failed-precondition", "Subscription is not active.");
  }
}

async function activeMemberCount(hotelId: string): Promise<number> {
  const members = await accountRef(hotelId).collection("members").where("active", "==", true).get();
  return members.size;
}

async function getOrCreateUser(
  email: string,
  password: string,
  displayName?: string
): Promise<{ user: UserRecord; created: boolean }> {
  try {
    return { user: await auth.getUserByEmail(email), created: false };
  } catch (error) {
    const typed = error as { code?: string };
    if (typed.code !== "auth/user-not-found") throw error;
  }

  if (password.length < 8) {
    throw new HttpsError("invalid-argument", "Password must be at least 8 characters.");
  }

  const user = await auth.createUser({
    email,
    password,
    displayName: displayName || undefined,
    emailVerified: false,
    disabled: false,
  });
  return { user, created: true };
}

export const bootstrapHotelOwner = onCall({ invoker: "public" }, async (request) => {
  const requestAuth = await requireAuth(request);
  const hotelId = hotelIdForOwner(requestAuth.uid);
  const email = normalizeEmail(requestAuth.token.email);
  const displayName = String(requestAuth.token.name || "").trim();
  const now = FieldValue.serverTimestamp();
  const accessUntil = nowPlusDays(DEFAULT_TRIAL_DAYS);

  await db.runTransaction(async (tx) => {
    const account = await tx.get(accountRef(hotelId));
    if (!account.exists) {
      tx.set(accountRef(hotelId), {
        hotelId,
        ownerUid: requestAuth.uid,
        ownerEmail: email,
        planId: DEFAULT_PLAN_ID,
        status: STATUS_TRIALING,
        maxUsers: DEFAULT_MAX_USERS,
        trialStartedAt: now,
        trialEndsAt: accessUntil,
        accessUntil,
        createdAt: now,
        updatedAt: now,
      });
    }

    tx.set(memberRef(hotelId, requestAuth.uid), {
      uid: requestAuth.uid,
      email,
      displayName,
      role: ROLE_OWNER,
      active: true,
      createdAt: now,
      updatedAt: now,
    }, { merge: true });

    tx.set(publicHotelRef(hotelId), {
      ownerUid: requestAuth.uid,
      hotelRemoteId: hotelId,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
  });

  const user = await auth.getUser(requestAuth.uid);
  await setAppClaims(user, hotelId, ROLE_OWNER);

  logger.info("Hotel owner bootstrapped", { hotelId, uid: requestAuth.uid });
  return { hotelId, role: ROLE_OWNER, status: STATUS_TRIALING, accessUntilMillis: accessUntil.toMillis() };
});

export const createHotelUser = onCall({ invoker: "public" }, async (request) => {
  const requestAuth = await requireAuth(request);
  const hotelId = requireString(request.data?.hotelId || requestAuth.token.hotelId, "hotelId");
  await requireOwnerOrManager(requestAuth, hotelId);
  await requireUsableSubscription(hotelId);

  const account = await accountRef(hotelId).get();
  const maxUsers = optionalPositiveInt(account.get("maxUsers"), DEFAULT_MAX_USERS);
  const currentMembers = await activeMemberCount(hotelId);
  if (currentMembers >= maxUsers) {
    throw new HttpsError("resource-exhausted", `This plan allows only ${maxUsers} active users.`);
  }

  const email = normalizeEmail(request.data?.email);
  if (!email || !email.includes("@")) {
    throw new HttpsError("invalid-argument", "Enter a valid email.");
  }

  const role = parseRole(request.data?.role);
  const password = requireString(request.data?.password, "password");
  const displayName = String(request.data?.displayName || "").trim();
  const { user, created } = await getOrCreateUser(email, password, displayName);
  await setAppClaims(user, hotelId, role);

  await memberRef(hotelId, user.uid).set({
    uid: user.uid,
    email,
    displayName,
    role,
    active: true,
    createdByUid: requestAuth.uid,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  }, { merge: true });

  logger.info("Hotel user added", { hotelId, uid: user.uid, role, created });
  return { uid: user.uid, email, role, created };
});

export const setHotelUserActive = onCall({ invoker: "public" }, async (request) => {
  const requestAuth = await requireAuth(request);
  const hotelId = requireString(request.data?.hotelId || requestAuth.token.hotelId, "hotelId");
  await requireOwnerOrManager(requestAuth, hotelId);

  const uid = requireString(request.data?.uid, "uid");
  if (uid === requestAuth.uid) {
    throw new HttpsError("failed-precondition", "You cannot deactivate yourself.");
  }

  const active = request.data?.active === true;
  await memberRef(hotelId, uid).set({
    active,
    updatedAt: FieldValue.serverTimestamp(),
    updatedByUid: requestAuth.uid,
  }, { merge: true });

  await auth.updateUser(uid, { disabled: !active });
  logger.info("Hotel user active flag changed", { hotelId, uid, active });
  return { uid, active };
});

export const setHotelSubscription = onCall({ invoker: "public" }, async (request) => {
  const requestAuth = await requireAuth(request);
  if (!isPlatformAdmin(requestAuth)) {
    throw new HttpsError("permission-denied", "Only platform admin can update subscriptions.");
  }

  const hotelId = requireString(request.data?.hotelId, "hotelId");
  const status = parseStatus(request.data?.status);
  const maxUsers = optionalPositiveInt(request.data?.maxUsers, DEFAULT_MAX_USERS);
  const planId = String(request.data?.planId || DEFAULT_PLAN_ID).trim();
  const accessUntilMillis = Number(request.data?.accessUntilMillis || 0);
  const accessUntil = Number.isFinite(accessUntilMillis) && accessUntilMillis > 0 ?
    Timestamp.fromMillis(accessUntilMillis) :
    nowPlusDays(status === STATUS_ACTIVE ? 31 : DEFAULT_TRIAL_DAYS);

  await accountRef(hotelId).set({
    planId,
    status,
    maxUsers,
    accessUntil,
    updatedAt: FieldValue.serverTimestamp(),
    updatedByUid: requestAuth.uid,
  }, { merge: true });

  logger.info("Subscription updated", { hotelId, status, maxUsers, planId });
  return { hotelId, status, maxUsers, planId, accessUntilMillis: accessUntil.toMillis() };
});

export const getMyHotelAccess = onCall({ invoker: "public" }, async (request) => {
  const requestAuth = await requireAuth(request);
  let hotelId = String(requestAuth.token.hotelId || "").trim();
  let member = hotelId ? await memberRef(hotelId, requestAuth.uid).get() : undefined;

  if (!hotelId || !member?.exists || member.get("active") !== true) {
    const memberships = await db.collectionGroup("members")
      .where("uid", "==", requestAuth.uid)
      .where("active", "==", true)
      .limit(1)
      .get();

    if (memberships.empty) {
      return { allowed: false, reason: "NO_ACTIVE_MEMBERSHIP" };
    }

    member = memberships.docs[0];
    hotelId = member.ref.parent.parent?.id || "";
    if (!hotelId) {
      return { allowed: false, reason: "NO_ACTIVE_MEMBERSHIP" };
    }
  }

  const account = await accountRef(hotelId).get();
  if (!account.exists || !member.exists || member.get("active") !== true) {
    return { allowed: false, reason: "NO_ACTIVE_MEMBERSHIP" };
  }

  const role = String(member.get("role") || ROLE_STAFF).toUpperCase() as MemberRole;
  const user = await auth.getUser(requestAuth.uid);
  await setAppClaims(user, hotelId, role);

  const accessUntil = account.get("accessUntil") as Timestamp | undefined;
  const accessUntilMillis = accessUntil?.toMillis() || 0;
  const status = String(account.get("status") || STATUS_SUSPENDED);
  const allowed = [STATUS_TRIALING, STATUS_ACTIVE].includes(status) && accessUntilMillis >= Date.now();

  return {
    allowed,
    hotelId,
    role,
    status,
    planId: account.get("planId") || DEFAULT_PLAN_ID,
    maxUsers: account.get("maxUsers") || DEFAULT_MAX_USERS,
    accessUntilMillis,
  };
});

export const listHotelAccounts = onCall({ invoker: "public" }, async (request) => {
  const requestAuth = await requireAuth(request);
  if (!isPlatformAdmin(requestAuth)) {
    throw new HttpsError("permission-denied", "Only platform admin can view hotel accounts.");
  }

  const accounts = await db.collection("hotelAccounts")
    .orderBy("updatedAt", "desc")
    .limit(200)
    .get();

  const hotels = await Promise.all(accounts.docs.map(async (account) => {
    const data = account.data();
    const members = await account.ref.collection("members").get();
    const publicHotel = await publicHotelRef(account.id).get();
    const activeUsers = members.docs.filter((member) => member.get("active") === true).length;
    const accessUntil = data.accessUntil as Timestamp | undefined;
    const trialEndsAt = data.trialEndsAt as Timestamp | undefined;

    return {
      hotelId: account.id,
      hotelName: publicHotel.get("hotelName") || data.hotelName || "",
      ownerEmail: data.ownerEmail || "",
      status: data.status || STATUS_SUSPENDED,
      planId: data.planId || DEFAULT_PLAN_ID,
      maxUsers: data.maxUsers || DEFAULT_MAX_USERS,
      activeUsers,
      totalUsers: members.size,
      accessUntilMillis: accessUntil?.toMillis() || 0,
      trialEndsAtMillis: trialEndsAt?.toMillis() || 0,
      updatedAtMillis: timestampMillis(data.updatedAt),
    };
  }));

  return { hotels };
});

function timestampMillis(value: unknown): number {
  if (value instanceof Timestamp) return value.toMillis();
  if (typeof value === "number") return value;
  return 0;
}

async function requireActiveHotelMember(requestAuth: AppAuth, hotelId: string): Promise<MemberRole> {
  const memberSnap = await memberRef(hotelId, requestAuth.uid).get();
  if (!memberSnap.exists || memberSnap.get("active") !== true) {
    throw new HttpsError("permission-denied", "You are not active in this hotel account.");
  }

  const role = String(memberSnap.get("role") || ROLE_STAFF).toUpperCase();
  if (![ROLE_OWNER, ROLE_MANAGER, ROLE_STAFF].includes(role)) {
    throw new HttpsError("permission-denied", "Your hotel role is not valid.");
  }
  return role as MemberRole;
}

export const importExistingHotels = onCall({ invoker: "public" }, async (request) => {
  const requestAuth = await requireAuth(request);
  if (!isPlatformAdmin(requestAuth)) {
    throw new HttpsError("permission-denied", "Only platform admin can import existing hotels.");
  }

  const hotels = await db.collection("hotels").limit(500).get();
  const now = FieldValue.serverTimestamp();
  const accessUntil = nowPlusDays(31);
  let createdAccounts = 0;
  let updatedAccounts = 0;
  let skippedHotels = 0;

  for (const hotel of hotels.docs) {
    const hotelId = hotel.id;
    const data = hotel.data();
    const ownerUid = String(data.ownerUid || uidFromHotelId(hotelId)).trim();

    if (!ownerUid) {
      skippedHotels += 1;
      continue;
    }

    let ownerEmail = normalizeEmail(data.ownerEmail || data.email);
    let displayName = String(data.hotelName || data.ownerName || "").trim();

    try {
      const owner = await auth.getUser(ownerUid);
      ownerEmail = ownerEmail || normalizeEmail(owner.email);
      displayName = displayName || String(owner.displayName || "").trim();
      await setAppClaims(owner, hotelId, ROLE_OWNER);
    } catch (error) {
      logger.warn("Existing hotel owner not found in Auth", { hotelId, ownerUid, error });
    }

    const account = await accountRef(hotelId).get();
    const accountData = {
      hotelId,
      ownerUid,
      ownerEmail,
      planId: account.get("planId") || DEFAULT_PLAN_ID,
      status: account.get("status") || STATUS_TRIALING,
      maxUsers: account.get("maxUsers") || DEFAULT_MAX_USERS,
      trialStartedAt: account.get("trialStartedAt") || now,
      trialEndsAt: account.get("trialEndsAt") || accessUntil,
      accessUntil: account.get("accessUntil") || accessUntil,
      source: "existing_hotel_import",
      createdAt: account.get("createdAt") || now,
      updatedAt: now,
      updatedByUid: requestAuth.uid,
    };

    await accountRef(hotelId).set(accountData, { merge: true });
    await memberRef(hotelId, ownerUid).set({
      uid: ownerUid,
      email: ownerEmail,
      displayName,
      role: ROLE_OWNER,
      active: true,
      importedAt: now,
      updatedAt: now,
    }, { merge: true });

    if (account.exists) {
      updatedAccounts += 1;
    } else {
      createdAccounts += 1;
    }
  }

  logger.info("Existing hotels imported", { createdAccounts, updatedAccounts, skippedHotels });
  return { scannedHotels: hotels.size, createdAccounts, updatedAccounts, skippedHotels };
});

function uidFromHotelId(hotelId: string): string {
  return hotelId.startsWith("hotel_") ? hotelId.substring("hotel_".length) : "";
}

function numberValue(value: unknown, fallback = 0): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function optionalNumberValue(value: unknown): number | null {
  if (value === null || value === undefined || value === "") return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function booleanValue(value: unknown, fallback = false): boolean {
  if (typeof value === "boolean") return value;
  if (typeof value === "number") return value !== 0;
  if (typeof value === "string") {
    const clean = value.trim().toLowerCase();
    if (["true", "1", "yes", "y"].includes(clean)) return true;
    if (["false", "0", "no", "n"].includes(clean)) return false;
  }
  return fallback;
}

function stringList(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.map((item) => String(item || "").trim()).filter(Boolean);
}

function startOfDay(millis: number): number {
  const date = new Date(millis);
  date.setHours(0, 0, 0, 0);
  return date.getTime();
}

function lockIdsFor(roomRemoteIds: string[], checkInMillis: number, checkOutMillis: number): Set<string> {
  const start = startOfDay(checkInMillis);
  const end = startOfDay(checkOutMillis);
  const lockIds = new Set<string>();
  if (end <= start) return lockIds;

  for (const roomId of roomRemoteIds) {
    for (let day = start; day < end; day += DAY_MILLIS) {
      lockIds.add(`${roomId}_${day}`);
    }
  }
  return lockIds;
}

function normaliseBookingPayload(raw: Record<string, unknown>, hotelId: string, uid: string) {
  const remoteId = requireString(raw.remoteId, "booking remoteId");
  const guestName = requireString(raw.guestName, "guest name");
  const roomRemoteIds = stringList(raw.roomRemoteIds);
  const checkInMillis = numberValue(raw.checkInMillis);
  const checkOutMillis = numberValue(raw.checkOutMillis);

  if (roomRemoteIds.length === 0) {
    throw new HttpsError("invalid-argument", "Select at least one room.");
  }
  if (checkOutMillis <= checkInMillis) {
    throw new HttpsError("invalid-argument", "Check-out must be after check-in.");
  }

  return {
    remoteId,
    baseRevision: numberValue(raw.baseRevision),
    cloudData: {
      bookingUuid: String(raw.bookingUuid || remoteId),
      hotelRemoteId: hotelId,
      propertyRemoteId: raw.propertyRemoteId ? String(raw.propertyRemoteId) : null,
      guestName,
      guestMobile: raw.guestMobile ? String(raw.guestMobile) : null,
      sourceName: raw.sourceName ? String(raw.sourceName) : null,
      sourceRemoteId: raw.sourceRemoteId ? String(raw.sourceRemoteId) : null,
      sourceType: String(raw.sourceType || "DIRECT"),
      grossCharges: numberValue(raw.grossCharges),
      roomRevenue: numberValue(raw.roomRevenue),
      propertyTax: numberValue(raw.propertyTax),
      commissionAmount: numberValue(raw.commissionAmount),
      commissionTax: numberValue(raw.commissionTax),
      sourceFee: numberValue(raw.sourceFee),
      tdsAmount: numberValue(raw.tdsAmount),
      tcsAmount: numberValue(raw.tcsAmount),
      expectedPayout: numberValue(raw.expectedPayout),
      adultCount: Math.max(0, Math.floor(numberValue(raw.adultCount, 1))),
      childCount: Math.max(0, Math.floor(numberValue(raw.childCount))),
      checkInMillis,
      checkOutMillis,
      roomRemoteIds,
      rate: numberValue(raw.rate),
      receivable: numberValue(raw.receivable || raw.finalPrice || raw.finalAmount),
      finalPrice: numberValue(raw.receivable || raw.finalPrice || raw.finalAmount),
      finalAmount: numberValue(raw.receivable || raw.finalPrice || raw.finalAmount),
      paid: numberValue(raw.paid || raw.advancePaid),
      advancePaid: numberValue(raw.paid || raw.advancePaid),
      balance: numberValue(raw.balance),
      paymentStatus: String(raw.paymentStatus || "NOT_PAID"),
      bookingStatus: String(raw.bookingStatus || "RESERVED"),
      actualCheckInAt: optionalNumberValue(raw.actualCheckInAt),
      actualCheckOutAt: optionalNumberValue(raw.actualCheckOutAt),
      checkoutNote: raw.checkoutNote ? String(raw.checkoutNote) : null,
      reopenNote: raw.reopenNote ? String(raw.reopenNote) : null,
      reopenedAt: optionalNumberValue(raw.reopenedAt),
      notes: raw.notes ? String(raw.notes) : null,
      updatedAt: numberValue(raw.updatedAt, Date.now()),
      isDeleted: booleanValue(raw.isDeleted),
      updatedByUid: uid,
      serverUpdatedAt: FieldValue.serverTimestamp(),
    },
  };
}

export const saveBookingServer = onCall({ invoker: "public" }, async (request) => {
  const requestAuth = await requireAuth(request);
  const hotelId = requireString(request.data?.hotelId || requestAuth.token.hotelId, "hotelId");
  await requireActiveHotelMember(requestAuth, hotelId);
  await requireUsableSubscription(hotelId);

  const { remoteId, baseRevision, cloudData } = normaliseBookingPayload(
    (request.data?.booking || {}) as Record<string, unknown>,
    hotelId,
    requestAuth.uid
  );
  const bookingDoc = publicHotelRef(hotelId).collection("bookings").doc(remoteId);
  const newLockIds = lockIdsFor(cloudData.roomRemoteIds, cloudData.checkInMillis, cloudData.checkOutMillis);

  const result = await db.runTransaction(async (tx) => {
    const existingBooking = await tx.get(bookingDoc);
    const remoteRevision = numberValue(existingBooking.get("revision"));
    if (existingBooking.exists && remoteRevision !== baseRevision) {
      throw new HttpsError("aborted", "This booking was changed on another device. Refresh before saving.");
    }

    for (const lockId of newLockIds) {
      const lockDoc = publicHotelRef(hotelId).collection("bookingLocks").doc(lockId);
      const lockSnapshot = await tx.get(lockDoc);
      const lockedBy = String(lockSnapshot.get("bookingRemoteId") || "");
      const lockDeleted = booleanValue(lockSnapshot.get("isDeleted"));
      if (lockSnapshot.exists && !lockDeleted && lockedBy !== remoteId) {
        throw new HttpsError("already-exists", "Selected room is already booked for these dates.");
      }
    }

    const oldRoomIds = existingBooking.exists ? stringList(existingBooking.get("roomRemoteIds")) : [];
    const oldLockIds = existingBooking.exists ?
      lockIdsFor(
        oldRoomIds,
        numberValue(existingBooking.get("checkInMillis"), cloudData.checkInMillis),
        numberValue(existingBooking.get("checkOutMillis"), cloudData.checkOutMillis)
      ) :
      new Set<string>();
    const nextRevision = remoteRevision + 1;

    for (const lockId of oldLockIds) {
      if (!newLockIds.has(lockId)) {
        tx.delete(publicHotelRef(hotelId).collection("bookingLocks").doc(lockId));
      }
    }

    tx.set(bookingDoc, {
      ...cloudData,
      revision: nextRevision,
    });

    for (const lockId of newLockIds) {
      const parts = lockId.split("_");
      const dateMillis = numberValue(parts[parts.length - 1]);
      const roomRemoteId = parts.slice(0, -1).join("_");
      tx.set(publicHotelRef(hotelId).collection("bookingLocks").doc(lockId), {
        hotelRemoteId: hotelId,
        bookingRemoteId: remoteId,
        roomRemoteId,
        dateMillis,
        isDeleted: false,
        updatedAt: cloudData.updatedAt,
        updatedByUid: requestAuth.uid,
        serverUpdatedAt: FieldValue.serverTimestamp(),
      });
    }

    return { revision: nextRevision, updatedByUid: requestAuth.uid };
  });

  return result;
});

function normaliseBookingFinancialLinePayload(
  raw: Record<string, unknown>,
  hotelId: string,
  bookingRemoteId: string,
  uid: string
) {
  const remoteId = requireString(raw.remoteId, "financial line remoteId");
  const roomRemoteId = requireString(raw.roomRemoteId, "financial line roomRemoteId");
  const businessDateMillis = numberValue(raw.businessDateMillis);

  if (businessDateMillis <= 0) {
    throw new HttpsError("invalid-argument", "Invalid room-night business date.");
  }

  return {
    remoteId,
    baseRevision: numberValue(raw.baseRevision),
    cloudData: {
      hotelRemoteId: hotelId,
      bookingRemoteId,
      roomRemoteId,
      propertyRemoteId: raw.propertyRemoteId ? String(raw.propertyRemoteId) : null,
      businessDateMillis,
      grossAmount: numberValue(raw.grossAmount),
      taxableAmount: numberValue(raw.taxableAmount),
      gstRatePercent: numberValue(raw.gstRatePercent),
      gstAmount: numberValue(raw.gstAmount),
      hsnSacCode: raw.hsnSacCode ? String(raw.hsnSacCode) : null,
      slabRemoteId: raw.slabRemoteId ? String(raw.slabRemoteId) : null,
      slabName: raw.slabName ? String(raw.slabName) : null,
      cgstRatePercent: numberValue(raw.cgstRatePercent),
      sgstRatePercent: numberValue(raw.sgstRatePercent),
      cessRatePercent: numberValue(raw.cessRatePercent),
      cgstAmount: numberValue(raw.cgstAmount),
      sgstAmount: numberValue(raw.sgstAmount),
      cessAmount: numberValue(raw.cessAmount),
      source: String(raw.source || "MANUAL"),
      updatedAt: numberValue(raw.updatedAt, Date.now()),
      isDeleted: booleanValue(raw.isDeleted),
      updatedByUid: uid,
      serverUpdatedAt: FieldValue.serverTimestamp(),
    },
  };
}

export const saveBookingAggregateServer = onCall({ invoker: "public" }, async (request) => {
  const requestAuth = await requireAuth(request);
  const hotelId = requireString(request.data?.hotelId || requestAuth.token.hotelId, "hotelId");
  await requireActiveHotelMember(requestAuth, hotelId);
  await requireUsableSubscription(hotelId);

  const operationId = requireString(request.data?.operationId, "operationId");

  const { remoteId, baseRevision, cloudData } = normaliseBookingPayload(
    (request.data?.booking || {}) as Record<string, unknown>,
    hotelId,
    requestAuth.uid
  );

  const rawLines = Array.isArray(request.data?.financialLines)
    ? request.data.financialLines as Array<Record<string, unknown>>
    : [];

  const financialLines = rawLines.map((line) =>
    normaliseBookingFinancialLinePayload(line, hotelId, remoteId, requestAuth.uid)
  );

  const hotelRef = publicHotelRef(hotelId);
  const bookingDoc = hotelRef.collection("bookings").doc(remoteId);
  const mutationDoc = hotelRef.collection("appliedBookingMutations").doc(operationId);
  const newLockIds = lockIdsFor(cloudData.roomRemoteIds, cloudData.checkInMillis, cloudData.checkOutMillis);

  if (financialLines.length + newLockIds.size > 430) {
    throw new HttpsError(
      "invalid-argument",
      "Booking has too many room-night rows for one safe sync operation."
    );
  }

  const result = await db.runTransaction(async (tx) => {
    const alreadyApplied = await tx.get(mutationDoc);

    if (alreadyApplied.exists) {
      const existingBookingRemoteId = String(alreadyApplied.get("bookingRemoteId") || "");
      if (existingBookingRemoteId !== remoteId) {
        throw new HttpsError(
          "aborted",
          "This sync operation ID was already used for another booking."
        );
      }

      return {
        operationId,
        bookingRemoteId: remoteId,
        bookingRevision: numberValue(alreadyApplied.get("bookingRevision")),
        financialLineRevisions: alreadyApplied.get("financialLineRevisions") || {},
        updatedByUid: String(alreadyApplied.get("updatedByUid") || requestAuth.uid),
        alreadyApplied: true,
      };
    }

    const existingBooking = await tx.get(bookingDoc);
    const remoteRevision = numberValue(existingBooking.get("revision"));

    if (existingBooking.exists && remoteRevision !== baseRevision) {
      throw new HttpsError(
        "aborted",
        "Cloud has another revision. The local booking was preserved for explicit conflict resolution."
      );
    }

    const lockSnapshots = new Map<string, DocumentSnapshot>();

    for (const lockId of newLockIds) {
      const lockDoc = hotelRef.collection("bookingLocks").doc(lockId);
      lockSnapshots.set(lockId, await tx.get(lockDoc));
    }

    for (const lockSnapshot of lockSnapshots.values()) {
      const lockedBy = String(lockSnapshot.get("bookingRemoteId") || "");
      const lockDeleted = booleanValue(lockSnapshot.get("isDeleted"));
      if (lockSnapshot.exists && !lockDeleted && lockedBy !== remoteId) {
        throw new HttpsError(
          "already-exists",
          "Selected room is already booked for these dates."
        );
      }
    }

    const lineSnapshots = new Map<string, DocumentSnapshot>();

    for (const line of financialLines) {
      const lineDoc = hotelRef.collection("bookingFinancialLines").doc(line.remoteId);
      lineSnapshots.set(line.remoteId, await tx.get(lineDoc));
    }

    for (const line of financialLines) {
      const lineSnapshot = lineSnapshots.get(line.remoteId);
      const lineRemoteRevision = numberValue(lineSnapshot?.get("revision"));

      if (lineSnapshot?.exists && lineRemoteRevision !== line.baseRevision) {
        throw new HttpsError(
          "aborted",
          "Room-night accounting changed on another device. Refresh before saving."
        );
      }
    }

    const oldRoomIds = existingBooking.exists
      ? stringList(existingBooking.get("roomRemoteIds"))
      : [];

    const oldLockIds = existingBooking.exists
      ? lockIdsFor(
          oldRoomIds,
          numberValue(existingBooking.get("checkInMillis"), cloudData.checkInMillis),
          numberValue(existingBooking.get("checkOutMillis"), cloudData.checkOutMillis)
        )
      : new Set<string>();

    const nextBookingRevision = remoteRevision + 1;
    const financialLineRevisions: Record<string, number> = {};

    for (const lockId of oldLockIds) {
      if (!newLockIds.has(lockId)) {
        tx.delete(hotelRef.collection("bookingLocks").doc(lockId));
      }
    }

    tx.set(bookingDoc, {
      ...cloudData,
      revision: nextBookingRevision,
    });

    for (const lockId of newLockIds) {
      const parts = lockId.split("_");
      const dateMillis = numberValue(parts[parts.length - 1]);
      const roomRemoteId = parts.slice(0, -1).join("_");

      tx.set(hotelRef.collection("bookingLocks").doc(lockId), {
        hotelRemoteId: hotelId,
        bookingRemoteId: remoteId,
        roomRemoteId,
        dateMillis,
        isDeleted: false,
        updatedAt: cloudData.updatedAt,
        updatedByUid: requestAuth.uid,
        serverUpdatedAt: FieldValue.serverTimestamp(),
      });
    }

    for (const line of financialLines) {
      const lineSnapshot = lineSnapshots.get(line.remoteId);
      const nextLineRevision = numberValue(lineSnapshot?.get("revision")) + 1;

      financialLineRevisions[line.remoteId] = nextLineRevision;

      tx.set(hotelRef.collection("bookingFinancialLines").doc(line.remoteId), {
        ...line.cloudData,
        revision: nextLineRevision,
      });
    }

    const successResult = {
      operationId,
      bookingRemoteId: remoteId,
      bookingRevision: nextBookingRevision,
      financialLineRevisions,
      updatedByUid: requestAuth.uid,
      alreadyApplied: false,
    };

    tx.set(mutationDoc, {
      ...successResult,
      hotelRemoteId: hotelId,
      createdAt: Date.now(),
      serverCreatedAt: FieldValue.serverTimestamp(),
    });

    return successResult;
  });

  return result;
});

export const deleteBookingServer = onCall({ invoker: "public" }, async (request) => {
  const requestAuth = await requireAuth(request);
  const hotelId = requireString(request.data?.hotelId || requestAuth.token.hotelId, "hotelId");
  await requireActiveHotelMember(requestAuth, hotelId);
  await requireUsableSubscription(hotelId);

  const bookingRemoteId = requireString(request.data?.bookingRemoteId, "bookingRemoteId");
  const baseRevision = numberValue(request.data?.baseRevision);
  const fallbackRoomIds = stringList(request.data?.roomRemoteIds);
  const fallbackCheckInMillis = numberValue(request.data?.checkInMillis);
  const fallbackCheckOutMillis = numberValue(request.data?.checkOutMillis);
  const bookingDoc = publicHotelRef(hotelId).collection("bookings").doc(bookingRemoteId);

  const result = await db.runTransaction(async (tx) => {
    const existingBooking = await tx.get(bookingDoc);
    const remoteRevision = numberValue(existingBooking.get("revision"));
    if (existingBooking.exists && remoteRevision !== baseRevision) {
      throw new HttpsError("aborted", "This booking was changed on another device. Refresh before deleting.");
    }

    const roomIds = existingBooking.exists ? stringList(existingBooking.get("roomRemoteIds")) : fallbackRoomIds;
    const checkInMillis = existingBooking.exists ?
      numberValue(existingBooking.get("checkInMillis")) :
      fallbackCheckInMillis;
    const checkOutMillis = existingBooking.exists ?
      numberValue(existingBooking.get("checkOutMillis")) :
      fallbackCheckOutMillis;
    const nextRevision = remoteRevision + 1;

    for (const lockId of lockIdsFor(roomIds, checkInMillis, checkOutMillis)) {
      tx.delete(publicHotelRef(hotelId).collection("bookingLocks").doc(lockId));
    }

    tx.set(bookingDoc, {
      isDeleted: true,
      revision: nextRevision,
      updatedAt: Date.now(),
      updatedByUid: requestAuth.uid,
      serverUpdatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });

    return { revision: nextRevision, updatedByUid: requestAuth.uid };
  });

  return result;
});

function normalizeInvoicePrefix(value: unknown): string {
  const cleaned = String(value || "INV")
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9-]/g, "")
    .slice(0, 12);
  return cleaned || "INV";
}

function financialYearShortIndia(timeMillis: number): string {
  const indiaMillis = timeMillis + 330 * 60 * 1000;
  const date = new Date(indiaMillis);
  const year = date.getUTCFullYear();
  const month = date.getUTCMonth();
  const startYear = month >= 3 ? year : year - 1;
  const endYear = startYear + 1;
  return `${String(startYear % 100).padStart(2, "0")}-${String(endYear % 100).padStart(2, "0")}`;
}

export const reserveInvoiceNumber = onCall({ invoker: "public" }, async (request) => {
  const requestAuth = await requireAuth(request);
  const hotelId = requireString(request.data?.hotelId || requestAuth.token.hotelId, "hotelId");
  await requireActiveHotelMember(requestAuth, hotelId);
  await requireUsableSubscription(hotelId);

  const prefix = normalizeInvoicePrefix(request.data?.prefix);
  const billMillis = numberValue(request.data?.billMillis, Date.now());
  const financialYear = financialYearShortIndia(billMillis);
  const seriesPrefix = `${prefix}/${financialYear}/`;
  const counterId = `${prefix}_${financialYear}`.replace(/[^A-Z0-9_-]/g, "_");
  const counterRef = publicHotelRef(hotelId).collection("invoiceCounters").doc(counterId);

  const result = await db.runTransaction(async (tx) => {
    const counter = await tx.get(counterRef);
    const nextSequence = numberValue(counter.get("lastSequence")) + 1;
    const billNumber = `${seriesPrefix}${String(nextSequence).padStart(4, "0")}`;

    tx.set(counterRef, {
      hotelRemoteId: hotelId,
      prefix,
      financialYear,
      seriesPrefix,
      lastSequence: nextSequence,
      lastBillNumber: billNumber,
      updatedAt: FieldValue.serverTimestamp(),
      updatedByUid: requestAuth.uid,
    }, { merge: true });

    return { billNumber, sequence: nextSequence, seriesPrefix };
  });

  logger.info("Invoice number reserved", { hotelId, billNumber: result.billNumber });
  return result;
});

