"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.reserveInvoiceNumber = exports.applyBookingChangeSetServer = exports.saveFoodBillAggregateServer = exports.saveFoodOrderAggregateServer = exports.saveBookingAccountingChargeServer = exports.saveBookingPaymentServer = exports.importExistingHotels = exports.listHotelAccounts = exports.getMyHotelAccess = exports.setHotelSubscription = exports.setHotelUserActive = exports.createHotelUser = exports.bootstrapHotelOwner = void 0;
const app_1 = require("firebase-admin/app");
const auth_1 = require("firebase-admin/auth");
const firestore_1 = require("firebase-admin/firestore");
const https_1 = require("firebase-functions/v2/https");
const v2_1 = require("firebase-functions/v2");
(0, app_1.initializeApp)();
(0, v2_1.setGlobalOptions)({ region: "asia-south1", maxInstances: 10 });
const db = (0, firestore_1.getFirestore)();
const auth = (0, auth_1.getAuth)();
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
async function requireAuth(request) {
    if (request.auth) {
        return request.auth;
    }
    const idToken = String(request.data?.idToken || "").trim();
    if (!idToken) {
        throw new https_1.HttpsError("unauthenticated", "Please sign in again.");
    }
    try {
        const token = await auth.verifyIdToken(idToken, true);
        return { uid: token.uid, token };
    }
    catch (error) {
        v2_1.logger.warn("Manual ID token verification failed", error);
        throw new https_1.HttpsError("unauthenticated", "Please sign in again.");
    }
}
function hotelIdForOwner(uid) {
    return `hotel_${uid}`;
}
function nowPlusDays(days) {
    return firestore_1.Timestamp.fromMillis(Date.now() + days * 24 * 60 * 60 * 1000);
}
function accountRef(hotelId) {
    return db.collection("hotelAccounts").doc(hotelId);
}
function memberRef(hotelId, uid) {
    return accountRef(hotelId).collection("members").doc(uid);
}
function publicHotelRef(hotelId) {
    return db.collection("hotels").doc(hotelId);
}
function normalizeEmail(value) {
    return String(value || "").trim().toLowerCase();
}
function requireString(value, field) {
    const text = String(value || "").trim();
    if (!text) {
        throw new https_1.HttpsError("invalid-argument", `${field} is required.`);
    }
    return text;
}
function optionalPositiveInt(value, fallback) {
    const parsed = Number(value);
    if (!Number.isFinite(parsed) || parsed <= 0)
        return fallback;
    return Math.floor(parsed);
}
function parseStatus(value) {
    const status = String(value || "").trim().toUpperCase();
    if ([STATUS_TRIALING, STATUS_ACTIVE, STATUS_PAST_DUE, STATUS_SUSPENDED].includes(status)) {
        return status;
    }
    throw new https_1.HttpsError("invalid-argument", "Invalid subscription status.");
}
function parseRole(value) {
    const role = String(value || ROLE_STAFF).trim().toUpperCase();
    if ([ROLE_MANAGER, ROLE_STAFF].includes(role)) {
        return role;
    }
    throw new https_1.HttpsError("invalid-argument", "Invalid member role.");
}
function platformAdminEmails() {
    return new Set(String(process.env.PLATFORM_ADMIN_EMAILS || "")
        .split(",")
        .map((email) => email.trim().toLowerCase())
        .filter(Boolean));
}
function isPlatformAdmin(requestAuth) {
    const email = normalizeEmail(requestAuth.token.email);
    return requestAuth.token.platformAdmin === true || platformAdminEmails().has(email);
}
async function setAppClaims(user, hotelId, role) {
    await auth.setCustomUserClaims(user.uid, {
        ...(user.customClaims || {}),
        hotelId,
        hotelRole: role,
    });
}
async function requireOwnerOrManager(requestAuth, hotelId) {
    const memberSnap = await memberRef(hotelId, requestAuth.uid).get();
    if (!memberSnap.exists || memberSnap.get("active") !== true) {
        throw new https_1.HttpsError("permission-denied", "You are not active in this hotel account.");
    }
    const role = String(memberSnap.get("role") || "").toUpperCase();
    if (role !== ROLE_OWNER && role !== ROLE_MANAGER) {
        throw new https_1.HttpsError("permission-denied", "Only owner or manager can do this.");
    }
    return role;
}
async function requireUsableSubscription(hotelId) {
    const account = await accountRef(hotelId).get();
    if (!account.exists) {
        throw new https_1.HttpsError("failed-precondition", "Hotel account is not created yet.");
    }
    const status = String(account.get("status") || "");
    const accessUntil = account.get("accessUntil");
    const hasTime = accessUntil ? accessUntil.toMillis() >= Date.now() : false;
    if (![STATUS_TRIALING, STATUS_ACTIVE].includes(status) || !hasTime) {
        throw new https_1.HttpsError("failed-precondition", "Subscription is not active.");
    }
}
async function activeMemberCount(hotelId) {
    const members = await accountRef(hotelId).collection("members").where("active", "==", true).get();
    return members.size;
}
async function getOrCreateUser(email, password, displayName) {
    try {
        return { user: await auth.getUserByEmail(email), created: false };
    }
    catch (error) {
        const typed = error;
        if (typed.code !== "auth/user-not-found")
            throw error;
    }
    if (password.length < 8) {
        throw new https_1.HttpsError("invalid-argument", "Password must be at least 8 characters.");
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
exports.bootstrapHotelOwner = (0, https_1.onCall)({ invoker: "public" }, async (request) => {
    const requestAuth = await requireAuth(request);
    const hotelId = hotelIdForOwner(requestAuth.uid);
    const email = normalizeEmail(requestAuth.token.email);
    const displayName = String(requestAuth.token.name || "").trim();
    const now = firestore_1.FieldValue.serverTimestamp();
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
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
        }, { merge: true });
    });
    const user = await auth.getUser(requestAuth.uid);
    await setAppClaims(user, hotelId, ROLE_OWNER);
    v2_1.logger.info("Hotel owner bootstrapped", { hotelId, uid: requestAuth.uid });
    return { hotelId, role: ROLE_OWNER, status: STATUS_TRIALING, accessUntilMillis: accessUntil.toMillis() };
});
exports.createHotelUser = (0, https_1.onCall)({ invoker: "public" }, async (request) => {
    const requestAuth = await requireAuth(request);
    const hotelId = requireString(request.data?.hotelId || requestAuth.token.hotelId, "hotelId");
    await requireOwnerOrManager(requestAuth, hotelId);
    await requireUsableSubscription(hotelId);
    const account = await accountRef(hotelId).get();
    const maxUsers = optionalPositiveInt(account.get("maxUsers"), DEFAULT_MAX_USERS);
    const currentMembers = await activeMemberCount(hotelId);
    if (currentMembers >= maxUsers) {
        throw new https_1.HttpsError("resource-exhausted", `This plan allows only ${maxUsers} active users.`);
    }
    const email = normalizeEmail(request.data?.email);
    if (!email || !email.includes("@")) {
        throw new https_1.HttpsError("invalid-argument", "Enter a valid email.");
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
        createdAt: firestore_1.FieldValue.serverTimestamp(),
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
    }, { merge: true });
    v2_1.logger.info("Hotel user added", { hotelId, uid: user.uid, role, created });
    return { uid: user.uid, email, role, created };
});
exports.setHotelUserActive = (0, https_1.onCall)({ invoker: "public" }, async (request) => {
    const requestAuth = await requireAuth(request);
    const hotelId = requireString(request.data?.hotelId || requestAuth.token.hotelId, "hotelId");
    await requireOwnerOrManager(requestAuth, hotelId);
    const uid = requireString(request.data?.uid, "uid");
    if (uid === requestAuth.uid) {
        throw new https_1.HttpsError("failed-precondition", "You cannot deactivate yourself.");
    }
    const active = request.data?.active === true;
    await memberRef(hotelId, uid).set({
        active,
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
        updatedByUid: requestAuth.uid,
    }, { merge: true });
    await auth.updateUser(uid, { disabled: !active });
    v2_1.logger.info("Hotel user active flag changed", { hotelId, uid, active });
    return { uid, active };
});
exports.setHotelSubscription = (0, https_1.onCall)({ invoker: "public" }, async (request) => {
    const requestAuth = await requireAuth(request);
    if (!isPlatformAdmin(requestAuth)) {
        throw new https_1.HttpsError("permission-denied", "Only platform admin can update subscriptions.");
    }
    const hotelId = requireString(request.data?.hotelId, "hotelId");
    const status = parseStatus(request.data?.status);
    const maxUsers = optionalPositiveInt(request.data?.maxUsers, DEFAULT_MAX_USERS);
    const planId = String(request.data?.planId || DEFAULT_PLAN_ID).trim();
    const accessUntilMillis = Number(request.data?.accessUntilMillis || 0);
    const accessUntil = Number.isFinite(accessUntilMillis) && accessUntilMillis > 0 ?
        firestore_1.Timestamp.fromMillis(accessUntilMillis) :
        nowPlusDays(status === STATUS_ACTIVE ? 31 : DEFAULT_TRIAL_DAYS);
    await accountRef(hotelId).set({
        planId,
        status,
        maxUsers,
        accessUntil,
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
        updatedByUid: requestAuth.uid,
    }, { merge: true });
    v2_1.logger.info("Subscription updated", { hotelId, status, maxUsers, planId });
    return { hotelId, status, maxUsers, planId, accessUntilMillis: accessUntil.toMillis() };
});
exports.getMyHotelAccess = (0, https_1.onCall)({ invoker: "public" }, async (request) => {
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
    const role = String(member.get("role") || ROLE_STAFF).toUpperCase();
    const user = await auth.getUser(requestAuth.uid);
    await setAppClaims(user, hotelId, role);
    const accessUntil = account.get("accessUntil");
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
exports.listHotelAccounts = (0, https_1.onCall)({ invoker: "public" }, async (request) => {
    const requestAuth = await requireAuth(request);
    if (!isPlatformAdmin(requestAuth)) {
        throw new https_1.HttpsError("permission-denied", "Only platform admin can view hotel accounts.");
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
        const accessUntil = data.accessUntil;
        const trialEndsAt = data.trialEndsAt;
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
function timestampMillis(value) {
    if (value instanceof firestore_1.Timestamp)
        return value.toMillis();
    if (typeof value === "number")
        return value;
    return 0;
}
async function requireActiveHotelMember(requestAuth, hotelId) {
    const memberSnap = await memberRef(hotelId, requestAuth.uid).get();
    if (!memberSnap.exists || memberSnap.get("active") !== true) {
        throw new https_1.HttpsError("permission-denied", "You are not active in this hotel account.");
    }
    const role = String(memberSnap.get("role") || ROLE_STAFF).toUpperCase();
    if (![ROLE_OWNER, ROLE_MANAGER, ROLE_STAFF].includes(role)) {
        throw new https_1.HttpsError("permission-denied", "Your hotel role is not valid.");
    }
    return role;
}
exports.importExistingHotels = (0, https_1.onCall)({ invoker: "public" }, async (request) => {
    const requestAuth = await requireAuth(request);
    if (!isPlatformAdmin(requestAuth)) {
        throw new https_1.HttpsError("permission-denied", "Only platform admin can import existing hotels.");
    }
    const hotels = await db.collection("hotels").limit(500).get();
    const now = firestore_1.FieldValue.serverTimestamp();
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
        }
        catch (error) {
            v2_1.logger.warn("Existing hotel owner not found in Auth", { hotelId, ownerUid, error });
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
        }
        else {
            createdAccounts += 1;
        }
    }
    v2_1.logger.info("Existing hotels imported", { createdAccounts, updatedAccounts, skippedHotels });
    return { scannedHotels: hotels.size, createdAccounts, updatedAccounts, skippedHotels };
});
function uidFromHotelId(hotelId) {
    return hotelId.startsWith("hotel_") ? hotelId.substring("hotel_".length) : "";
}
function numberValue(value, fallback = 0) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
}
function optionalNumberValue(value) {
    if (value === null || value === undefined || value === "")
        return null;
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
}
function booleanValue(value, fallback = false) {
    if (typeof value === "boolean")
        return value;
    if (typeof value === "number")
        return value !== 0;
    if (typeof value === "string") {
        const clean = value.trim().toLowerCase();
        if (["true", "1", "yes", "y"].includes(clean))
            return true;
        if (["false", "0", "no", "n"].includes(clean))
            return false;
    }
    return fallback;
}
function stringList(value) {
    if (!Array.isArray(value))
        return [];
    return value.map((item) => String(item || "").trim()).filter(Boolean);
}
function startOfDay(millis) {
    const date = new Date(millis);
    date.setHours(0, 0, 0, 0);
    return date.getTime();
}
function lockIdsFor(roomRemoteIds, checkInMillis, checkOutMillis) {
    const start = startOfDay(checkInMillis);
    const end = startOfDay(checkOutMillis);
    const lockIds = new Set();
    if (end <= start)
        return lockIds;
    for (const roomId of roomRemoteIds) {
        for (let day = start; day < end; day += DAY_MILLIS) {
            lockIds.add(`${roomId}_${day}`);
        }
    }
    return lockIds;
}
function normaliseFoodBillPayload(raw, hotelId, uid) {
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
            serverUpdatedAt: firestore_1.FieldValue.serverTimestamp(),
        },
    };
}
function normaliseFoodBillItemPayload(raw, hotelId, billRemoteId, uid) {
    const remoteId = requireString(raw.remoteId, "food bill item remoteId");
    const payloadBillRemoteId = requireString(raw.billRemoteId, "food bill item billRemoteId");
    if (payloadBillRemoteId !== billRemoteId) {
        throw new https_1.HttpsError("invalid-argument", "Food bill item belongs to another bill.");
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
            serverUpdatedAt: firestore_1.FieldValue.serverTimestamp(),
        },
    };
}
function normaliseFoodOrderPayload(raw, hotelId, billRemoteId, uid) {
    const remoteId = requireString(raw.remoteId, "food order remoteId");
    const payloadBillRemoteId = raw.billRemoteId ? String(raw.billRemoteId) : null;
    const payloadLinkedFinalBillId = raw.linkedFinalBillId ? String(raw.linkedFinalBillId) : null;
    if (payloadBillRemoteId && payloadBillRemoteId !== billRemoteId) {
        throw new https_1.HttpsError("invalid-argument", "Food order belongs to another bill.");
    }
    if (payloadLinkedFinalBillId && payloadLinkedFinalBillId !== billRemoteId) {
        throw new https_1.HttpsError("invalid-argument", "Food order is linked to another final bill.");
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
            serverUpdatedAt: firestore_1.FieldValue.serverTimestamp(),
        },
    };
}
function normaliseFoodOrderItemPayload(raw, hotelId, uid) {
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
            serverUpdatedAt: firestore_1.FieldValue.serverTimestamp(),
        },
    };
}
function normaliseBookingAccountingChargePayload(raw, hotelId, billRemoteId, uid) {
    const remoteId = requireString(raw.remoteId, "accounting charge remoteId");
    const linkedFinalBillId = raw.linkedFinalBillId ? String(raw.linkedFinalBillId) : null;
    if (linkedFinalBillId && linkedFinalBillId !== billRemoteId) {
        throw new https_1.HttpsError("invalid-argument", "Accounting charge is linked to another bill.");
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
            serverUpdatedAt: firestore_1.FieldValue.serverTimestamp(),
        },
    };
}
function normaliseRoomGstSlabPayload(raw, hotelId, uid) {
    const remoteId = requireString(raw.remoteId, "room GST slab remoteId");
    const gstRatePercent = numberValue(raw.gstRatePercent);
    const cgstRatePercent = numberValue(raw.cgstRatePercent);
    const sgstRatePercent = numberValue(raw.sgstRatePercent);
    const cessRatePercent = numberValue(raw.cessRatePercent);
    if (Math.abs((cgstRatePercent + sgstRatePercent + cessRatePercent) - gstRatePercent) > 0.001) {
        throw new https_1.HttpsError("invalid-argument", "CGST + SGST + cess must equal total GST.");
    }
    const minGrossAmount = numberValue(raw.minGrossAmount);
    const maxGrossAmount = raw.maxGrossAmount === null || raw.maxGrossAmount === undefined
        ? null
        : numberValue(raw.maxGrossAmount);
    if (minGrossAmount < 0 || (maxGrossAmount !== null && maxGrossAmount < minGrossAmount)) {
        throw new https_1.HttpsError("invalid-argument", "Invalid room GST tariff range.");
    }
    return {
        remoteId,
        baseRevision: numberValue(raw.baseRevision),
        cloudData: {
            hotelRemoteId: hotelId,
            slabName: requireString(raw.slabName, "room GST slab name"),
            minGrossAmount,
            maxGrossAmount,
            gstRatePercent,
            cgstRatePercent,
            sgstRatePercent,
            cessRatePercent,
            hsnSacCode: String(raw.hsnSacCode || "996311"),
            notificationRef: raw.notificationRef ? String(raw.notificationRef) : null,
            effectiveFromMillis: numberValue(raw.effectiveFromMillis),
            effectiveToMillis: raw.effectiveToMillis === null || raw.effectiveToMillis === undefined
                ? null
                : numberValue(raw.effectiveToMillis),
            isActive: booleanValue(raw.isActive, true),
            updatedAt: numberValue(raw.updatedAt, Date.now()),
            isDeleted: booleanValue(raw.isDeleted),
            updatedByUid: uid,
            serverUpdatedAt: firestore_1.FieldValue.serverTimestamp(),
        },
    };
}

