import { initializeApp } from "firebase-admin/app";
import { DecodedIdToken, getAuth, UserRecord } from "firebase-admin/auth";
import { DocumentReference, DocumentSnapshot, FieldPath, FieldValue, Timestamp, Transaction, getFirestore } from "firebase-admin/firestore";
import { CallableRequest, HttpsError, onCall } from "firebase-functions/v2/https";
import { logger, setGlobalOptions } from "firebase-functions/v2";

import {
  DEVICE_STATUS_ACTIVE,
  DEVICE_STATUS_LOGGED_OUT,
  decideDeviceClaim,
  normalizeDeviceId,
} from "./deviceSessionPolicy";

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

function deviceRef(hotelId: string, uid: string, deviceId: string) {
  return memberRef(hotelId, uid).collection("devices").doc(deviceId);
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

export const claimMyDevice = onCall({ invoker: "public" }, async (request) => {
      const requestAuth = await requireAuth(request);

      let hotelId = String(requestAuth.token.hotelId || "").trim();
      let member = hotelId
        ? await memberRef(hotelId, requestAuth.uid).get()
        : undefined;

      if (!hotelId || !member?.exists || member.get("active") !== true) {
        const memberships = await db.collectionGroup("members")
          .where("uid", "==", requestAuth.uid)
          .where("active", "==", true)
          .limit(1)
          .get();

        if (memberships.empty) {
          return {
            allowed: false,
            reason: "NO_ACTIVE_MEMBERSHIP",
          };
        }

        member = memberships.docs[0];
        hotelId = member.ref.parent.parent?.id || "";

        if (!hotelId) {
          return {
            allowed: false,
            reason: "NO_ACTIVE_MEMBERSHIP",
          };
        }
      }

      await requireUsableSubscription(hotelId);

      let deviceId: string;

      try {
        deviceId = normalizeDeviceId(request.data?.deviceId);
      } catch {
        throw new HttpsError(
          "invalid-argument",
          "Device identity is invalid."
        );
      }

      const deviceName = String(request.data?.deviceName || "")
        .trim()
        .slice(0, 120);

      const memberDocument = memberRef(hotelId, requestAuth.uid);
      const deviceDocument = deviceRef(
        hotelId,
        requestAuth.uid,
        deviceId
      );

      const decision = await db.runTransaction(async (tx) => {
        const currentMember = await tx.get(memberDocument);

        if (!currentMember.exists || currentMember.get("active") !== true) {
          return "NO_ACTIVE_MEMBERSHIP" as const;
        }

        const activeDeviceId = currentMember.get("activeDeviceId");
        const claimDecision = decideDeviceClaim(
          activeDeviceId,
          deviceId
        );

        if (claimDecision === "BLOCKED") {
          return "BLOCKED" as const;
        }

        const currentDevice = await tx.get(deviceDocument);
        const now = FieldValue.serverTimestamp();

        if (!currentDevice.exists) {
          tx.set(deviceDocument, {
            uid: requestAuth.uid,
            deviceId,
            deviceName,
            status: DEVICE_STATUS_ACTIVE,
            firstSeenAt: now,
            lastSeenAt: now,
          });
        } else {
          tx.set(deviceDocument, {
            deviceName,
            status: DEVICE_STATUS_ACTIVE,
            lastSeenAt: now,
          }, { merge: true });
        }

        tx.set(memberDocument, {
          activeDeviceId: deviceId,
          activeDeviceUpdatedAt: now,
        }, { merge: true });

        return claimDecision;
      });

      if (decision === "NO_ACTIVE_MEMBERSHIP") {
        return {
          allowed: false,
          reason: "NO_ACTIVE_MEMBERSHIP",
        };
      }

      if (decision === "BLOCKED") {
        return {
          allowed: false,
          reason: "DEVICE_ALREADY_ACTIVE",
        };
      }

      logger.info("Device session claimed", {
        hotelId,
        uid: requestAuth.uid,
        decision,
      });

      return {
        allowed: true,
        hotelId,
        deviceId,
        deviceStatus: DEVICE_STATUS_ACTIVE,
        decision,
      };
    });
export const logoutMyDevice = onCall({ invoker: "public" }, async (request) => {
  const requestAuth = await requireAuth(request);

  const hotelId = requireString(
    request.data?.hotelId || requestAuth.token.hotelId,
    "hotelId"
  );

  let deviceId: string;

  try {
    deviceId = normalizeDeviceId(request.data?.deviceId);
  } catch {
    throw new HttpsError(
      "invalid-argument",
      "Device identity is invalid."
    );
  }

  const memberDocument = memberRef(hotelId, requestAuth.uid);
  const deviceDocument = deviceRef(
    hotelId,
    requestAuth.uid,
    deviceId
  );

  const decision = await db.runTransaction(async (tx) => {
    const currentMember = await tx.get(memberDocument);

    if (!currentMember.exists) {
      return "NO_MEMBERSHIP" as const;
    }

    const activeDeviceId = String(
      currentMember.get("activeDeviceId") || ""
    ).trim();

    if (activeDeviceId !== deviceId) {
      return "NOT_ACTIVE_DEVICE" as const;
    }

    const currentDevice = await tx.get(deviceDocument);
    const now = FieldValue.serverTimestamp();

    if (!currentDevice.exists) {
      tx.set(deviceDocument, {
        uid: requestAuth.uid,
        deviceId,
        status: DEVICE_STATUS_LOGGED_OUT,
        lastSeenAt: now,
        loggedOutAt: now,
      });
    } else {
      tx.set(deviceDocument, {
        status: DEVICE_STATUS_LOGGED_OUT,
        lastSeenAt: now,
        loggedOutAt: now,
      }, { merge: true });
    }

    tx.set(memberDocument, {
      activeDeviceId: FieldValue.delete(),
      activeDeviceUpdatedAt: now,
    }, { merge: true });

    return "LOGGED_OUT" as const;
  });

  if (decision === "NO_MEMBERSHIP") {
    return {
      released: false,
      reason: "NO_MEMBERSHIP",
    };
  }

  if (decision === "NOT_ACTIVE_DEVICE") {
    return {
      released: false,
      reason: "NOT_ACTIVE_DEVICE",
    };
  }

  logger.info("Device session logged out", {
    hotelId,
    uid: requestAuth.uid,
    deviceId,
  });

  return {
    released: true,
    hotelId,
    deviceId,
    deviceStatus: DEVICE_STATUS_LOGGED_OUT,
  };
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

function normaliseFoodBillPayload(raw: Record<string, unknown>, hotelId: string, uid: string) {
  const remoteId = requireString(raw.remoteId, "food bill remoteId");
  const billNumber = requireString(raw.billNumber, "food bill number");

  return {
    remoteId,
    baseRevision: numberValue(raw.baseRevision),
    cloudData: {
      hotelRemoteId: hotelId,
      propertyRemoteId: raw.propertyRemoteId ? String(raw.propertyRemoteId) : null,
      supplierName: raw.supplierName ? String(raw.supplierName) : null,
      supplierGstin: raw.supplierGstin ? String(raw.supplierGstin) : null,
      supplierAddress: raw.supplierAddress ? String(raw.supplierAddress) : null,
      supplierPhone: raw.supplierPhone ? String(raw.supplierPhone) : null,
      supplierState: raw.supplierState ? String(raw.supplierState) : null,
      propertyDisplayName: raw.propertyDisplayName ? String(raw.propertyDisplayName) : null,
      billNumber,
      billMillis: numberValue(raw.billMillis, Date.now()),
      guestName: raw.guestName ? String(raw.guestName) : null,
      guestMobile: raw.guestMobile ? String(raw.guestMobile) : null,
      guestAddress: raw.guestAddress ? String(raw.guestAddress) : null,
      guestGstin: raw.guestGstin ? String(raw.guestGstin) : null,
      roomsIncluded: String(raw.roomsIncluded || ""),
      orderRemoteIds: String(raw.orderRemoteIds || ""),
      subtotal: numberValue(raw.subtotal),
      discountAmount: numberValue(raw.discountAmount),
      taxableAmount: numberValue(raw.taxableAmount),
      cgstAmount: numberValue(raw.cgstAmount),
      sgstAmount: numberValue(raw.sgstAmount),
      cessAmount: numberValue(raw.cessAmount),
      gstAmount: numberValue(raw.gstAmount),
      grandTotal: numberValue(raw.grandTotal),
      paymentMode: raw.paymentMode ? String(raw.paymentMode) : null,
      notes: raw.notes ? String(raw.notes) : null,
      status: String(raw.status || "ISSUED"),
      updatedAt: numberValue(raw.updatedAt, Date.now()),
      isDeleted: booleanValue(raw.isDeleted),
      updatedByUid: uid,
      serverUpdatedAt: FieldValue.serverTimestamp(),
    },
  };
}

function normaliseFoodBillItemPayload(
  raw: Record<string, unknown>,
  hotelId: string,
  billRemoteId: string,
  uid: string
) {
  const remoteId = requireString(raw.remoteId, "food bill item remoteId");
  const payloadBillRemoteId = requireString(raw.billRemoteId, "food bill item billRemoteId");
  if (payloadBillRemoteId !== billRemoteId) {
    throw new HttpsError("invalid-argument", "Food bill item belongs to another bill.");
  }

  return {
    remoteId,
    baseRevision: numberValue(raw.baseRevision),
    cloudData: {
      hotelRemoteId: hotelId,
      billRemoteId,
      orderRemoteId: String(raw.orderRemoteId || ""),
      orderNumber: raw.orderNumber ? String(raw.orderNumber) : null,
      orderMillis: numberValue(raw.orderMillis),
      roomName: raw.roomName ? String(raw.roomName) : null,
      menuItemRemoteId: raw.menuItemRemoteId ? String(raw.menuItemRemoteId) : null,
      itemName: requireString(raw.itemName, "food bill item name"),
      quantity: numberValue(raw.quantity, 1),
      unitPrice: numberValue(raw.unitPrice),
      lineSubtotal: numberValue(raw.lineSubtotal),
      gstCategoryRemoteId: raw.gstCategoryRemoteId ? String(raw.gstCategoryRemoteId) : null,
      gstCategoryName: raw.gstCategoryName ? String(raw.gstCategoryName) : null,
      hsnSacCode: raw.hsnSacCode ? String(raw.hsnSacCode) : null,
      gstRatePercent: numberValue(raw.gstRatePercent),
      cgstRatePercent: numberValue(raw.cgstRatePercent),
      sgstRatePercent: numberValue(raw.sgstRatePercent),
      cessRatePercent: numberValue(raw.cessRatePercent),
      taxableAmount: numberValue(raw.taxableAmount),
      cgstAmount: numberValue(raw.cgstAmount),
      sgstAmount: numberValue(raw.sgstAmount),
      cessAmount: numberValue(raw.cessAmount),
      gstAmount: numberValue(raw.gstAmount),
      lineTotal: numberValue(raw.lineTotal),
      updatedAt: numberValue(raw.updatedAt, Date.now()),
      isDeleted: booleanValue(raw.isDeleted),
      updatedByUid: uid,
      serverUpdatedAt: FieldValue.serverTimestamp(),
    },
  };
}

function normaliseFoodOrderPayload(
  raw: Record<string, unknown>,
  hotelId: string,
  billRemoteId: string | null,
  uid: string
) {
  const remoteId = requireString(raw.remoteId, "food order remoteId");
  const payloadBillRemoteId = raw.billRemoteId ? String(raw.billRemoteId) : null;
  const payloadLinkedFinalBillId = raw.linkedFinalBillId ? String(raw.linkedFinalBillId) : null;

  if (payloadBillRemoteId && payloadBillRemoteId !== billRemoteId) {
    throw new HttpsError("invalid-argument", "Food order belongs to another bill.");
  }
  if (payloadLinkedFinalBillId && payloadLinkedFinalBillId !== billRemoteId) {
    throw new HttpsError("invalid-argument", "Food order is linked to another final bill.");
  }

  return {
    remoteId,
    baseRevision: numberValue(raw.baseRevision),
    cloudData: {
      hotelRemoteId: hotelId,
      propertyRemoteId: raw.propertyRemoteId ? String(raw.propertyRemoteId) : null,
      bookingRemoteId: raw.bookingRemoteId ? String(raw.bookingRemoteId) : null,
      billRemoteId: payloadBillRemoteId,
      orderNumber: raw.orderNumber ? String(raw.orderNumber) : null,
      foodBillingScope: String(raw.foodBillingScope || "WALK_IN"),
      linkedFinalBillId: payloadLinkedFinalBillId,
      archivedAt: optionalNumberValue(raw.archivedAt),
      roomRemoteId: raw.roomRemoteId ? String(raw.roomRemoteId) : null,
      roomName: raw.roomName ? String(raw.roomName) : null,
      guestName: requireString(raw.guestName, "food order guest name"),
      orderMillis: numberValue(raw.orderMillis, Date.now()),
      status: String(raw.status || "OPEN"),
      subtotal: numberValue(raw.subtotal),
      discountAmount: numberValue(raw.discountAmount),
      taxableAmount: numberValue(raw.taxableAmount),
      gstAmount: numberValue(raw.gstAmount),
      totalAmount: numberValue(raw.totalAmount),
      notes: raw.notes ? String(raw.notes) : null,
      updatedAt: numberValue(raw.updatedAt, Date.now()),
      isDeleted: booleanValue(raw.isDeleted),
      updatedByUid: uid,
      serverUpdatedAt: FieldValue.serverTimestamp(),
    },
  };
}

function normaliseFoodOrderItemPayload(raw: Record<string, unknown>, hotelId: string, uid: string) {
  const remoteId = requireString(raw.remoteId, "food order item remoteId");

  return {
    remoteId,
    baseRevision: numberValue(raw.baseRevision),
    cloudData: {
      hotelRemoteId: hotelId,
      orderRemoteId: requireString(raw.orderRemoteId, "food order item orderRemoteId"),
      menuItemRemoteId: raw.menuItemRemoteId ? String(raw.menuItemRemoteId) : null,
      itemName: requireString(raw.itemName, "food order item name"),
      quantity: numberValue(raw.quantity, 1),
      unitPrice: numberValue(raw.unitPrice),
      gstRatePercent: numberValue(raw.gstRatePercent),
      gstCategoryRemoteId: raw.gstCategoryRemoteId ? String(raw.gstCategoryRemoteId) : null,
      gstCategoryName: raw.gstCategoryName ? String(raw.gstCategoryName) : null,
      hsnSacCode: raw.hsnSacCode ? String(raw.hsnSacCode) : null,
      cgstRatePercent: numberValue(raw.cgstRatePercent),
      sgstRatePercent: numberValue(raw.sgstRatePercent),
      cessRatePercent: numberValue(raw.cessRatePercent),
      lineSubtotal: numberValue(raw.lineSubtotal),
      lineGst: numberValue(raw.lineGst),
      lineTotal: numberValue(raw.lineTotal),
      isCancelled: booleanValue(raw.isCancelled),
      updatedAt: numberValue(raw.updatedAt, Date.now()),
      isDeleted: booleanValue(raw.isDeleted),
      updatedByUid: uid,
      serverUpdatedAt: FieldValue.serverTimestamp(),
    },
  };
}

function normaliseBookingAccountingChargePayload(
  raw: Record<string, unknown>,
  hotelId: string,
  billRemoteId: string | null,
  uid: string
) {
  const remoteId = requireString(raw.remoteId, "accounting charge remoteId");
  const linkedFinalBillId = raw.linkedFinalBillId ? String(raw.linkedFinalBillId) : null;
  if (linkedFinalBillId && linkedFinalBillId !== billRemoteId) {
    throw new HttpsError("invalid-argument", "Accounting charge is linked to another bill.");
  }

  return {
    remoteId,
    baseRevision: numberValue(raw.baseRevision),
    cloudData: {
      hotelRemoteId: hotelId,
      bookingRemoteId: requireString(raw.bookingRemoteId, "accounting charge bookingRemoteId"),
      chargeType: String(raw.chargeType || "SERVICE_CHARGE"),
      accountBucket: raw.accountBucket ? String(raw.accountBucket) : null,
      amount: numberValue(raw.amount),
      description: requireString(raw.description, "accounting charge description"),
      reason: raw.reason ? String(raw.reason) : null,
      hsnSacCode: raw.hsnSacCode ? String(raw.hsnSacCode) : null,
      gstRatePercent: numberValue(raw.gstRatePercent),
      taxInclusive: booleanValue(raw.taxInclusive, true),
      taxableAmount: optionalNumberValue(raw.taxableAmount),
      linkedFinalBillId,
      archivedAt: optionalNumberValue(raw.archivedAt),
      approvedBy: raw.approvedBy ? String(raw.approvedBy) : null,
      createdBy: raw.createdBy ? String(raw.createdBy) : null,
      chargeMillis: numberValue(raw.chargeMillis, Date.now()),
      updatedAt: numberValue(raw.updatedAt, Date.now()),
      isDeleted: booleanValue(raw.isDeleted),
      updatedByUid: uid,
      serverUpdatedAt: FieldValue.serverTimestamp(),
    },
  };
}

function normaliseBookingPaymentPayload(raw: Record<string, unknown>, hotelId: string, uid: string) {
  const remoteId = requireString(raw.remoteId, "booking payment remoteId");

  return {
    remoteId,
    baseRevision: numberValue(raw.baseRevision),
    cloudData: {
      hotelRemoteId: hotelId,
      bookingRemoteId: requireString(raw.bookingRemoteId, "booking payment bookingRemoteId"),
      originalPaymentRemoteId: raw.originalPaymentRemoteId ? String(raw.originalPaymentRemoteId) : null,
      paymentType: String(raw.paymentType || "PAYMENT"),
      paymentCategory: String(raw.paymentCategory || "AUTO"),
      amount: numberValue(raw.amount),
      allocatedStayAmount: numberValue(raw.allocatedStayAmount),
      allocatedFoodAmount: numberValue(raw.allocatedFoodAmount),
      allocatedServiceAmount: numberValue(raw.allocatedServiceAmount),
      allocatedDamageAmount: numberValue(raw.allocatedDamageAmount),
      unappliedAmount: numberValue(raw.unappliedAmount),
      paymentMillis: numberValue(raw.paymentMillis, Date.now()),
      method: raw.method ? String(raw.method) : null,
      note: raw.note ? String(raw.note) : null,
      updatedAt: numberValue(raw.updatedAt, Date.now()),
      isDeleted: booleanValue(raw.isDeleted),
      updatedByUid: uid,
      serverUpdatedAt: FieldValue.serverTimestamp(),
    },
  };
}

async function saveRevisionCheckedRecord(
  request: CallableRequest,
  collectionName: "bookingPayments" | "bookingAccountingCharges",
  mutationCollectionName: "appliedPaymentMutations" | "appliedAccountingChargeMutations",
  entityLabel: string,
  normalise: (raw: Record<string, unknown>, hotelId: string, uid: string) => {
    remoteId: string;
    baseRevision: number;
    cloudData: Record<string, unknown>;
  },
  validateInTransaction?: (
    tx: Transaction,
    hotelRef: DocumentReference,
    entity: { remoteId: string; baseRevision: number; cloudData: Record<string, unknown> }
  ) => Promise<void>
) {
  const requestAuth = await requireAuth(request);
  const hotelId = requireString(request.data?.hotelId || requestAuth.token.hotelId, "hotelId");
  await requireActiveHotelMember(requestAuth, hotelId);
  await requireUsableSubscription(hotelId);

  const operationId = requireString(request.data?.operationId, "operationId");
  const entity = normalise(
    (request.data?.entity || {}) as Record<string, unknown>,
    hotelId,
    requestAuth.uid
  );
  const hotelRef = publicHotelRef(hotelId);
  const entityDoc = hotelRef.collection(collectionName).doc(entity.remoteId);
  const mutationDoc = hotelRef.collection(mutationCollectionName).doc(operationId);

  return db.runTransaction(async (tx) => {
    const alreadyApplied = await tx.get(mutationDoc);
    if (alreadyApplied.exists) {
      if (String(alreadyApplied.get("remoteId") || "") !== entity.remoteId) {
        throw new HttpsError("aborted", `This ${entityLabel} operation ID was already used.`);
      }
      return {
        revision: numberValue(alreadyApplied.get("revision")),
        updatedByUid: String(alreadyApplied.get("updatedByUid") || requestAuth.uid),
        alreadyApplied: true,
      };
    }

    const existing = await tx.get(entityDoc);
    const remoteRevision = numberValue(existing.get("revision"));
    if (existing.exists && remoteRevision !== entity.baseRevision) {
      throw new HttpsError(
        "aborted",
        `This ${entityLabel} changed on another device. Refresh before syncing.`
      );
    }

    if (validateInTransaction) {
      await validateInTransaction(tx, hotelRef, entity);
    }

    const revision = remoteRevision + 1;
    tx.set(entityDoc, { ...entity.cloudData, revision });
    tx.set(mutationDoc, {
      remoteId: entity.remoteId,
      revision,
      updatedByUid: requestAuth.uid,
      createdAt: Date.now(),
      serverCreatedAt: FieldValue.serverTimestamp(),
    });
    return { revision, updatedByUid: requestAuth.uid, alreadyApplied: false };
  });
}

export const saveBookingPaymentServer = onCall({ invoker: "public" }, async (request) =>
  saveRevisionCheckedRecord(
    request,
    "bookingPayments",
    "appliedPaymentMutations",
    "payment",
    normaliseBookingPaymentPayload,
    async (tx, hotelRef, entity) => {
      const allocationFields = [
        "allocatedStayAmount",
        "allocatedFoodAmount",
        "allocatedServiceAmount",
        "allocatedDamageAmount",
        "unappliedAmount",
      ];
      if (allocationFields.some((field) => numberValue(entity.cloudData[field]) < 0)) {
        throw new HttpsError("invalid-argument", "Payment allocations cannot be negative.");
      }
      const allocationTotal = allocationFields.reduce(
        (sum, field) => sum + numberValue(entity.cloudData[field]),
        0
      );
      if (Math.abs(allocationTotal - numberValue(entity.cloudData.amount)) > 0.02) {
        throw new HttpsError("invalid-argument", "Payment allocations must equal the payment amount.");
      }
      const paymentType = String(entity.cloudData.paymentType || "");
      const bookingRemoteId = requireString(entity.cloudData.bookingRemoteId, "payment bookingRemoteId");
      const booking = await tx.get(hotelRef.collection("bookings").doc(bookingRemoteId));
      if (!booking.exists || booleanValue(booking.get("isDeleted"))) {
        throw new HttpsError("failed-precondition", "Booking was not found.");
      }
      const bookingCancelled = String(booking.get("bookingStatus") || "") === "CANCELLED";
      if (bookingCancelled && ["PAYMENT", "ADVANCE"].includes(paymentType)) {
        throw new HttpsError("failed-precondition", "Payments cannot be added to a cancelled booking.");
      }
      const isRefund = paymentType === "REFUND";
      const isCorrection = paymentType === "ADJUSTMENT";
      if (!isRefund && !isCorrection) return;
      if (isRefund && bookingCancelled) {
        if (String(booking.get("cancellationSettlementStatus") || "PENDING") !== "DECIDED") {
          throw new HttpsError(
            "failed-precondition",
            "Decide the Direct Booking cancellation settlement before recording a refund."
          );
        }
        const bookingRefunds = await tx.get(
          hotelRef.collection("bookingPayments")
            .where("bookingRemoteId", "==", bookingRemoteId)
            .where("paymentType", "==", "REFUND")
        );
        const totalRefunds = bookingRefunds.docs
          .filter((doc) => doc.id !== entity.remoteId && !booleanValue(doc.get("isDeleted")))
          .reduce((sum, doc) => sum + numberValue(doc.get("amount")), 0);
        const refundsAfterDecision = Math.max(
          0,
          totalRefunds - numberValue(booking.get("cancellationRefundBaselineAmount"))
        );
        const refundDue = Math.max(
          0,
          numberValue(booking.get("cancellationApprovedRefundAmount")) - refundsAfterDecision
        );
        if (numberValue(entity.cloudData.amount) > refundDue + 0.001) {
          throw new HttpsError(
            "failed-precondition",
            "Refund exceeds the approved cancellation refund."
          );
        }
      }
      const originalPaymentRemoteId = requireString(
        entity.cloudData.originalPaymentRemoteId,
        isCorrection ? "payment to correct" : "original payment"
      );
      const original = await tx.get(hotelRef.collection("bookingPayments").doc(originalPaymentRemoteId));
      if (!original.exists || booleanValue(original.get("isDeleted"))) {
        throw new HttpsError("failed-precondition", "Original payment was not found.");
      }
      if (String(original.get("bookingRemoteId") || "") !== String(entity.cloudData.bookingRemoteId || "")) {
        throw new HttpsError("invalid-argument", "Original payment belongs to another booking.");
      }
      if (!["PAYMENT", "ADVANCE"].includes(String(original.get("paymentType") || ""))) {
        throw new HttpsError(
          "failed-precondition",
          isCorrection ? "Only a payment or advance can be corrected." : "Only a payment or advance can be refunded."
        );
      }
      const priorReversals = await tx.get(
        hotelRef.collection("bookingPayments")
          .where("originalPaymentRemoteId", "==", originalPaymentRemoteId)
      );
      const reversedAmount = priorReversals.docs
        .filter((doc) => doc.id !== entity.remoteId && !booleanValue(doc.get("isDeleted")))
        .filter((doc) => ["REFUND", "ADJUSTMENT"].includes(String(doc.get("paymentType") || "")))
        .reduce((sum, doc) => sum + numberValue(doc.get("amount")), 0);
      const remainingAmount = Math.max(0, numberValue(original.get("amount")) - reversedAmount);
      const requestedAmount = numberValue(entity.cloudData.amount);
      if (requestedAmount > remainingAmount + 0.001) {
        throw new HttpsError(
          "failed-precondition",
          isCorrection ?
            "Correction exceeds the remaining original payment." :
            "Refund exceeds the remaining refundable amount."
        );
      }
      if (isCorrection && Math.abs(requestedAmount - remainingAmount) > 0.001) {
        throw new HttpsError(
          "failed-precondition",
          "Correction must reverse the full remaining original payment. Re-enter the correct payment afterwards."
        );
      }
      const ratio = requestedAmount / numberValue(original.get("amount"));
      const originalCategory = String(original.get("paymentCategory") || "AUTO").toUpperCase();
      const originalAllocationTotal =
        numberValue(original.get("allocatedStayAmount")) +
        numberValue(original.get("allocatedFoodAmount")) +
        numberValue(original.get("allocatedServiceAmount")) +
        numberValue(original.get("allocatedDamageAmount")) +
        numberValue(original.get("unappliedAmount"));
      if (isCorrection && originalAllocationTotal <= 0.001) {
        const correctionCategory = ["FOOD", "SERVICE", "DAMAGE"].includes(originalCategory) ? originalCategory : "STAY";
        entity.cloudData.paymentCategory = correctionCategory;
        entity.cloudData.allocatedStayAmount = correctionCategory === "STAY" ? requestedAmount : 0;
        entity.cloudData.allocatedFoodAmount = correctionCategory === "FOOD" ? requestedAmount : 0;
        entity.cloudData.allocatedServiceAmount = correctionCategory === "SERVICE" ? requestedAmount : 0;
        entity.cloudData.allocatedDamageAmount = correctionCategory === "DAMAGE" ? requestedAmount : 0;
        entity.cloudData.unappliedAmount = 0;
      } else {
        if (isCorrection && originalAllocationTotal > numberValue(original.get("amount")) + 0.02) {
          throw new HttpsError(
            "failed-precondition",
            "Original payment allocation is inconsistent. Contact support before correcting it."
          );
        }
        const implicitUnapplied = isCorrection ?
          Math.max(0, numberValue(original.get("amount")) - originalAllocationTotal) : 0;
        entity.cloudData.paymentCategory = originalCategory;
        entity.cloudData.allocatedStayAmount = numberValue(original.get("allocatedStayAmount")) * ratio;
        entity.cloudData.allocatedFoodAmount = numberValue(original.get("allocatedFoodAmount")) * ratio;
        entity.cloudData.allocatedServiceAmount = numberValue(original.get("allocatedServiceAmount")) * ratio;
        entity.cloudData.allocatedDamageAmount = numberValue(original.get("allocatedDamageAmount")) * ratio;
        entity.cloudData.unappliedAmount =
          (numberValue(original.get("unappliedAmount")) + implicitUnapplied) * ratio;
      }
    }
  )
);

export const saveBookingAccountingChargeServer = onCall({ invoker: "public" }, async (request) =>
  saveRevisionCheckedRecord(
    request,
    "bookingAccountingCharges",
    "appliedAccountingChargeMutations",
    "service or damage charge",
    (raw, hotelId, uid) => normaliseBookingAccountingChargePayload(raw, hotelId, null, uid),
    async (tx, hotelRef, entity) => {
      const bookingRemoteId = requireString(
        entity.cloudData.bookingRemoteId,
        "accounting charge bookingRemoteId"
      );
      const booking = await tx.get(hotelRef.collection("bookings").doc(bookingRemoteId));
      if (!booking.exists || booleanValue(booking.get("isDeleted"))) {
        throw new HttpsError("failed-precondition", "Booking was not found.");
      }
      if (String(booking.get("bookingStatus") || "") === "CANCELLED") {
        throw new HttpsError("failed-precondition", "New charges cannot be added to a cancelled booking.");
      }
    }
  )
);

export const saveFoodOrderAggregateServer = onCall({ invoker: "public" }, async (request) => {
  const requestAuth = await requireAuth(request);
  const hotelId = requireString(request.data?.hotelId || requestAuth.token.hotelId, "hotelId");
  await requireActiveHotelMember(requestAuth, hotelId);
  await requireUsableSubscription(hotelId);

  const operationId = requireString(request.data?.operationId, "operationId");
  const order = normaliseFoodOrderPayload(
    (request.data?.order || {}) as Record<string, unknown>,
    hotelId,
    null,
    requestAuth.uid
  );
  const rawItems = Array.isArray(request.data?.orderItems)
    ? request.data.orderItems as Array<Record<string, unknown>>
    : [];
  const orderItems = rawItems.map((item) =>
    normaliseFoodOrderItemPayload(item, hotelId, requestAuth.uid)
  );
  for (const item of orderItems) {
    if (String(item.cloudData.orderRemoteId || "") !== order.remoteId) {
      throw new HttpsError("invalid-argument", "Food order item belongs to another order.");
    }
  }
  if (orderItems.length > 420) {
    throw new HttpsError("invalid-argument", "Food order has too many items for one safe sync operation.");
  }

  const hotelRef = publicHotelRef(hotelId);
  const orderDoc = hotelRef.collection("foodOrders").doc(order.remoteId);
  const mutationDoc = hotelRef.collection("appliedFoodOrderMutations").doc(operationId);

  return db.runTransaction(async (tx) => {
    const alreadyApplied = await tx.get(mutationDoc);
    if (alreadyApplied.exists) {
      if (String(alreadyApplied.get("orderRemoteId") || "") !== order.remoteId) {
        throw new HttpsError("aborted", "This food order operation ID was already used.");
      }
      return {
        orderRevision: numberValue(alreadyApplied.get("orderRevision")),
        orderItemRevisions: alreadyApplied.get("orderItemRevisions") || {},
        updatedByUid: String(alreadyApplied.get("updatedByUid") || requestAuth.uid),
        alreadyApplied: true,
      };
    }

    const existingOrder = await tx.get(orderDoc);
    const orderBookingRemoteId = String(order.cloudData.bookingRemoteId || "");
    if (orderBookingRemoteId) {
      const booking = await tx.get(hotelRef.collection("bookings").doc(orderBookingRemoteId));
      if (!booking.exists || booleanValue(booking.get("isDeleted"))) {
        throw new HttpsError("failed-precondition", "Booking was not found.");
      }
      if (String(booking.get("bookingStatus") || "") === "CANCELLED") {
        throw new HttpsError("failed-precondition", "Food cannot be added to a cancelled booking.");
      }
    }
    const remoteOrderRevision = numberValue(existingOrder.get("revision"));
    if (existingOrder.exists && remoteOrderRevision !== order.baseRevision) {
      throw new HttpsError("aborted", "This food order changed on another device. Refresh before syncing.");
    }
    if (
      existingOrder.exists &&
      !booleanValue(existingOrder.get("isDeleted")) &&
      (String(existingOrder.get("billRemoteId") || "") ||
        String(existingOrder.get("linkedFinalBillId") || ""))
    ) {
      throw new HttpsError("failed-precondition", "This food order is already billed in cloud.");
    }

    const itemSnapshots = new Map<string, DocumentSnapshot>();
    for (const item of orderItems) {
      const itemDoc = hotelRef.collection("foodOrderItems").doc(item.remoteId);
      itemSnapshots.set(item.remoteId, await tx.get(itemDoc));
    }
    for (const item of orderItems) {
      const snapshot = itemSnapshots.get(item.remoteId);
      if (snapshot?.exists && numberValue(snapshot.get("revision")) !== item.baseRevision) {
        throw new HttpsError(
          "aborted",
          "A food order item changed on another device. Refresh before syncing."
        );
      }
    }

    const orderRevision = remoteOrderRevision + 1;
    const orderItemRevisions: Record<string, number> = {};
    tx.set(orderDoc, { ...order.cloudData, revision: orderRevision });
    for (const item of orderItems) {
      const nextRevision = numberValue(itemSnapshots.get(item.remoteId)?.get("revision")) + 1;
      orderItemRevisions[item.remoteId] = nextRevision;
      tx.set(hotelRef.collection("foodOrderItems").doc(item.remoteId), {
        ...item.cloudData,
        revision: nextRevision,
      });
    }

    const result = {
      orderRemoteId: order.remoteId,
      orderRevision,
      orderItemRevisions,
      updatedByUid: requestAuth.uid,
      alreadyApplied: false,
    };
    tx.set(mutationDoc, {
      ...result,
      createdAt: Date.now(),
      serverCreatedAt: FieldValue.serverTimestamp(),
    });
    return result;
  });
});

export const saveFoodBillAggregateServer = onCall({ invoker: "public" }, async (request) => {
  const requestAuth = await requireAuth(request);
  const hotelId = requireString(request.data?.hotelId || requestAuth.token.hotelId, "hotelId");
  await requireActiveHotelMember(requestAuth, hotelId);
  await requireUsableSubscription(hotelId);

  const operationId = requireString(request.data?.operationId, "operationId");
  const bill = normaliseFoodBillPayload(
    (request.data?.bill || {}) as Record<string, unknown>,
    hotelId,
    requestAuth.uid
  );

  const rawBillItems = Array.isArray(request.data?.billItems)
    ? request.data.billItems as Array<Record<string, unknown>>
    : [];
  const rawOrders = Array.isArray(request.data?.orders)
    ? request.data.orders as Array<Record<string, unknown>>
    : [];
  const rawOrderItems = Array.isArray(request.data?.orderItems)
    ? request.data.orderItems as Array<Record<string, unknown>>
    : [];
  const rawAccountingCharges = Array.isArray(request.data?.accountingCharges)
    ? request.data.accountingCharges as Array<Record<string, unknown>>
    : [];

  const billItems = rawBillItems.map((item) =>
    normaliseFoodBillItemPayload(item, hotelId, bill.remoteId, requestAuth.uid)
  );
  const orders = rawOrders.map((order) =>
    normaliseFoodOrderPayload(order, hotelId, bill.remoteId, requestAuth.uid)
  );
  const orderItems = rawOrderItems.map((item) =>
    normaliseFoodOrderItemPayload(item, hotelId, requestAuth.uid)
  );
  const accountingCharges = rawAccountingCharges.map((charge) =>
    normaliseBookingAccountingChargePayload(charge, hotelId, bill.remoteId, requestAuth.uid)
  );

  if (billItems.length === 0) {
    throw new HttpsError("invalid-argument", "Bill must contain at least one item.");
  }

  const orderIds = new Set(orders.map((order) => order.remoteId));
  for (const item of orderItems) {
    const orderRemoteId = String(item.cloudData.orderRemoteId || "");
    if (!orderIds.has(orderRemoteId)) {
      throw new HttpsError("invalid-argument", "Food order item belongs to an order outside this bill aggregate.");
    }
  }

  if (billItems.length + orders.length + orderItems.length + accountingCharges.length > 420) {
    throw new HttpsError(
      "invalid-argument",
      "Food bill has too many linked rows for one safe sync operation."
    );
  }

  const hotelRef = publicHotelRef(hotelId);
  const mutationDoc = hotelRef.collection("appliedFoodBillMutations").doc(operationId);
  const billDoc = hotelRef.collection("foodBills").doc(bill.remoteId);

  const result = await db.runTransaction(async (tx) => {
    const alreadyApplied = await tx.get(mutationDoc);

    if (alreadyApplied.exists) {
      const existingBillRemoteId = String(alreadyApplied.get("billRemoteId") || "");
      if (existingBillRemoteId !== bill.remoteId) {
        throw new HttpsError(
          "aborted",
          "This food bill sync operation ID was already used for another bill."
        );
      }

      return {
        operationId,
        billRemoteId: bill.remoteId,
        billRevision: numberValue(alreadyApplied.get("billRevision")),
        foodBillItemRevisions: alreadyApplied.get("foodBillItemRevisions") || {},
        foodOrderRevisions: alreadyApplied.get("foodOrderRevisions") || {},
        foodOrderItemRevisions: alreadyApplied.get("foodOrderItemRevisions") || {},
        accountingChargeRevisions: alreadyApplied.get("accountingChargeRevisions") || {},
        updatedByUid: String(alreadyApplied.get("updatedByUid") || requestAuth.uid),
        alreadyApplied: true,
      };
    }

    const duplicateBillSnapshot = await tx.get(
      hotelRef.collection("foodBills")
        .where("billNumber", "==", bill.cloudData.billNumber)
        .limit(5)
    );
    duplicateBillSnapshot.docs.forEach((doc) => {
      if (doc.id !== bill.remoteId && !booleanValue(doc.get("isDeleted"))) {
        throw new HttpsError(
          "already-exists",
          "This bill number already exists in cloud."
        );
      }
    });

    const existingBill = await tx.get(billDoc);
    const finalBillMarker = "_final_bill_";
    const markerIndex = bill.remoteId.indexOf(finalBillMarker);
    if (markerIndex > 0) {
      const bookingRemoteId = bill.remoteId.substring(0, markerIndex);
      const booking = await tx.get(hotelRef.collection("bookings").doc(bookingRemoteId));
      if (!booking.exists || booleanValue(booking.get("isDeleted"))) {
        throw new HttpsError("failed-precondition", "Booking was not found.");
      }
      if (String(booking.get("bookingStatus") || "") === "CANCELLED") {
        throw new HttpsError(
          "failed-precondition",
          "A final bill cannot be generated for a cancelled booking."
        );
      }
    }
    const remoteBillRevision = numberValue(existingBill.get("revision"));
    if (existingBill.exists && remoteBillRevision !== bill.baseRevision) {
      throw new HttpsError(
        "aborted",
        "This bill was changed on another device. Refresh before syncing."
      );
    }

    const billItemSnapshots = new Map<string, DocumentSnapshot>();
    for (const item of billItems) {
      const itemDoc = hotelRef.collection("foodBillItems").doc(item.remoteId);
      billItemSnapshots.set(item.remoteId, await tx.get(itemDoc));
    }

    const orderSnapshots = new Map<string, DocumentSnapshot>();
    for (const order of orders) {
      const orderDoc = hotelRef.collection("foodOrders").doc(order.remoteId);
      orderSnapshots.set(order.remoteId, await tx.get(orderDoc));
    }

    const orderItemSnapshots = new Map<string, DocumentSnapshot>();
    for (const item of orderItems) {
      const itemDoc = hotelRef.collection("foodOrderItems").doc(item.remoteId);
      orderItemSnapshots.set(item.remoteId, await tx.get(itemDoc));
    }

    const chargeSnapshots = new Map<string, DocumentSnapshot>();
    for (const charge of accountingCharges) {
      const chargeDoc = hotelRef.collection("bookingAccountingCharges").doc(charge.remoteId);
      chargeSnapshots.set(charge.remoteId, await tx.get(chargeDoc));
    }

    for (const item of billItems) {
      const itemSnapshot = billItemSnapshots.get(item.remoteId);
      const itemRemoteRevision = numberValue(itemSnapshot?.get("revision"));
      if (itemSnapshot?.exists && itemRemoteRevision !== item.baseRevision) {
        throw new HttpsError(
          "aborted",
          "A bill item changed on another device. Refresh before syncing."
        );
      }
    }

    for (const order of orders) {
      const orderSnapshot = orderSnapshots.get(order.remoteId);
      const orderRemoteRevision = numberValue(orderSnapshot?.get("revision"));
      if (orderSnapshot?.exists && orderRemoteRevision !== order.baseRevision) {
        throw new HttpsError(
          "aborted",
          "A food order changed on another device. Refresh before billing."
        );
      }

      const existingOrderBillId = String(orderSnapshot?.get("billRemoteId") || "");
      const existingOrderFinalBillId = String(orderSnapshot?.get("linkedFinalBillId") || "");
      const existingOrderDeleted = booleanValue(orderSnapshot?.get("isDeleted"));
      if (
        orderSnapshot?.exists &&
        !existingOrderDeleted &&
        ((existingOrderBillId && existingOrderBillId !== bill.remoteId) ||
          (existingOrderFinalBillId && existingOrderFinalBillId !== bill.remoteId))
      ) {
        throw new HttpsError(
          "already-exists",
          "One selected food order is already billed in cloud."
        );
      }
    }

    for (const item of orderItems) {
      const itemSnapshot = orderItemSnapshots.get(item.remoteId);
      const itemRemoteRevision = numberValue(itemSnapshot?.get("revision"));
      if (itemSnapshot?.exists && itemRemoteRevision !== item.baseRevision) {
        throw new HttpsError(
          "aborted",
          "A food order item changed on another device. Refresh before billing."
        );
      }
    }

    for (const charge of accountingCharges) {
      const chargeSnapshot = chargeSnapshots.get(charge.remoteId);
      const chargeRemoteRevision = numberValue(chargeSnapshot?.get("revision"));
      if (chargeSnapshot?.exists && chargeRemoteRevision !== charge.baseRevision) {
        throw new HttpsError(
          "aborted",
          "A service or damage charge changed on another device. Refresh before billing."
        );
      }

      const existingLinkedBillId = String(chargeSnapshot?.get("linkedFinalBillId") || "");
      const existingChargeDeleted = booleanValue(chargeSnapshot?.get("isDeleted"));
      if (chargeSnapshot?.exists && !existingChargeDeleted && existingLinkedBillId && existingLinkedBillId !== bill.remoteId) {
        throw new HttpsError(
          "already-exists",
          "A service or damage charge is already linked to another bill."
        );
      }
    }

    const billRevision = remoteBillRevision + 1;
    const foodBillItemRevisions: Record<string, number> = {};
    const foodOrderRevisions: Record<string, number> = {};
    const foodOrderItemRevisions: Record<string, number> = {};
    const accountingChargeRevisions: Record<string, number> = {};

    tx.set(billDoc, {
      ...bill.cloudData,
      revision: billRevision,
    });

    for (const item of billItems) {
      const itemSnapshot = billItemSnapshots.get(item.remoteId);
      const nextRevision = numberValue(itemSnapshot?.get("revision")) + 1;
      foodBillItemRevisions[item.remoteId] = nextRevision;
      tx.set(hotelRef.collection("foodBillItems").doc(item.remoteId), {
        ...item.cloudData,
        revision: nextRevision,
      });
    }

    for (const order of orders) {
      const orderSnapshot = orderSnapshots.get(order.remoteId);
      const nextRevision = numberValue(orderSnapshot?.get("revision")) + 1;
      foodOrderRevisions[order.remoteId] = nextRevision;
      tx.set(hotelRef.collection("foodOrders").doc(order.remoteId), {
        ...order.cloudData,
        revision: nextRevision,
      });
    }

    for (const item of orderItems) {
      const itemSnapshot = orderItemSnapshots.get(item.remoteId);
      const nextRevision = numberValue(itemSnapshot?.get("revision")) + 1;
      foodOrderItemRevisions[item.remoteId] = nextRevision;
      tx.set(hotelRef.collection("foodOrderItems").doc(item.remoteId), {
        ...item.cloudData,
        revision: nextRevision,
      });
    }

    for (const charge of accountingCharges) {
      const chargeSnapshot = chargeSnapshots.get(charge.remoteId);
      const nextRevision = numberValue(chargeSnapshot?.get("revision")) + 1;
      accountingChargeRevisions[charge.remoteId] = nextRevision;
      tx.set(hotelRef.collection("bookingAccountingCharges").doc(charge.remoteId), {
        ...charge.cloudData,
        revision: nextRevision,
      });
    }

    const successResult = {
      operationId,
      billRemoteId: bill.remoteId,
      billRevision,
      foodBillItemRevisions,
      foodOrderRevisions,
      foodOrderItemRevisions,
      accountingChargeRevisions,
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

/** Applies a protected room lifecycle transition after checking cloud history. */
export const changeRoomLifecycleServer = onCall({ invoker: "public" }, async (request) => {
  const requestAuth = await requireAuth(request);
  const hotelId = requireString(request.data?.hotelId || requestAuth.token.hotelId, "hotelId");
  await requireOwnerOrManager(requestAuth, hotelId);
  await requireUsableSubscription(hotelId);

  const operationId = requireString(request.data?.operationId, "operationId");
  const roomRemoteId = requireString(request.data?.roomRemoteId, "roomRemoteId");
  const action = requireString(request.data?.action, "action").toUpperCase();
  const reason = String(request.data?.reason || "").trim();
  if (!["DELETE", "DISABLE", "RETIRE", "REACTIVATE"].includes(action)) {
    throw new HttpsError("invalid-argument", "Invalid room lifecycle action.");
  }
  if (["DISABLE", "RETIRE"].includes(action) && !reason) {
    throw new HttpsError("invalid-argument", "A reason is required.");
  }

  const hotelRef = publicHotelRef(hotelId);
  const roomDoc = hotelRef.collection("rooms").doc(roomRemoteId);
  const mutationDoc = hotelRef.collection("appliedRoomLifecycleOperations").doc(operationId);
  const auditDoc = hotelRef.collection("roomLifecycleAuditEvents").doc(operationId);

  return db.runTransaction(async (tx) => {
    const applied = await tx.get(mutationDoc);
    if (applied.exists) {
      if (String(applied.get("roomRemoteId") || "") !== roomRemoteId ||
          String(applied.get("action") || "") !== action) {
        throw new HttpsError("aborted", "This operation ID was already used for another room action.");
      }
      return {
        operationId,
        roomRemoteId,
        action,
        revision: numberValue(applied.get("revision")),
        updatedByUid: String(applied.get("updatedByUid") || requestAuth.uid),
        deleted: booleanValue(applied.get("deleted")),
        alreadyApplied: true,
      };
    }

    const roomSnapshot = await tx.get(roomDoc);
    if (!roomSnapshot.exists) {
      if (action !== "DELETE") {
        throw new HttpsError("not-found", "The room no longer exists.");
      }
      const missingResult = {
        operationId,
        roomRemoteId,
        action,
        revision: 0,
        updatedByUid: requestAuth.uid,
        deleted: true,
        alreadyApplied: false,
      };
      tx.set(mutationDoc, {
        ...missingResult,
        hotelRemoteId: hotelId,
        createdAt: FieldValue.serverTimestamp(),
      });
      return missingResult;
    }
    if (booleanValue(roomSnapshot.get("isDeleted"))) {
      if (action !== "DELETE") {
        throw new HttpsError("failed-precondition", "The room has already been deleted.");
      }
    }

    const bookings = await tx.get(
      hotelRef.collection("bookings").where("roomRemoteIds", "array-contains", roomRemoteId)
    );
    const financialLines = await tx.get(
      hotelRef.collection("bookingFinancialLines").where("roomRemoteId", "==", roomRemoteId).limit(1)
    );
    const foodOrders = await tx.get(
      hotelRef.collection("foodOrders").where("roomRemoteId", "==", roomRemoteId).limit(1)
    );
    const hasHistory = !bookings.empty || !financialLines.empty || !foodOrders.empty;
    const now = Date.now();
    const blockingBookings = bookings.docs.filter((booking) => {
      const status = String(booking.get("bookingStatus") || "RESERVED");
      return !booleanValue(booking.get("isDeleted")) &&
        ["RESERVED", "CHECKED_IN"].includes(status) &&
        numberValue(booking.get("checkOutMillis")) > now;
    });

    if (action === "DELETE" && hasHistory) {
      throw new HttpsError(
        "failed-precondition",
        "This room has booking or billing history and cannot be deleted. Disable or retire it instead."
      );
    }
    if (["DISABLE", "RETIRE"].includes(action) && blockingBookings.length > 0) {
      throw new HttpsError(
        "failed-precondition",
        "Move, cancel, or check out all current/future bookings before changing this room."
      );
    }

    if (action === "RETIRE") {
      const unbilledPastBookings = [];
      for (const booking of bookings.docs) {
        const status = String(booking.get("bookingStatus") || "RESERVED");
        const isPast = !booleanValue(booking.get("isDeleted")) &&
          status !== "CANCELLED" &&
          numberValue(booking.get("checkOutMillis")) <= now;
        if (!isPast) continue;
        const finalBillPrefix = `${booking.id}_final_bill_`;
        const finalBills = await tx.get(
          hotelRef.collection("foodBills")
            .where(FieldPath.documentId(), ">=", finalBillPrefix)
            .where(FieldPath.documentId(), "<", `${finalBillPrefix}\uf8ff`)
            .limit(5)
        );
        if (!finalBills.docs.some((bill) => !booleanValue(bill.get("isDeleted")))) {
          unbilledPastBookings.push(booking.id);
        }
      }
      if (unbilledPastBookings.length > 0) {
        throw new HttpsError(
          "failed-precondition",
          "Generate the final bill for all past bookings before retiring this room."
        );
      }
    }

    const previousStatus = String(roomSnapshot.get("lifecycleStatus") || "ACTIVE");
    if (previousStatus === "RETIRED" && action !== "RETIRE") {
      throw new HttpsError("failed-precondition", "A retired room cannot be changed or reactivated.");
    }

    const revision = numberValue(roomSnapshot.get("revision")) + 1;
    const deleted = action === "DELETE";
    const result = {
      operationId,
      roomRemoteId,
      action,
      revision,
      updatedByUid: requestAuth.uid,
      deleted,
      alreadyApplied: false,
    };

    if (deleted) {
      tx.delete(roomDoc);
    } else {
      const nextStatus = action === "REACTIVATE" ? "ACTIVE" : action === "DISABLE" ? "DISABLED" : "RETIRED";
      tx.set(roomDoc, {
        lifecycleStatus: nextStatus,
        lifecycleReason: nextStatus === "ACTIVE" ? null : reason,
        disabledAtMillis: nextStatus === "DISABLED" ? now : null,
        retiredAtMillis: nextStatus === "RETIRED" ? now : null,
        isDeleted: false,
        updatedAt: now,
        revision,
        updatedByUid: requestAuth.uid,
        serverUpdatedAt: FieldValue.serverTimestamp(),
      }, { merge: true });
    }
    tx.set(auditDoc, {
      hotelRemoteId: hotelId,
      roomRemoteId,
      operationId,
      action,
      reason: reason || null,
      previousStatus,
      newStatus: deleted ? "DELETED" :
        action === "REACTIVATE" ? "ACTIVE" : action === "DISABLE" ? "DISABLED" : "RETIRED",
      userUid: requestAuth.uid,
      serverTime: FieldValue.serverTimestamp(),
    });
    tx.set(mutationDoc, {
      ...result,
      hotelRemoteId: hotelId,
      createdAt: FieldValue.serverTimestamp(),
    });
    return result;
  });
});

/** Applies one receptionist Save as a field/room change set against the latest cloud booking. */
export const applyBookingChangeSetServer = onCall({ invoker: "public" }, async (request) => {
  const requestAuth = await requireAuth(request);
  const hotelId = requireString(request.data?.hotelId || requestAuth.token.hotelId, "hotelId");
  await requireActiveHotelMember(requestAuth, hotelId);
  await requireUsableSubscription(hotelId);

  const operationId = requireString(request.data?.operationId, "operationId");
  const deviceId = requireString(request.data?.deviceId, "deviceId");
  const changeSet = (request.data?.changeSet || {}) as Record<string, unknown>;
  const bookingRemoteId = requireString(changeSet.bookingRemoteId, "bookingRemoteId");
  const create = booleanValue(changeSet.create);
  const setFields = (changeSet.setFields || {}) as Record<string, unknown>;
  const addRoomIds = new Set(stringList(changeSet.addRoomRemoteIds));
  const removeRoomIds = new Set(stringList(changeSet.removeRoomRemoteIds));
  const requestedFinancialLineRebuild = booleanValue(changeSet.rebuildFinancialLines);
  const template = (changeSet.financialLineTemplate || {}) as Record<string, unknown>;
  const requestedLineIds = (changeSet.financialLineRemoteIdsByKey || {}) as Record<string, unknown>;

  const allowedFields = new Set([
    "bookingUuid", "propertyRemoteId", "guestName", "guestMobile", "sourceName", "sourceRemoteId",
    "sourceType", "adultCount", "childCount", "checkInMillis", "checkOutMillis", "pricingStatus",
    "bookingStatus", "cancelledAt", "cancellationReason",
    "cancellationSettlementStatus", "cancellationSettlementOutcome",
    "cancellationApprovedRefundAmount", "cancellationFeeAmount",
    "cancellationRefundBaselineAmount", "notes", "grossCharges", "rate",
    "receivable", "roomRevenue", "propertyTax", "commissionAmount", "commissionTax", "sourceFee",
    "tdsAmount", "tcsAmount", "expectedPayout",
  ]);
  Object.keys(setFields).forEach((field) => {
    if (!allowedFields.has(field)) throw new HttpsError("invalid-argument", `Unsupported booking field: ${field}`);
  });
  if (Array.from(addRoomIds).some((roomId) => removeRoomIds.has(roomId))) {
    throw new HttpsError("invalid-argument", "The same room cannot be added and removed in one save.");
  }
  if (addRoomIds.size === 0 && removeRoomIds.size === 0 && Object.keys(setFields).length === 0 &&
      !requestedFinancialLineRebuild) {
    throw new HttpsError("invalid-argument", "This save contains no booking changes.");
  }
  const financialImpactFields = new Set([
    "propertyRemoteId", "sourceName", "sourceRemoteId", "sourceType",
    "checkInMillis", "checkOutMillis", "pricingStatus", "grossCharges", "rate",
    "receivable", "roomRevenue", "propertyTax", "commissionAmount", "commissionTax",
    "sourceFee", "tdsAmount", "tcsAmount", "expectedPayout",
  ]);
  const serverDetectedFinancialImpact =
    create ||
    addRoomIds.size > 0 ||
    removeRoomIds.size > 0 ||
    Object.keys(setFields).some((field) => financialImpactFields.has(field));
  // This flag is a calculation instruction, never an authorization boundary.
  // The server independently detects all known room-billing changes so a stale,
  // faulty, or modified client cannot bypass issued-bill protection.
  const rebuildFinancialLines = requestedFinancialLineRebuild || serverDetectedFinancialImpact;

  const hotelRef = publicHotelRef(hotelId);
  const bookingDoc = hotelRef.collection("bookings").doc(bookingRemoteId);
  const mutationDoc = hotelRef.collection("appliedBookingChangeSets").doc(operationId);
  const auditDoc = hotelRef.collection("bookingAuditEvents").doc(operationId);

  return db.runTransaction(async (tx) => {
    const applied = await tx.get(mutationDoc);
    if (applied.exists) {
      if (String(applied.get("bookingRemoteId") || "") !== bookingRemoteId) {
        throw new HttpsError("aborted", "This operation ID was already used for another booking.");
      }
      return {
        operationId,
        bookingRemoteId,
        bookingRevision: numberValue(applied.get("bookingRevision")),
        financialLineRevisions: applied.get("financialLineRevisions") || {},
        updatedByUid: String(applied.get("updatedByUid") || requestAuth.uid),
        alreadyApplied: true,
      };
    }

    const currentSnapshot = await tx.get(bookingDoc);
    if (create && currentSnapshot.exists && !booleanValue(currentSnapshot.get("isDeleted"))) {
      throw new HttpsError("already-exists", "This booking already exists.");
    }
    if (!create && !currentSnapshot.exists) {
      throw new HttpsError("not-found", "The booking no longer exists.");
    }
    const current = currentSnapshot.exists ? currentSnapshot.data()! : {};
    const next: Record<string, unknown> = { ...current };
    Object.entries(setFields).forEach(([field, value]) => { next[field] = value; });
    next.hotelRemoteId = hotelId;
    next.updatedByUid = requestAuth.uid;
    next.updatedAt = Date.now();
    next.isDeleted = false;

    const previousRooms = new Set(stringList(current.roomRemoteIds));
    const nextRooms = new Set(previousRooms);
    addRoomIds.forEach((roomId) => nextRooms.add(roomId));
    removeRoomIds.forEach((roomId) => nextRooms.delete(roomId));
    if (nextRooms.size === 0) throw new HttpsError("invalid-argument", "Select at least one room.");
    next.roomRemoteIds = Array.from(nextRooms);

    const checkInMillis = numberValue(next.checkInMillis);
    const checkOutMillis = numberValue(next.checkOutMillis);
    if (checkInMillis <= 0 || checkOutMillis <= checkInMillis) {
      throw new HttpsError("invalid-argument", "Check-out must be after check-in.");
    }
    if (!String(next.guestName || "").trim()) throw new HttpsError("invalid-argument", "Guest name is required.");
    const cancelled = String(next.bookingStatus || "RESERVED") === "CANCELLED";
    const wasCancelled = String(current.bookingStatus || "RESERVED") === "CANCELLED";
    if (cancelled && !String(next.cancellationReason || "").trim()) {
      const fullAggregateSync =
        Object.prototype.hasOwnProperty.call(setFields, "bookingUuid") &&
        Object.prototype.hasOwnProperty.call(setFields, "checkInMillis") &&
        Object.prototype.hasOwnProperty.call(setFields, "checkOutMillis");
      if (wasCancelled || fullAggregateSync) {
        // Versions before cancellation reasons were introduced converted deleted
        // bookings into CANCELLED records without inventing a reason. Preserve those
        // records honestly when their full aggregate is synchronized or recovered.
        next.cancellationReason = "Legacy cancellation — reason not recorded";
      } else {
        throw new HttpsError("invalid-argument", "Cancellation reason is required.");
      }
    }
    if (cancelled && !wasCancelled &&
        !Object.prototype.hasOwnProperty.call(setFields, "cancellationSettlementStatus")) {
      // Safe rolling-upgrade behavior: an older APK may cancel without knowing the
      // settlement fields. Never guess that money is forfeited; require later review.
      next.cancellationSettlementStatus = "PENDING";
      next.cancellationSettlementOutcome = null;
      next.cancellationApprovedRefundAmount = 0;
      next.cancellationFeeAmount = 0;
      next.cancellationRefundBaselineAmount = 0;
    }
    if (!next.cancellationSettlementStatus) {
      next.cancellationSettlementStatus = cancelled ? "PENDING" : "NOT_APPLICABLE";
    }
    const settlementStatus = String(next.cancellationSettlementStatus);
    const validSettlementStatuses = new Set(["NOT_APPLICABLE", "NOT_REQUIRED", "PENDING", "DECIDED"]);
    if (!validSettlementStatuses.has(settlementStatus)) {
      throw new HttpsError("invalid-argument", "Invalid cancellation settlement status.");
    }
    if (cancelled && settlementStatus === "NOT_APPLICABLE") {
      throw new HttpsError("invalid-argument", "A cancelled booking must have a settlement status.");
    }
    if (!cancelled && settlementStatus !== "NOT_APPLICABLE") {
      throw new HttpsError("invalid-argument", "Settlement decisions apply only to cancelled bookings.");
    }
    if (cancelled && String(next.sourceType || "DIRECT") === "OTA" && settlementStatus !== "PENDING") {
      throw new HttpsError("failed-precondition", "OTA cancellation settlement must remain pending in this version.");
    }
    const settlementOutcome = next.cancellationSettlementOutcome == null
      ? ""
      : String(next.cancellationSettlementOutcome);
    const validSettlementOutcomes = new Set(["NO_REFUND", "PARTIAL_REFUND", "FULL_REFUND"]);
    if (settlementStatus === "DECIDED" && !validSettlementOutcomes.has(settlementOutcome)) {
      throw new HttpsError("invalid-argument", "A decided cancellation requires a valid outcome.");
    }
    if (settlementStatus !== "DECIDED" && settlementOutcome) {
      throw new HttpsError("invalid-argument", "Only a decided cancellation may have a settlement outcome.");
    }
    for (const moneyField of [
      "cancellationApprovedRefundAmount",
      "cancellationFeeAmount",
      "cancellationRefundBaselineAmount",
    ]) {
      const amount = numberValue(next[moneyField]);
      if (!Number.isFinite(amount) || amount < 0) {
        throw new HttpsError("invalid-argument", `${moneyField} cannot be negative.`);
      }
      next[moneyField] = amount;
    }
    const priorSettlementStatus = String(current.cancellationSettlementStatus || "NOT_APPLICABLE");
    const priorSettlementOutcome = current.cancellationSettlementOutcome == null
      ? ""
      : String(current.cancellationSettlementOutcome);
    const settlementDecisionChanged =
      (Object.prototype.hasOwnProperty.call(setFields, "cancellationSettlementStatus") &&
        settlementStatus !== priorSettlementStatus) ||
      (Object.prototype.hasOwnProperty.call(setFields, "cancellationSettlementOutcome") &&
        settlementOutcome !== priorSettlementOutcome) ||
      [
        "cancellationApprovedRefundAmount",
        "cancellationFeeAmount",
        "cancellationRefundBaselineAmount",
      ].some((field) =>
        Object.prototype.hasOwnProperty.call(setFields, field) &&
        Math.abs(numberValue(next[field]) - numberValue(current[field])) > 0.001
      );
    if (wasCancelled &&
        ["DECIDED", "NOT_REQUIRED"].includes(priorSettlementStatus) &&
        settlementDecisionChanged) {
      throw new HttpsError(
        "failed-precondition",
        "This cancellation decision is already recorded; use an append-only correction."
      );
    }
    const approvedRefundAmount = numberValue(next.cancellationApprovedRefundAmount);
    const cancellationFeeAmount = numberValue(next.cancellationFeeAmount);
    if (settlementStatus !== "DECIDED" &&
        (approvedRefundAmount > 0.001 || cancellationFeeAmount > 0.001)) {
      throw new HttpsError(
        "invalid-argument",
        "Pending or payment-free cancellation cannot contain a refund approval or cancellation fee."
      );
    }
    if (settlementStatus === "DECIDED") {
      if (settlementOutcome === "NO_REFUND" && approvedRefundAmount > 0.001) {
        throw new HttpsError("invalid-argument", "A no-refund decision cannot approve a refund.");
      }
      if (settlementOutcome === "FULL_REFUND" && cancellationFeeAmount > 0.001) {
        throw new HttpsError("invalid-argument", "A full-refund decision cannot contain a cancellation fee.");
      }
      if (settlementOutcome === "PARTIAL_REFUND" &&
          (approvedRefundAmount <= 0.001 || cancellationFeeAmount <= 0.001)) {
        throw new HttpsError(
          "invalid-argument",
          "A partial refund requires both a refund amount and a retained cancellation fee."
        );
      }
    }
    if (settlementStatus !== priorSettlementStatus &&
        (settlementStatus === "DECIDED" || settlementStatus === "NOT_REQUIRED")) {
      next.cancellationDecisionAt = Date.now();
      next.cancellationDecisionByUid = requestAuth.uid;
    }

    const roomSnapshots = new Map<string, DocumentSnapshot>();
    for (const roomId of nextRooms) {
      roomSnapshots.set(roomId, await tx.get(hotelRef.collection("rooms").doc(roomId)));
    }
    for (const room of roomSnapshots.values()) {
      if (!room.exists || booleanValue(room.get("isDeleted"))) {
        throw new HttpsError("failed-precondition", "A selected room no longer exists.");
      }
      if (String(room.get("lifecycleStatus") || "ACTIVE") !== "ACTIVE") {
        throw new HttpsError("failed-precondition", `${String(room.get("roomName") || "Room")} is disabled or retired.`);
      }
    }
    const propertyKeys = new Set(Array.from(roomSnapshots.values()).map((room) =>
      String(room.get("propertyRemoteId") || "__MAIN_PROPERTY__")
    ));
    if (propertyKeys.size !== 1) {
      throw new HttpsError("invalid-argument", "All selected rooms must belong to the same property.");
    }
    const propertyKey = Array.from(propertyKeys)[0];
    next.propertyRemoteId = propertyKey === "__MAIN_PROPERTY__" ? null : propertyKey;

    const oldLockIds = currentSnapshot.exists
      ? lockIdsFor(stringList(current.roomRemoteIds), numberValue(current.checkInMillis), numberValue(current.checkOutMillis))
      : new Set<string>();
    const newLockIds = cancelled ? new Set<string>() : lockIdsFor(Array.from(nextRooms), checkInMillis, checkOutMillis);
    const lockSnapshots = new Map<string, DocumentSnapshot>();
    const blockingIds = new Set<string>();
    for (const lockId of newLockIds) {
      const snapshot = await tx.get(hotelRef.collection("bookingLocks").doc(lockId));
      lockSnapshots.set(lockId, snapshot);
      const lockedBy = String(snapshot.get("bookingRemoteId") || "");
      if (snapshot.exists && !booleanValue(snapshot.get("isDeleted")) && lockedBy && lockedBy !== bookingRemoteId) {
        blockingIds.add(lockedBy);
      }
    }
    const blockingBookings = new Map<string, DocumentSnapshot>();
    for (const id of blockingIds) blockingBookings.set(id, await tx.get(hotelRef.collection("bookings").doc(id)));
    for (const lock of lockSnapshots.values()) {
      const lockedBy = String(lock.get("bookingRemoteId") || "");
      const blocker = blockingBookings.get(lockedBy);
      if (blocker?.exists && !booleanValue(blocker.get("isDeleted")) && String(blocker.get("bookingStatus") || "") !== "CANCELLED") {
        throw new HttpsError("already-exists", "A selected room is already booked for these dates.");
      }
    }

    const financialSnapshot = await tx.get(
      hotelRef.collection("bookingFinancialLines").where("bookingRemoteId", "==", bookingRemoteId)
    );
    const finalBillPrefix = `${bookingRemoteId}_final_bill_`;
    const finalBills = await tx.get(
      hotelRef.collection("foodBills")
        .where(FieldPath.documentId(), ">=", finalBillPrefix)
        .where(FieldPath.documentId(), "<", `${finalBillPrefix}\uf8ff`)
        .limit(5)
    );
    const cancellationRequested =
      String(next.bookingStatus || "RESERVED") === "CANCELLED" &&
      String(current.bookingStatus || "RESERVED") !== "CANCELLED";
    if ((rebuildFinancialLines || cancellationRequested) &&
        finalBills.docs.some((doc) => !booleanValue(doc.get("isDeleted")))) {
      throw new HttpsError(
        "failed-precondition",
        "This booking's room billing is locked because the final bill has been issued."
      );
    }

    const estimatedFinancialWrites = rebuildFinancialLines ? newLockIds.size + financialSnapshot.size : 0;
    const estimatedWrites = 4 + newLockIds.size + Array.from(oldLockIds).filter((id) => !newLockIds.has(id)).length + estimatedFinancialWrites;
    if (estimatedWrites > 450) {
      throw new HttpsError("invalid-argument", "This booking is too large to save safely in one atomic operation.");
    }

    const revision = numberValue(current.revision) + 1;
    const previousValues: Record<string, unknown> = {};
    const acceptedValues: Record<string, unknown> = {};
    Object.keys(setFields).forEach((field) => {
      previousValues[field] = current[field] ?? null;
      acceptedValues[field] = next[field] ?? null;
    });
    previousValues.roomRemoteIds = Array.from(previousRooms);
    acceptedValues.roomRemoteIds = Array.from(nextRooms);

    for (const lockId of oldLockIds) if (!newLockIds.has(lockId)) tx.delete(hotelRef.collection("bookingLocks").doc(lockId));
    tx.set(bookingDoc, { ...next, revision, serverUpdatedAt: FieldValue.serverTimestamp() });
    for (const lockId of newLockIds) {
      const parts = lockId.split("_");
      tx.set(hotelRef.collection("bookingLocks").doc(lockId), {
        hotelRemoteId: hotelId,
        bookingRemoteId,
        roomRemoteId: parts.slice(0, -1).join("_"),
        dateMillis: numberValue(parts[parts.length - 1]),
        isDeleted: false,
        updatedByUid: requestAuth.uid,
        serverUpdatedAt: FieldValue.serverTimestamp(),
      });
    }

    const financialLineRevisions: Record<string, number> = {};
    if (rebuildFinancialLines) {
      const grossPaise = Math.max(0, Math.round(numberValue(next.grossCharges) * 100));
      const dates: number[] = [];
      // checkInMillis/checkOutMillis already carry the app's exact business-date identity.
      // Re-normalising them in the Cloud Run timezone changes an India-local midnight
      // into a different epoch value and creates a second semantic room-night line.
      for (let day = checkInMillis; day < checkOutMillis; day += DAY_MILLIS) dates.push(day);
      const keys = Array.from(nextRooms).flatMap((roomId) => dates.map((day) => `${roomId}|${day}`));
      const existingByKey = new Map(financialSnapshot.docs.map((doc) => [`${doc.get("roomRemoteId")}|${numberValue(doc.get("businessDateMillis"))}`, doc]));
      const expectedIds = new Set<string>();
      const basePaise = keys.length ? Math.floor(grossPaise / keys.length) : 0;
      const remainder = keys.length ? grossPaise % keys.length : 0;
      keys.forEach((key, index) => {
        const [roomId, dayText] = key.split("|");
        const day = Number(dayText);
        const existing = existingByKey.get(key);
        const lineId = String(requestedLineIds[key] || existing?.id || `line_${bookingRemoteId}_${roomId}_${day}`);
        expectedIds.add(lineId);
        const gross = (basePaise + (index < remainder ? 1 : 0)) / 100;
        const gstRate = numberValue(template.gstRatePercent, numberValue(existing?.get("gstRatePercent")));
        const taxable = Math.round((gstRate > 0 ? gross / (1 + gstRate / 100) : gross) * 100) / 100;
        const gst = Math.round((gross - taxable) * 100) / 100;
        const lineRevision = numberValue(existing?.get("revision")) + 1;
        financialLineRevisions[lineId] = lineRevision;
        tx.set(hotelRef.collection("bookingFinancialLines").doc(lineId), {
          hotelRemoteId: hotelId, bookingRemoteId, roomRemoteId: roomId, propertyRemoteId: next.propertyRemoteId,
          businessDateMillis: day, grossAmount: gross, taxableAmount: taxable, gstRatePercent: gstRate,
          gstAmount: gst, hsnSacCode: template.hsnSacCode ?? existing?.get("hsnSacCode") ?? null,
          slabRemoteId: template.slabRemoteId ?? existing?.get("slabRemoteId") ?? null,
          slabName: template.slabName ?? existing?.get("slabName") ?? null,
          cgstRatePercent: numberValue(template.cgstRatePercent, numberValue(existing?.get("cgstRatePercent"))),
          sgstRatePercent: numberValue(template.sgstRatePercent, numberValue(existing?.get("sgstRatePercent"))),
          cessRatePercent: numberValue(template.cessRatePercent, numberValue(existing?.get("cessRatePercent"))),
          cgstAmount: Math.round(gst / 2 * 100) / 100, sgstAmount: Math.round((gst - Math.round(gst / 2 * 100) / 100) * 100) / 100,
          cessAmount: 0, source: String(template.source || existing?.get("source") || "MANUAL"),
          updatedAt: Date.now(), isDeleted: false, revision: lineRevision,
          updatedByUid: requestAuth.uid, serverUpdatedAt: FieldValue.serverTimestamp(),
        });
      });
      financialSnapshot.docs.filter((doc) => !expectedIds.has(doc.id) && !booleanValue(doc.get("isDeleted"))).forEach((doc) => {
        const lineRevision = numberValue(doc.get("revision")) + 1;
        financialLineRevisions[doc.id] = lineRevision;
        tx.set(doc.ref, { isDeleted: true, updatedAt: Date.now(), revision: lineRevision,
          updatedByUid: requestAuth.uid, serverUpdatedAt: FieldValue.serverTimestamp() }, { merge: true });
      });
    }

    const result = {
      operationId,
      bookingRemoteId,
      bookingRevision: revision,
      financialLineRevisions,
      updatedByUid: requestAuth.uid,
      alreadyApplied: false,
    };
    tx.set(auditDoc, {
      hotelRemoteId: hotelId, bookingRemoteId, operationId, userUid: requestAuth.uid, deviceId,
      setFields: Object.keys(setFields), previousValues, newValues: acceptedValues,
      addRoomRemoteIds: Array.from(addRoomIds), removeRoomRemoteIds: Array.from(removeRoomIds),
      serverTime: FieldValue.serverTimestamp(),
    });
    tx.set(mutationDoc, { ...result, hotelRemoteId: hotelId, createdAt: FieldValue.serverTimestamp() });
    return result;
  });
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
