import { initializeApp } from "https://www.gstatic.com/firebasejs/10.13.2/firebase-app.js";
import {
  getAuth,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signOut,
} from "https://www.gstatic.com/firebasejs/10.13.2/firebase-auth.js";
import {
  getFunctions,
  httpsCallable,
} from "https://www.gstatic.com/firebasejs/10.13.2/firebase-functions.js";

const firebaseConfig = {
  apiKey: "AIzaSyCBHT4T1UgUd3yk0moRTUWcYg-TnKc7KvA",
  authDomain: "booking-register-aadd3.firebaseapp.com",
  projectId: "booking-register-aadd3",
  storageBucket: "booking-register-aadd3.firebasestorage.app",
  messagingSenderId: "59100482110",
  appId: "1:59100482110:web:1517344b7a340d96421f6f",
};

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const functions = getFunctions(app, "asia-south1");

const loginView = document.getElementById("loginView");
const dashboardView = document.getElementById("dashboardView");
const emailInput = document.getElementById("emailInput");
const passwordInput = document.getElementById("passwordInput");
const loginButton = document.getElementById("loginButton");
const logoutButton = document.getElementById("logoutButton");
const refreshButton = document.getElementById("refreshButton");
const importButton = document.getElementById("importButton");
const searchInput = document.getElementById("searchInput");
const statusFilter = document.getElementById("statusFilter");
const statsRow = document.getElementById("statsRow");
const hotelList = document.getElementById("hotelList");
const summaryText = document.getElementById("summaryText");
const loginMessage = document.getElementById("loginMessage");
const dashboardMessage = document.getElementById("dashboardMessage");
const hotelCardTemplate = document.getElementById("hotelCardTemplate");

const listHotelAccounts = httpsCallable(functions, "listHotelAccounts");
const setHotelSubscription = httpsCallable(functions, "setHotelSubscription");
const importExistingHotels = httpsCallable(functions, "importExistingHotels");

let allHotels = [];

loginButton.addEventListener("click", login);
logoutButton.addEventListener("click", () => signOut(auth));
refreshButton.addEventListener("click", loadHotels);
importButton.addEventListener("click", importHotels);
searchInput.addEventListener("input", renderFilteredHotels);
statusFilter.addEventListener("change", renderFilteredHotels);

onAuthStateChanged(auth, (user) => {
  const loggedIn = Boolean(user);
  loginView.classList.toggle("hidden", loggedIn);
  dashboardView.classList.toggle("hidden", !loggedIn);
  logoutButton.classList.toggle("hidden", !loggedIn);

  if (loggedIn) {
    loadHotels();
  } else {
    allHotels = [];
    hotelList.innerHTML = "";
    statsRow.innerHTML = "";
    summaryText.textContent = "Login to view hotel accounts.";
  }
});

async function login() {
  loginMessage.textContent = "";
  loginButton.disabled = true;

  try {
    await signInWithEmailAndPassword(
      auth,
      emailInput.value.trim(),
      passwordInput.value
    );
  } catch (error) {
    loginMessage.textContent = readableError(error);
  } finally {
    loginButton.disabled = false;
  }
}

async function loadHotels() {
  dashboardMessage.textContent = "";
  refreshButton.disabled = true;
  summaryText.textContent = "Loading hotel accounts...";

  try {
    const result = await listHotelAccounts({});
    allHotels = result.data.hotels || [];
    renderFilteredHotels();
  } catch (error) {
    allHotels = [];
    hotelList.innerHTML = "";
    statsRow.innerHTML = "";
    summaryText.textContent = "Unable to load hotel accounts.";
    dashboardMessage.textContent = readableError(error);
  } finally {
    refreshButton.disabled = false;
  }
}

async function importHotels() {
  const confirmed = window.confirm(
    "Import existing hotels into the admin account system? This will not change bookings or rooms."
  );
  if (!confirmed) return;

  dashboardMessage.textContent = "";
  importButton.disabled = true;
  refreshButton.disabled = true;

  try {
    const result = await importExistingHotels({});
    const data = result.data || {};
    dashboardMessage.style.color = "#17663a";
    dashboardMessage.textContent =
      `Import complete. Scanned ${data.scannedHotels || 0}, created ${data.createdAccounts || 0}, updated ${data.updatedAccounts || 0}, skipped ${data.skippedHotels || 0}.`;
    await loadHotels();
  } catch (error) {
    dashboardMessage.style.color = "#a11f1f";
    dashboardMessage.textContent = readableError(error);
  } finally {
    importButton.disabled = false;
    refreshButton.disabled = false;
  }
}