function normaliseBookingPaymentPayload(raw, hotelId, uid) {
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
            serverUpdatedAt: firestore_1.FieldValue.serverTimestamp(),
        },
    };
}
async function saveRevisionCheckedRecord(request, collectionName, mutationCollectionName, entityLabel, normalise, validateInTransaction) {
    const requestAuth = await requireAuth(request);
    const hotelId = requireString(request.data?.hotelId || requestAuth.token.hotelId, "hotelId");
    await requireActiveHotelMember(requestAuth, hotelId);
    await requireUsableSubscription(hotelId);
    const operationId = requireString(request.data?.operationId, "operationId");
    const entity = normalise((request.data?.entity || {}), hotelId, requestAuth.uid);
    const hotelRef = publicHotelRef(hotelId);
    const entityDoc = hotelRef.collection(collectionName).doc(entity.remoteId);
    const mutationDoc = hotelRef.collection(mutationCollectionName).doc(operationId);
    return db.runTransaction(async (tx) => {
        const alreadyApplied = await tx.get(mutationDoc);
        if (alreadyApplied.exists) {
            if (String(alreadyApplied.get("remoteId") || "") !== entity.remoteId) {
                throw new https_1.HttpsError("aborted", `This ${entityLabel} operation ID was already used.`);
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
            throw new https_1.HttpsError("aborted", `This ${entityLabel} changed on another device. Refresh before syncing.`);
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
            serverCreatedAt: firestore_1.FieldValue.serverTimestamp(),
        });
        return { revision, updatedByUid: requestAuth.uid, alreadyApplied: false };
    });
}

function paymentStayAmount(data) {
    const explicit = numberValue(data.allocatedStayAmount);
    const allocatedTotal = explicit + numberValue(data.allocatedFoodAmount) +
        numberValue(data.allocatedServiceAmount) + numberValue(data.allocatedDamageAmount) +
        numberValue(data.unappliedAmount);
    if (allocatedTotal > 0.001)
        return explicit;
    const category = String(data.paymentCategory || "AUTO");
    return ["AUTO", "STAY"].includes(category) ? numberValue(data.amount) : 0;
}
async function updateBookingPaymentSummaryInTransaction(tx, hotelRef, bookingRemoteId, pendingPayment = null, pendingCharge = null) {
    const bookingDoc = hotelRef.collection("bookings").doc(bookingRemoteId);
    const [booking, payments, charges] = await Promise.all([
        tx.get(bookingDoc),
        tx.get(hotelRef.collection("bookingPayments").where("bookingRemoteId", "==", bookingRemoteId)),
        tx.get(hotelRef.collection("bookingAccountingCharges").where("bookingRemoteId", "==", bookingRemoteId)),
    ]);
    if (!booking.exists || booleanValue(booking.get("isDeleted")))
        return;
    const paymentRecords = payments.docs
        .filter((doc) => !booleanValue(doc.get("isDeleted")) && doc.id !== pendingPayment?.remoteId)
        .map((doc) => ({ id: doc.id, ...doc.data() }));
    if (pendingPayment && !booleanValue(pendingPayment.cloudData.isDeleted)) {
        paymentRecords.push({ id: pendingPayment.remoteId, ...pendingPayment.cloudData });
    }
    const chargeRecords = charges.docs
        .filter((doc) => !booleanValue(doc.get("isDeleted")) && doc.id !== pendingCharge?.remoteId)
        .map((doc) => ({ id: doc.id, ...doc.data() }));
    if (pendingCharge && !booleanValue(pendingCharge.cloudData.isDeleted)) {
        chargeRecords.push({ id: pendingCharge.remoteId, ...pendingCharge.cloudData });
    }
    const stayPaid = paymentRecords.reduce((sum, payment) => {
        const sign = ["REFUND", "ADJUSTMENT"].includes(String(payment.paymentType || "")) ? -1 : 1;
        return sum + sign * paymentStayAmount(payment);
    }, 0);
    const stayDiscount = chargeRecords.reduce((sum, charge) => {
        const isDiscount = String(charge.chargeType || "") === "DISCOUNT";
        const bucket = String(charge.accountBucket || "");
        return sum + (isDiscount && bucket === "STAY" ? numberValue(charge.amount) : 0);
    }, 0);
    const originalStayTotal = Math.max(0, numberValue(booking.get("grossCharges"),
        numberValue(booking.get("receivable"), numberValue(booking.get("rate")))));
    const stayTotal = Math.max(0, Math.round((originalStayTotal - stayDiscount) * 100) / 100);
    const paid = Math.max(0, Math.round(stayPaid * 100) / 100);
    const balance = Math.max(0, Math.round((stayTotal - paid) * 100) / 100);
    const currentStatus = String(booking.get("paymentStatus") || "");
    const sourceType = String(booking.get("sourceType") || "DIRECT");
    const paymentStatus = currentStatus === "COMPLIMENTARY" ? "COMPLIMENTARY" :
        sourceType === "OTA" ? "FULLY_PAID" :
            stayTotal > 0 && paid >= stayTotal - 0.001 ? "FULLY_PAID" :
                paid > 0 ? "PARTIALLY_PAID" : "NOT_PAID";
    tx.set(bookingDoc, {
        paid,
        advancePaid: paid,
        balance,
        paymentStatus,
        derivedFinancialUpdatedAt: Date.now(),
        serverUpdatedAt: firestore_1.FieldValue.serverTimestamp(),
    }, { merge: true });
}
exports.saveRoomGstSlabServer = (0, https_1.onCall)({ invoker: "public" }, async (request) => saveRevisionCheckedRecord(
    request,
    "roomGstSlabs",
    "appliedRoomGstSlabMutations",
    "room GST slab",
    normaliseRoomGstSlabPayload
));
exports.saveBookingPaymentServer = (0, https_1.onCall)({ invoker: "public" }, async (request) => saveRevisionCheckedRecord(request, "bookingPayments", "appliedPaymentMutations", "payment", normaliseBookingPaymentPayload, async (tx, hotelRef, entity) => {
    const allocationFields = [
        "allocatedStayAmount",
        "allocatedFoodAmount",
        "allocatedServiceAmount",
        "allocatedDamageAmount",
        "unappliedAmount",
    ];
    if (allocationFields.some((field) => numberValue(entity.cloudData[field]) < 0)) {
        throw new https_1.HttpsError("invalid-argument", "Payment allocations cannot be negative.");
    }
    const allocationTotal = allocationFields.reduce((sum, field) => sum + numberValue(entity.cloudData[field]), 0);
    if (Math.abs(allocationTotal - numberValue(entity.cloudData.amount)) > 0.02) {
        throw new https_1.HttpsError("invalid-argument", "Payment allocations must equal the payment amount.");
    }
    if (String(entity.cloudData.paymentType || "") !== "REFUND") {
        await updateBookingPaymentSummaryInTransaction(
            tx,
            hotelRef,
            String(entity.cloudData.bookingRemoteId || ""),
            entity,
            null
        );
        return;
    }
    const originalPaymentRemoteId = requireString(entity.cloudData.originalPaymentRemoteId, "original payment");
    const original = await tx.get(hotelRef.collection("bookingPayments").doc(originalPaymentRemoteId));
    if (!original.exists || booleanValue(original.get("isDeleted"))) {
        throw new https_1.HttpsError("failed-precondition", "Original payment was not found.");
    }
    if (String(original.get("bookingRemoteId") || "") !== String(entity.cloudData.bookingRemoteId || "")) {
        throw new https_1.HttpsError("invalid-argument", "Original payment belongs to another booking.");
    }
    if (!["PAYMENT", "ADVANCE"].includes(String(original.get("paymentType") || ""))) {
        throw new https_1.HttpsError("failed-precondition", "Only a payment or advance can be refunded.");
    }
    const priorRefunds = await tx.get(hotelRef.collection("bookingPayments")
        .where("originalPaymentRemoteId", "==", originalPaymentRemoteId));
    const refundedAmount = priorRefunds.docs
        .filter((doc) => doc.id !== entity.remoteId && !booleanValue(doc.get("isDeleted")))
        .reduce((sum, doc) => sum + numberValue(doc.get("amount")), 0);
    if (numberValue(entity.cloudData.amount) > numberValue(original.get("amount")) - refundedAmount + 0.001) {
        throw new https_1.HttpsError("failed-precondition", "Refund exceeds the remaining refundable amount.");
    }
    const ratio = numberValue(entity.cloudData.amount) / numberValue(original.get("amount"));
    entity.cloudData.paymentCategory = String(original.get("paymentCategory") || "AUTO");
    entity.cloudData.allocatedStayAmount = numberValue(original.get("allocatedStayAmount")) * ratio;
    entity.cloudData.allocatedFoodAmount = numberValue(original.get("allocatedFoodAmount")) * ratio;
    entity.cloudData.allocatedServiceAmount = numberValue(original.get("allocatedServiceAmount")) * ratio;
    entity.cloudData.allocatedDamageAmount = numberValue(original.get("allocatedDamageAmount")) * ratio;
    entity.cloudData.unappliedAmount = numberValue(original.get("unappliedAmount")) * ratio;
    await updateBookingPaymentSummaryInTransaction(
        tx,
        hotelRef,
        String(entity.cloudData.bookingRemoteId || ""),
        entity,
        null
    );
}));
exports.saveBookingAccountingChargeServer = (0, https_1.onCall)({ invoker: "public" }, async (request) => saveRevisionCheckedRecord(
    request,
    "bookingAccountingCharges",
    "appliedAccountingChargeMutations",
    "service or damage charge",
    (raw, hotelId, uid) => normaliseBookingAccountingChargePayload(raw, hotelId, null, uid),
    async (tx, hotelRef, entity) => {
        await updateBookingPaymentSummaryInTransaction(
            tx,
            hotelRef,
            String(entity.cloudData.bookingRemoteId || ""),
            null,
            entity
        );
    }
));
exports.saveFoodOrderAggregateServer = (0, https_1.onCall)({ invoker: "public" }, async (request) => {
    const requestAuth = await requireAuth(request);
    const hotelId = requireString(request.data?.hotelId || requestAuth.token.hotelId, "hotelId");
    await requireActiveHotelMember(requestAuth, hotelId);
    await requireUsableSubscription(hotelId);
    const operationId = requireString(request.data?.operationId, "operationId");
    const order = normaliseFoodOrderPayload((request.data?.order || {}), hotelId, null, requestAuth.uid);
    const rawItems = Array.isArray(request.data?.orderItems)
        ? request.data.orderItems
        : [];
    const orderItems = rawItems.map((item) => normaliseFoodOrderItemPayload(item, hotelId, requestAuth.uid));
    for (const item of orderItems) {
        if (String(item.cloudData.orderRemoteId || "") !== order.remoteId) {
            throw new https_1.HttpsError("invalid-argument", "Food order item belongs to another order.");
        }
    }
    if (orderItems.length > 420) {
        throw new https_1.HttpsError("invalid-argument", "Food order has too many items for one safe sync operation.");
    }
    const hotelRef = publicHotelRef(hotelId);
    const orderDoc = hotelRef.collection("foodOrders").doc(order.remoteId);
    const mutationDoc = hotelRef.collection("appliedFoodOrderMutations").doc(operationId);
    return db.runTransaction(async (tx) => {
        const alreadyApplied = await tx.get(mutationDoc);
        if (alreadyApplied.exists) {
            if (String(alreadyApplied.get("orderRemoteId") || "") !== order.remoteId) {
                throw new https_1.HttpsError("aborted", "This food order operation ID was already used.");
            }
            return {
                orderRevision: numberValue(alreadyApplied.get("orderRevision")),
                orderItemRevisions: alreadyApplied.get("orderItemRevisions") || {},
                updatedByUid: String(alreadyApplied.get("updatedByUid") || requestAuth.uid),
                alreadyApplied: true,
            };
        }
        const existingOrder = await tx.get(orderDoc);
        const remoteOrderRevision = numberValue(existingOrder.get("revision"));
        if (existingOrder.exists && remoteOrderRevision !== order.baseRevision) {
            throw new https_1.HttpsError("aborted", "This food order changed on another device. Refresh before syncing.");
        }
        if (existingOrder.exists &&
            !booleanValue(existingOrder.get("isDeleted")) &&
            (String(existingOrder.get("billRemoteId") || "") ||
                String(existingOrder.get("linkedFinalBillId") || ""))) {
            throw new https_1.HttpsError("failed-precondition", "This food order is already billed in cloud.");
        }
        const itemSnapshots = new Map();
        for (const item of orderItems) {
            const itemDoc = hotelRef.collection("foodOrderItems").doc(item.remoteId);
            itemSnapshots.set(item.remoteId, await tx.get(itemDoc));
        }
        for (const item of orderItems) {
            const snapshot = itemSnapshots.get(item.remoteId);
            if (snapshot?.exists && numberValue(snapshot.get("revision")) !== item.baseRevision) {
                throw new https_1.HttpsError("aborted", "A food order item changed on another device. Refresh before syncing.");
            }
        }
        const orderRevision = remoteOrderRevision + 1;
        const orderItemRevisions = {};
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
            serverCreatedAt: firestore_1.FieldValue.serverTimestamp(),
        });
        return result;
    });
});
exports.saveFoodBillAggregateServer = (0, https_1.onCall)({ invoker: "public" }, async (request) => {
    const requestAuth = await requireAuth(request);
    const hotelId = requireString(request.data?.hotelId || requestAuth.token.hotelId, "hotelId");
    await requireActiveHotelMember(requestAuth, hotelId);
    await requireUsableSubscription(hotelId);
    const operationId = requireString(request.data?.operationId, "operationId");
    const bill = normaliseFoodBillPayload((request.data?.bill || {}), hotelId, requestAuth.uid);
    const rawBillItems = Array.isArray(request.data?.billItems)
        ? request.data.billItems
        : [];
    const rawOrders = Array.isArray(request.data?.orders)
        ? request.data.orders
        : [];
    const rawOrderItems = Array.isArray(request.data?.orderItems)
        ? request.data.orderItems
        : [];
    const rawAccountingCharges = Array.isArray(request.data?.accountingCharges)
        ? request.data.accountingCharges
        : [];
    const billItems = rawBillItems.map((item) => normaliseFoodBillItemPayload(item, hotelId, bill.remoteId, requestAuth.uid));
    const orders = rawOrders.map((order) => normaliseFoodOrderPayload(order, hotelId, bill.remoteId, requestAuth.uid));
    const orderItems = rawOrderItems.map((item) => normaliseFoodOrderItemPayload(item, hotelId, requestAuth.uid));
    const accountingCharges = rawAccountingCharges.map((charge) => normaliseBookingAccountingChargePayload(charge, hotelId, bill.remoteId, requestAuth.uid));
    if (billItems.length === 0) {
        throw new https_1.HttpsError("invalid-argument", "Bill must contain at least one item.");
    }
    const orderIds = new Set(orders.map((order) => order.remoteId));
    for (const item of orderItems) {
        const orderRemoteId = String(item.cloudData.orderRemoteId || "");
        if (!orderIds.has(orderRemoteId)) {
            throw new https_1.HttpsError("invalid-argument", "Food order item belongs to an order outside this bill aggregate.");
        }
    }
    if (billItems.length + orders.length + orderItems.length + accountingCharges.length > 420) {
        throw new https_1.HttpsError("invalid-argument", "Food bill has too many linked rows for one safe sync operation.");
    }
    const hotelRef = publicHotelRef(hotelId);
    const mutationDoc = hotelRef.collection("appliedFoodBillMutations").doc(operationId);
    const billDoc = hotelRef.collection("foodBills").doc(bill.remoteId);
    const result = await db.runTransaction(async (tx) => {
        const alreadyApplied = await tx.get(mutationDoc);
        if (alreadyApplied.exists) {
            const existingBillRemoteId = String(alreadyApplied.get("billRemoteId") || "");
            if (existingBillRemoteId !== bill.remoteId) {
                throw new https_1.HttpsError("aborted", "This food bill sync operation ID was already used for another bill.");
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
        const duplicateBillSnapshot = await tx.get(hotelRef.collection("foodBills")
            .where("billNumber", "==", bill.cloudData.billNumber)
            .limit(5));
        duplicateBillSnapshot.docs.forEach((doc) => {
            if (doc.id !== bill.remoteId && !booleanValue(doc.get("isDeleted"))) {
                throw new https_1.HttpsError("already-exists", "This bill number already exists in cloud.");
            }
        });
        const existingBill = await tx.get(billDoc);
        const remoteBillRevision = numberValue(existingBill.get("revision"));
        if (existingBill.exists && remoteBillRevision !== bill.baseRevision) {
            throw new https_1.HttpsError("aborted", "This bill was changed on another device. Refresh before syncing.");
        }
        const billItemSnapshots = new Map();
        for (const item of billItems) {
            const itemDoc = hotelRef.collection("foodBillItems").doc(item.remoteId);
            billItemSnapshots.set(item.remoteId, await tx.get(itemDoc));
        }
        const orderSnapshots = new Map();
        for (const order of orders) {
            const orderDoc = hotelRef.collection("foodOrders").doc(order.remoteId);
            orderSnapshots.set(order.remoteId, await tx.get(orderDoc));
        }
        const orderItemSnapshots = new Map();
        for (const item of orderItems) {
            const itemDoc = hotelRef.collection("foodOrderItems").doc(item.remoteId);
            orderItemSnapshots.set(item.remoteId, await tx.get(itemDoc));
        }
        const chargeSnapshots = new Map();
        for (const charge of accountingCharges) {
            const chargeDoc = hotelRef.collection("bookingAccountingCharges").doc(charge.remoteId);
            chargeSnapshots.set(charge.remoteId, await tx.get(chargeDoc));
        }
        for (const item of billItems) {
            const itemSnapshot = billItemSnapshots.get(item.remoteId);
            const itemRemoteRevision = numberValue(itemSnapshot?.get("revision"));
            if (itemSnapshot?.exists && itemRemoteRevision !== item.baseRevision) {
                throw new https_1.HttpsError("aborted", "A bill item changed on another device. Refresh before syncing.");
            }
        }
        for (const order of orders) {
            const orderSnapshot = orderSnapshots.get(order.remoteId);
            const orderRemoteRevision = numberValue(orderSnapshot?.get("revision"));
            if (orderSnapshot?.exists && orderRemoteRevision !== order.baseRevision) {
                throw new https_1.HttpsError("aborted", "A food order changed on another device. Refresh before billing.");
            }
            const existingOrderBillId = String(orderSnapshot?.get("billRemoteId") || "");
            const existingOrderFinalBillId = String(orderSnapshot?.get("linkedFinalBillId") || "");
            const existingOrderDeleted = booleanValue(orderSnapshot?.get("isDeleted"));
            if (orderSnapshot?.exists &&
                !existingOrderDeleted &&
                ((existingOrderBillId && existingOrderBillId !== bill.remoteId) ||
                    (existingOrderFinalBillId && existingOrderFinalBillId !== bill.remoteId))) {
                throw new https_1.HttpsError("already-exists", "One selected food order is already billed in cloud.");
            }
        }
        for (const item of orderItems) {
            const itemSnapshot = orderItemSnapshots.get(item.remoteId);
            const itemRemoteRevision = numberValue(itemSnapshot?.get("revision"));
            if (itemSnapshot?.exists && itemRemoteRevision !== item.baseRevision) {
                throw new https_1.HttpsError("aborted", "A food order item changed on another device. Refresh before billing.");
            }
        }
        for (const charge of accountingCharges) {
            const chargeSnapshot = chargeSnapshots.get(charge.remoteId);
            const chargeRemoteRevision = numberValue(chargeSnapshot?.get("revision"));
            if (chargeSnapshot?.exists && chargeRemoteRevision !== charge.baseRevision) {
                throw new https_1.HttpsError("aborted", "A service or damage charge changed on another device. Refresh before billing.");
            }
            const existingLinkedBillId = String(chargeSnapshot?.get("linkedFinalBillId") || "");
            const existingChargeDeleted = booleanValue(chargeSnapshot?.get("isDeleted"));
            if (chargeSnapshot?.exists && !existingChargeDeleted && existingLinkedBillId && existingLinkedBillId !== bill.remoteId) {
                throw new https_1.HttpsError("already-exists", "A service or damage charge is already linked to another bill.");
            }
        }
        const billRevision = remoteBillRevision + 1;
        const foodBillItemRevisions = {};
        const foodOrderRevisions = {};
        const foodOrderItemRevisions = {};
        const accountingChargeRevisions = {};
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
            serverCreatedAt: firestore_1.FieldValue.serverTimestamp(),
        });
        return successResult;
    });
    return result;
});