function renderFilteredHotels() {
  const query = searchInput.value.trim().toLowerCase();
  const status = statusFilter.value;
  const filtered = allHotels.filter((hotel) => {
    const matchesStatus = status === "ALL" || hotel.status === status;
    const haystack = [
      hotel.hotelName,
      hotel.hotelId,
      hotel.ownerEmail,
      hotel.planId,
    ].join(" ").toLowerCase();
    return matchesStatus && (!query || haystack.includes(query));
  });

  renderStats(allHotels);
  renderHotels(filtered, allHotels.length);
}

function renderStats(hotels) {
  const total = hotels.length;
  const active = hotels.filter((hotel) => hotel.status === "ACTIVE").length;
  const trial = hotels.filter((hotel) => hotel.status === "TRIALING").length;
  const blocked = hotels.filter((hotel) => hotel.status === "PAST_DUE" || hotel.status === "SUSPENDED").length;
  statsRow.innerHTML = [
    statCard("Total Hotels", total),
    statCard("Active", active),
    statCard("Trial", trial),
    statCard("Blocked", blocked),
  ].join("");
}

function statCard(label, value) {
  return `<section class="stat-card"><span>${label}</span><strong>${value}</strong></section>`;
}

function renderHotels(hotels, totalCount = hotels.length) {
  hotelList.innerHTML = "";
  summaryText.textContent =
    hotels.length === totalCount
      ? `${hotels.length} hotel account${hotels.length === 1 ? "" : "s"}`
      : `${hotels.length} of ${totalCount} hotel accounts`;

  if (hotels.length === 0) {
    hotelList.innerHTML = `<section class="panel">No matching hotels found.</section>`;
    return;
  }

  hotels.forEach((hotel) => {
    const node = hotelCardTemplate.content.cloneNode(true);
    const card = node.querySelector(".hotel-card");
    const status = hotel.status || "SUSPENDED";

    node.querySelector(".hotel-name").textContent = hotel.hotelName || hotel.ownerEmail || hotel.hotelId;
    node.querySelector(".hotel-id").textContent = hotel.hotelId;
    node.querySelector(".owner-email").textContent = hotel.ownerEmail || "No owner email";
    node.querySelector(".users").textContent = `${hotel.activeUsers || 0}/${hotel.maxUsers || 0}`;
    node.querySelector(".access-until").textContent = formatDate(hotel.accessUntilMillis);
    node.querySelector(".plan-id").textContent = hotel.planId || "starter_199_monthly";

    const pill = node.querySelector(".status-pill");
    pill.textContent = labelStatus(status);
    pill.classList.add(`status-${status}`);

    const statusSelect = node.querySelector(".status-select");
    const maxUsersInput = node.querySelector(".max-users-input");
    const accessDaysInput = node.querySelector(".access-days-input");
    const saveButton = node.querySelector(".save-button");

    statusSelect.value = status;
    maxUsersInput.value = hotel.maxUsers || 2;

    saveButton.addEventListener("click", async () => {
      dashboardMessage.textContent = "";
      saveButton.disabled = true;

      try {
        const accessDays = Number(accessDaysInput.value || 31);
        const accessUntilMillis = Date.now() + accessDays * 24 * 60 * 60 * 1000;

        await setHotelSubscription({
          hotelId: hotel.hotelId,
          status: statusSelect.value,
          maxUsers: Number(maxUsersInput.value || 2),
          planId: hotel.planId || "starter_199_monthly",
          accessUntilMillis,
        });

        dashboardMessage.textContent = "Saved successfully.";
        dashboardMessage.style.color = "#17663a";
        await loadHotels();
      } catch (error) {
        dashboardMessage.style.color = "#a11f1f";
        dashboardMessage.textContent = readableError(error);
      } finally {
        saveButton.disabled = false;
      }
    });

    hotelList.appendChild(card);
  });
}

function formatDate(millis) {
  if (!millis) return "Not set";
  return new Intl.DateTimeFormat("en-IN", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  }).format(new Date(millis));
}

function labelStatus(status) {
  switch (status) {
    case "TRIALING":
      return "Trial";
    case "ACTIVE":
      return "Active";
    case "PAST_DUE":
      return "Past due";
    default:
      return "Suspended";
  }
}

function readableError(error) {
  const message = error?.message || String(error);
  return message.replace("Firebase: ", "").replace(/\.$/, "");
}