function normaliseBookingAggregatePayload(raw, hotelId, uid) {
    const remoteId = requireString(raw.remoteId, "booking remoteId");
    const roomRemoteIds = stringList(raw.roomRemoteIds);
    if (roomRemoteIds.length === 0)
        throw new https_1.HttpsError("invalid-argument", "Select at least one room.");
    const checkInMillis = numberValue(raw.checkInMillis);
    const checkOutMillis = numberValue(raw.checkOutMillis);
    if (checkInMillis <= 0 || checkOutMillis <= checkInMillis)
        throw new https_1.HttpsError("invalid-argument", "Check-out must be after check-in.");
    return {
        remoteId,
        baseRevision: numberValue(raw.baseRevision),
        roomRemoteIds,
        checkInMillis,
        checkOutMillis,
        cloudData: {
            ...raw,
            remoteId: undefined,
            baseRevision: undefined,
            hotelRemoteId: hotelId,
            roomRemoteIds,
            checkInMillis,
            checkOutMillis,
            bookingStatus: String(raw.bookingStatus || "RESERVED"),
            isDeleted: booleanValue(raw.isDeleted),
            updatedAt: numberValue(raw.updatedAt, Date.now()),
            updatedByUid: uid,
            serverUpdatedAt: firestore_1.FieldValue.serverTimestamp(),
        },
    };
}
function normaliseBookingFinancialLinePayload(raw, hotelId, bookingRemoteId, uid) {
    const remoteId = requireString(raw.remoteId, "room-night line remoteId");
    const payloadBookingId = requireString(raw.bookingRemoteId, "room-night bookingRemoteId");
    if (payloadBookingId !== bookingRemoteId)
        throw new https_1.HttpsError("invalid-argument", "Room-night line belongs to another booking.");
    return {
        remoteId,
        baseRevision: numberValue(raw.baseRevision),
        cloudData: {
            ...raw,
            remoteId: undefined,
            baseRevision: undefined,
            hotelRemoteId: hotelId,
            bookingRemoteId,
            updatedAt: numberValue(raw.updatedAt, Date.now()),
            isDeleted: booleanValue(raw.isDeleted),
            updatedByUid: uid,
            serverUpdatedAt: firestore_1.FieldValue.serverTimestamp(),
        },
    };
}
exports.saveBookingAggregateServer = (0, https_1.onCall)({ invoker: "public" }, async (request) => {
    const requestAuth = await requireAuth(request);
    const hotelId = requireString(request.data?.hotelId || requestAuth.token.hotelId, "hotelId");
    await requireActiveHotelMember(requestAuth, hotelId);
    await requireUsableSubscription(hotelId);
    const operationId = requireString(request.data?.operationId, "operationId");
    const booking = normaliseBookingAggregatePayload(request.data?.booking || {}, hotelId, requestAuth.uid);
    const lines = (Array.isArray(request.data?.financialLines) ? request.data.financialLines : [])
        .map((line) => normaliseBookingFinancialLinePayload(line, hotelId, booking.remoteId, requestAuth.uid));
    const activeLines = lines.filter((line) => !booleanValue(line.cloudData.isDeleted));
    const expectedKeys = new Set();
    for (let day = booking.checkInMillis; day < booking.checkOutMillis; day += DAY_MILLIS) {
        for (const roomId of booking.roomRemoteIds)
            expectedKeys.add(`${roomId}|${day}`);
    }
    const actualKeys = new Set(activeLines.map((line) => `${line.cloudData.roomRemoteId}|${numberValue(line.cloudData.businessDateMillis)}`));
    if (expectedKeys.size !== actualKeys.size || Array.from(expectedKeys).some((key) => !actualKeys.has(key))) {
        throw new https_1.HttpsError("invalid-argument", "Room-night accounting does not match the selected rooms and dates.");
    }
    const hotelRef = publicHotelRef(hotelId);
    const bookingDoc = hotelRef.collection("bookings").doc(booking.remoteId);
    const mutationDoc = hotelRef.collection("appliedBookingAggregates").doc(operationId);
    return db.runTransaction(async (tx) => {
        const applied = await tx.get(mutationDoc);
        if (applied.exists) {
            if (String(applied.get("bookingRemoteId") || "") !== booking.remoteId)
                throw new https_1.HttpsError("aborted", "This booking operation ID was already used.");
            return {
                operationId,
                bookingRemoteId: booking.remoteId,
                bookingRevision: numberValue(applied.get("bookingRevision")),
                financialLineRevisions: applied.get("financialLineRevisions") || {},
                updatedByUid: String(applied.get("updatedByUid") || requestAuth.uid),
                alreadyApplied: true,
            };
        }
        const current = await tx.get(bookingDoc);
        const remoteRevision = numberValue(current.get("revision"));
        if (current.exists && remoteRevision !== booking.baseRevision)
            throw new https_1.HttpsError("aborted", "This booking changed on another device. Refresh before saving.");
        if (!current.exists && booking.baseRevision !== 0)
            throw new https_1.HttpsError("aborted", "This booking no longer exists in cloud.");
        const newLockIds = new Set();
        for (let day = booking.checkInMillis; day < booking.checkOutMillis; day += DAY_MILLIS)
            for (const roomId of booking.roomRemoteIds)
                newLockIds.add(`${roomId}_${day}`);
        if (newLockIds.size + lines.length > 450)
            throw new https_1.HttpsError("invalid-argument", "This booking is too large to save safely in one operation.");
        const lockSnapshots = new Map();
        for (const lockId of newLockIds)
            lockSnapshots.set(lockId, await tx.get(hotelRef.collection("bookingLocks").doc(lockId)));
        const blockerIds = new Set(Array.from(lockSnapshots.values())
            .filter((snap) => snap.exists && !booleanValue(snap.get("isDeleted")))
            .map((snap) => String(snap.get("bookingRemoteId") || ""))
            .filter((id) => id && id !== booking.remoteId));
        const blockerBookings = new Map();
        for (const id of blockerIds)
            blockerBookings.set(id, await tx.get(hotelRef.collection("bookings").doc(id)));
        for (const snap of lockSnapshots.values()) {
            const lockedBy = String(snap.get("bookingRemoteId") || "");
            if (!lockedBy || lockedBy === booking.remoteId)
                continue;
            const blocker = blockerBookings.get(lockedBy);
            if (blocker?.exists && !booleanValue(blocker.get("isDeleted")) && String(blocker.get("bookingStatus") || "") !== "CANCELLED")
                throw new https_1.HttpsError("already-exists", "A selected room is already booked for these dates.");
        }
        const lineSnapshots = new Map();
        for (const line of lines)
            lineSnapshots.set(line.remoteId, await tx.get(hotelRef.collection("bookingFinancialLines").doc(line.remoteId)));
        for (const line of lines) {
            const snap = lineSnapshots.get(line.remoteId);
            const revision = numberValue(snap?.get("revision"));
            if (snap?.exists && revision !== line.baseRevision)
                throw new https_1.HttpsError("aborted", "Room-night accounting changed on another device. Refresh before saving.");
        }
        const oldRoomIds = current.exists ? stringList(current.get("roomRemoteIds")) : [];
        const oldCheckIn = current.exists ? numberValue(current.get("checkInMillis"), booking.checkInMillis) : booking.checkInMillis;
        const oldCheckOut = current.exists ? numberValue(current.get("checkOutMillis"), booking.checkOutMillis) : booking.checkOutMillis;
        const oldLockIds = new Set();
        for (let day = oldCheckIn; day < oldCheckOut; day += DAY_MILLIS)
            for (const roomId of oldRoomIds)
                oldLockIds.add(`${roomId}_${day}`);
        const bookingRevision = remoteRevision + 1;
        for (const lockId of oldLockIds)
            if (!newLockIds.has(lockId))
                tx.delete(hotelRef.collection("bookingLocks").doc(lockId));
        const cleanBookingData = { ...booking.cloudData };
        delete cleanBookingData.remoteId;
        delete cleanBookingData.baseRevision;
        tx.set(bookingDoc, { ...cleanBookingData, revision: bookingRevision });
        for (const lockId of newLockIds) {
            const separator = lockId.lastIndexOf("_");
            const roomRemoteId = lockId.substring(0, separator);
            const dateMillis = numberValue(lockId.substring(separator + 1));
            tx.set(hotelRef.collection("bookingLocks").doc(lockId), {
                hotelRemoteId: hotelId,
                bookingRemoteId: booking.remoteId,
                roomRemoteId,
                dateMillis,
                isDeleted: false,
                updatedByUid: requestAuth.uid,
                serverUpdatedAt: firestore_1.FieldValue.serverTimestamp(),
            });
        }
        const financialLineRevisions = {};
        for (const line of lines) {
            const snap = lineSnapshots.get(line.remoteId);
            const revision = numberValue(snap?.get("revision")) + 1;
            const cleanLineData = { ...line.cloudData };
            delete cleanLineData.remoteId;
            delete cleanLineData.baseRevision;
            financialLineRevisions[line.remoteId] = revision;
            tx.set(hotelRef.collection("bookingFinancialLines").doc(line.remoteId), { ...cleanLineData, revision });
        }
        const result = {
            operationId,
            bookingRemoteId: booking.remoteId,
            bookingRevision,
            financialLineRevisions,
            updatedByUid: requestAuth.uid,
            alreadyApplied: false,
        };
        tx.set(mutationDoc, { ...result, hotelRemoteId: hotelId, createdAt: firestore_1.FieldValue.serverTimestamp() });
        return result;
    });
});
exports.cancelBookingServer = (0, https_1.onCall)({ invoker: "public" }, async (request) => {
    const requestAuth = await requireAuth(request);
    const hotelId = requireString(request.data?.hotelId || requestAuth.token.hotelId, "hotelId");
    await requireActiveHotelMember(requestAuth, hotelId);
    await requireUsableSubscription(hotelId);
    const operationId = requireString(request.data?.operationId, "operationId");
    const bookingRemoteId = requireString(request.data?.bookingRemoteId, "bookingRemoteId");
    const baseRevision = numberValue(request.data?.baseRevision);
    const hotelRef = publicHotelRef(hotelId);
    const bookingDoc = hotelRef.collection("bookings").doc(bookingRemoteId);
    const mutationDoc = hotelRef.collection("appliedBookingCancellations").doc(operationId);
    return db.runTransaction(async (tx) => {
        const applied = await tx.get(mutationDoc);
        if (applied.exists) {
            if (String(applied.get("bookingRemoteId") || "") !== bookingRemoteId)
                throw new https_1.HttpsError("aborted", "This cancellation operation ID was already used.");
            return {
                revision: numberValue(applied.get("revision")),
                updatedByUid: String(applied.get("updatedByUid") || requestAuth.uid),
                alreadyApplied: true,
            };
        }
        const booking = await tx.get(bookingDoc);
        if (!booking.exists) {
            tx.set(mutationDoc, {
                bookingRemoteId,
                revision: 0,
                updatedByUid: requestAuth.uid,
                createdAt: firestore_1.FieldValue.serverTimestamp(),
            });
            return { revision: 0, updatedByUid: requestAuth.uid, alreadyApplied: false };
        }
        const remoteRevision = numberValue(booking.get("revision"));
        const alreadyCancelled = booleanValue(booking.get("isDeleted")) || String(booking.get("bookingStatus") || "") === "CANCELLED";
        if (!alreadyCancelled && remoteRevision !== baseRevision)
            throw new https_1.HttpsError("aborted", "This booking changed on another device. Refresh before cancelling.");
        const roomIds = stringList(booking.get("roomRemoteIds"));
        const checkInMillis = numberValue(booking.get("checkInMillis"));
        const checkOutMillis = numberValue(booking.get("checkOutMillis"));
        const lockIds = [];
        for (let day = checkInMillis; day < checkOutMillis; day += DAY_MILLIS)
            for (const roomId of roomIds)
                lockIds.push(`${roomId}_${day}`);
        for (const lockId of lockIds)
            await tx.get(hotelRef.collection("bookingLocks").doc(lockId));
        const revision = alreadyCancelled ? remoteRevision : remoteRevision + 1;
        for (const lockId of lockIds)
            tx.delete(hotelRef.collection("bookingLocks").doc(lockId));
        tx.set(bookingDoc, {
            bookingStatus: "CANCELLED",
            isDeleted: true,
            cancelledAt: Date.now(),
            updatedByUid: requestAuth.uid,
            revision,
            serverUpdatedAt: firestore_1.FieldValue.serverTimestamp(),
        }, { merge: true });
        tx.set(mutationDoc, {
            bookingRemoteId,
            revision,
            updatedByUid: requestAuth.uid,
            createdAt: firestore_1.FieldValue.serverTimestamp(),
        });
        return { revision, updatedByUid: requestAuth.uid, alreadyApplied: false };
    });
});

/** Applies one receptionist Save as a field/room change set against the latest cloud booking. */
exports.applyBookingChangeSetServer = (0, https_1.onCall)({ invoker: "public" }, async (request) => {
    const requestAuth = await requireAuth(request);
    const hotelId = requireString(request.data?.hotelId || requestAuth.token.hotelId, "hotelId");
    await requireActiveHotelMember(requestAuth, hotelId);
    await requireUsableSubscription(hotelId);
    const operationId = requireString(request.data?.operationId, "operationId");
    const deviceId = requireString(request.data?.deviceId, "deviceId");
    const changeSet = (request.data?.changeSet || {});
    const bookingRemoteId = requireString(changeSet.bookingRemoteId, "bookingRemoteId");
    const create = booleanValue(changeSet.create);
    const setFields = (changeSet.setFields || {});
    const addRoomIds = new Set(stringList(changeSet.addRoomRemoteIds));
    const removeRoomIds = new Set(stringList(changeSet.removeRoomRemoteIds));
    const rebuildFinancialLines = booleanValue(changeSet.rebuildFinancialLines);
    const template = (changeSet.financialLineTemplate || {});
    const requestedLineIds = (changeSet.financialLineRemoteIdsByKey || {});
    const allowedFields = new Set([
        "bookingUuid", "propertyRemoteId", "guestName", "guestMobile", "sourceName", "sourceRemoteId",
        "sourceType", "adultCount", "childCount", "checkInMillis", "checkOutMillis", "pricingStatus",
        "bookingStatus", "cancelledAt", "cancellationReason", "notes", "grossCharges", "rate",
        "receivable", "roomRevenue", "propertyTax", "commissionAmount", "commissionTax", "sourceFee",
        "tdsAmount", "tcsAmount", "expectedPayout",
    ]);
    Object.keys(setFields).forEach((field) => {
        if (!allowedFields.has(field))
            throw new https_1.HttpsError("invalid-argument", `Unsupported booking field: ${field}`);
    });
    if (Array.from(addRoomIds).some((roomId) => removeRoomIds.has(roomId))) {
        throw new https_1.HttpsError("invalid-argument", "The same room cannot be added and removed in one save.");
    }
    if (addRoomIds.size === 0 && removeRoomIds.size === 0 && Object.keys(setFields).length === 0) {
        throw new https_1.HttpsError("invalid-argument", "This save contains no booking changes.");
    }
    const hotelRef = publicHotelRef(hotelId);
    const bookingDoc = hotelRef.collection("bookings").doc(bookingRemoteId);
    const mutationDoc = hotelRef.collection("appliedBookingChangeSets").doc(operationId);
    const auditDoc = hotelRef.collection("bookingAuditEvents").doc(operationId);
    return db.runTransaction(async (tx) => {
        const applied = await tx.get(mutationDoc);
        if (applied.exists) {
            if (String(applied.get("bookingRemoteId") || "") !== bookingRemoteId) {
                throw new https_1.HttpsError("aborted", "This operation ID was already used for another booking.");
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
            throw new https_1.HttpsError("already-exists", "This booking already exists.");
        }
        if (!create && !currentSnapshot.exists) {
            throw new https_1.HttpsError("not-found", "The booking no longer exists.");
        }
        const current = currentSnapshot.exists ? currentSnapshot.data() : {};
        const next = { ...current };
        Object.entries(setFields).forEach(([field, value]) => { next[field] = value; });
        next.hotelRemoteId = hotelId;
        next.updatedByUid = requestAuth.uid;
        next.updatedAt = Date.now();
        next.isDeleted = false;
        const previousRooms = new Set(stringList(current.roomRemoteIds));
        const nextRooms = new Set(previousRooms);
        addRoomIds.forEach((roomId) => nextRooms.add(roomId));
        removeRoomIds.forEach((roomId) => nextRooms.delete(roomId));
        if (nextRooms.size === 0)
            throw new https_1.HttpsError("invalid-argument", "Select at least one room.");
        next.roomRemoteIds = Array.from(nextRooms);
        const checkInMillis = numberValue(next.checkInMillis);
        const checkOutMillis = numberValue(next.checkOutMillis);
        if (checkInMillis <= 0 || checkOutMillis <= checkInMillis) {
            throw new https_1.HttpsError("invalid-argument", "Check-out must be after check-in.");
        }
        if (!String(next.guestName || "").trim())
            throw new https_1.HttpsError("invalid-argument", "Guest name is required.");
        if (String(next.bookingStatus || "RESERVED") === "CANCELLED" && !String(next.cancellationReason || "").trim()) {
            throw new https_1.HttpsError("invalid-argument", "Cancellation reason is required.");
        }
        const roomSnapshots = new Map();
        for (const roomId of nextRooms) {
            roomSnapshots.set(roomId, await tx.get(hotelRef.collection("rooms").doc(roomId)));
        }
        for (const room of roomSnapshots.values()) {
            if (!room.exists || booleanValue(room.get("isDeleted"))) {
                throw new https_1.HttpsError("failed-precondition", "A selected room no longer exists.");
            }
            if (String(room.get("lifecycleStatus") || "ACTIVE") !== "ACTIVE") {
                throw new https_1.HttpsError("failed-precondition", `${String(room.get("roomName") || "Room")} is disabled or retired.`);
            }
        }
        const propertyKeys = new Set(Array.from(roomSnapshots.values()).map((room) => String(room.get("propertyRemoteId") || "__MAIN_PROPERTY__")));
        if (propertyKeys.size !== 1) {
            throw new https_1.HttpsError("invalid-argument", "All selected rooms must belong to the same property.");
        }
        const propertyKey = Array.from(propertyKeys)[0];
        next.propertyRemoteId = propertyKey === "__MAIN_PROPERTY__" ? null : propertyKey;
        const cancelled = String(next.bookingStatus || "RESERVED") === "CANCELLED";
        const oldLockIds = currentSnapshot.exists
            ? lockIdsFor(stringList(current.roomRemoteIds), numberValue(current.checkInMillis), numberValue(current.checkOutMillis))
            : new Set();
        const newLockIds = cancelled ? new Set() : lockIdsFor(Array.from(nextRooms), checkInMillis, checkOutMillis);
        const lockSnapshots = new Map();
        const blockingIds = new Set();
        for (const lockId of newLockIds) {
            const snapshot = await tx.get(hotelRef.collection("bookingLocks").doc(lockId));
            lockSnapshots.set(lockId, snapshot);
            const lockedBy = String(snapshot.get("bookingRemoteId") || "");
            if (snapshot.exists && !booleanValue(snapshot.get("isDeleted")) && lockedBy && lockedBy !== bookingRemoteId) {
                blockingIds.add(lockedBy);
            }
        }
        const blockingBookings = new Map();
        for (const id of blockingIds)
            blockingBookings.set(id, await tx.get(hotelRef.collection("bookings").doc(id)));
        for (const lock of lockSnapshots.values()) {
            const lockedBy = String(lock.get("bookingRemoteId") || "");
            const blocker = blockingBookings.get(lockedBy);
            if (blocker?.exists && !booleanValue(blocker.get("isDeleted")) && String(blocker.get("bookingStatus") || "") !== "CANCELLED") {
                throw new https_1.HttpsError("already-exists", "A selected room is already booked for these dates.");
            }
        }
        const financialSnapshot = await tx.get(hotelRef.collection("bookingFinancialLines").where("bookingRemoteId", "==", bookingRemoteId));
        const finalBillPrefix = `${bookingRemoteId}_final_bill_`;
        const finalBills = await tx.get(hotelRef.collection("foodBills")
            .where(firestore_1.FieldPath.documentId(), ">=", finalBillPrefix)
            .where(firestore_1.FieldPath.documentId(), "<", `${finalBillPrefix}\uf8ff`)
            .limit(5));
        if (rebuildFinancialLines && finalBills.docs.some((doc) => !booleanValue(doc.get("isDeleted")))) {
            throw new https_1.HttpsError("failed-precondition", "Room price, dates and rooms are locked because the final bill has been issued.");
        }
        const estimatedFinancialWrites = rebuildFinancialLines ? newLockIds.size + financialSnapshot.size : 0;
        const estimatedWrites = 4 + newLockIds.size + Array.from(oldLockIds).filter((id) => !newLockIds.has(id)).length + estimatedFinancialWrites;
        if (estimatedWrites > 450) {
            throw new https_1.HttpsError("invalid-argument", "This booking is too large to save safely in one atomic operation.");
        }
        const revision = numberValue(current.revision) + 1;
        const previousValues = {};
        const acceptedValues = {};
        Object.keys(setFields).forEach((field) => {
            previousValues[field] = current[field] ?? null;
            acceptedValues[field] = next[field] ?? null;
        });
        previousValues.roomRemoteIds = Array.from(previousRooms);
        acceptedValues.roomRemoteIds = Array.from(nextRooms);
        for (const lockId of oldLockIds)
            if (!newLockIds.has(lockId))
                tx.delete(hotelRef.collection("bookingLocks").doc(lockId));
        tx.set(bookingDoc, { ...next, revision, serverUpdatedAt: firestore_1.FieldValue.serverTimestamp() });
        for (const lockId of newLockIds) {
            const parts = lockId.split("_");
            tx.set(hotelRef.collection("bookingLocks").doc(lockId), {
                hotelRemoteId: hotelId,
                bookingRemoteId,
                roomRemoteId: parts.slice(0, -1).join("_"),
                dateMillis: numberValue(parts[parts.length - 1]),
                isDeleted: false,
                updatedByUid: requestAuth.uid,
                serverUpdatedAt: firestore_1.FieldValue.serverTimestamp(),
            });
        }
        const financialLineRevisions = {};
        if (rebuildFinancialLines) {
            const grossPaise = Math.max(0, Math.round(numberValue(next.grossCharges) * 100));
            const dates = [];
            // checkInMillis/checkOutMillis already carry the app's exact business-date identity.
            // Re-normalising them in the Cloud Run timezone changes an India-local midnight
            // into a different epoch value and creates a second semantic room-night line.
            for (let day = checkInMillis; day < checkOutMillis; day += DAY_MILLIS)
                dates.push(day);
            const keys = Array.from(nextRooms).flatMap((roomId) => dates.map((day) => `${roomId}|${day}`));
            const existingByKey = new Map(financialSnapshot.docs.map((doc) => [`${doc.get("roomRemoteId")}|${numberValue(doc.get("businessDateMillis"))}`, doc]));
            const expectedIds = new Set();
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
                    updatedByUid: requestAuth.uid, serverUpdatedAt: firestore_1.FieldValue.serverTimestamp(),
                });
            });
            financialSnapshot.docs.filter((doc) => !expectedIds.has(doc.id) && !booleanValue(doc.get("isDeleted"))).forEach((doc) => {
                const lineRevision = numberValue(doc.get("revision")) + 1;
                financialLineRevisions[doc.id] = lineRevision;
                tx.set(doc.ref, { isDeleted: true, updatedAt: Date.now(), revision: lineRevision,
                    updatedByUid: requestAuth.uid, serverUpdatedAt: firestore_1.FieldValue.serverTimestamp() }, { merge: true });
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
            serverTime: firestore_1.FieldValue.serverTimestamp(),
        });
        tx.set(mutationDoc, { ...result, hotelRemoteId: hotelId, createdAt: firestore_1.FieldValue.serverTimestamp() });
        return result;
    });
});
function normalizeInvoicePrefix(value) {
    const cleaned = String(value || "INV")
        .trim()
        .toUpperCase()
        .replace(/[^A-Z0-9-]/g, "")
        .slice(0, 12);
    return cleaned || "INV";
}
function financialYearShortIndia(timeMillis) {
    const indiaMillis = timeMillis + 330 * 60 * 1000;
    const date = new Date(indiaMillis);
    const year = date.getUTCFullYear();
    const month = date.getUTCMonth();
    const startYear = month >= 3 ? year : year - 1;
    const endYear = startYear + 1;
    return `${String(startYear % 100).padStart(2, "0")}-${String(endYear % 100).padStart(2, "0")}`;
}
exports.reserveInvoiceNumber = (0, https_1.onCall)({ invoker: "public" }, async (request) => {
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
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
            updatedByUid: requestAuth.uid,
        }, { merge: true });
        return { billNumber, sequence: nextSequence, seriesPrefix };
    });
    v2_1.logger.info("Invoice number reserved", { hotelId, billNumber: result.billNumber });
    return result;
});
//# sourceMappingURL=index.js.map