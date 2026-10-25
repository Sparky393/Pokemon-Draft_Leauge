/* Three minutes of real input inactivity. All state is browser/session state, never league data. */
(() => {
  const IDLE_MS = 180000;
  const STORAGE_KEY = "league-session-activity-v1";
  let active = false,
    stopped = false,
    lastActivity = 0,
    session = null,
    identity = null;
  let generation = 0,
    timer = null,
    logoutPromise = null,
    refreshPromise = null;
  let needsRefresh = false,
    refreshPage = null;
  const reads = new Set();
  const appState = () => (typeof state === "undefined" ? null : state);
  const readShared = () => {
    try {
      const value = JSON.parse(localStorage.getItem(STORAGE_KEY) || "null");
      return value &&
        typeof value.session === "string" &&
        Number.isFinite(value.lastActivity)
        ? value
        : null;
    } catch {
      return null;
    }
  };
  const writeShared = (loggedOut = false) => {
    try {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ session, identity, lastActivity, loggedOut }),
      );
    } catch {}
  };
  function cancelReads() {
    for (const controller of reads) controller.abort();
    reads.clear();
  }
  function busy(value) {
    const app = document.querySelector("#app");
    if (app) app.inert = value;
    const status = document.querySelector("#session-refresh-status");
    if (status) status.hidden = !value;
  }
  function stopLocal(message) {
    active = false;
    stopped = true;
    generation++;
    clearTimeout(timer);
    cancelReads();
    busy(false);
    const current = appState();
    if (current) {
      current.user = null;
      current.teams = [];
      current.settings = null;
      current.page = "home";
    }
    document.querySelector("#app")?.classList.add("hidden");
    document.querySelector("#login-screen")?.classList.remove("hidden");
    document
      .querySelectorAll(".pokemon-detail-overlay")
      .forEach((node) => node.remove());
    for (const id of ["#login-username", "#login-password"]) {
      const input = document.querySelector(id);
      if (input) input.value = "";
    }
    const notice = document.querySelector("#session-idle-message");
    if (notice) {
      notice.textContent = message;
      notice.classList.remove("hidden");
    }
  }
  function logout(
    message = "Signed out after 3 minutes without activity. Sign in again to continue.",
  ) {
    if (stopped) return logoutPromise || Promise.resolve();
    stopLocal(message); // Stop requests before the final, session-only logout call.
    writeShared(true);
    logoutPromise = fetch("/api/auth/logout", {
      method: "POST",
      credentials: "same-origin",
      keepalive: true,
      headers: { "X-Requested-With": "XMLHttpRequest" },
    }).catch(() => {}); // No background retry; the browser stays signed out while offline.
    return logoutPromise;
  }
  function syncShared() {
    const shared = readShared();
    if (!active || !shared) return;
    if (shared.session !== session) {
      stopLocal(
        "The session changed in another tab. Sign in again to continue.",
      );
    } else if (shared.loggedOut) {
      stopLocal("Signed out in another tab. Sign in again to continue.");
    } else {
      lastActivity = Math.max(lastActivity, shared.lastActivity);
    }
  }
  function expired() {
    syncShared();
    if (!active) return stopped;
    if (Date.now() - lastActivity >= IDLE_MS) {
      logout();
      return true;
    }
    return false;
  }
  function schedule() {
    clearTimeout(timer);
    if (!active) return;
    timer = setTimeout(
      () => {
        if (!expired()) schedule();
      },
      Math.max(1, IDLE_MS - (Date.now() - lastActivity)),
    );
  }
  function begin(user, fresh = false) {
    const key = `${user.role}:${user.id ?? ""}:${user.username}`;
    const shared = readShared();
    generation++;
    cancelReads();
    logoutPromise = null;
    refreshPromise = null;
    stopped = false;
    active = true;
    identity = key;
    const reuse = !fresh && shared?.identity === key;
    session = reuse
      ? shared.session
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    lastActivity = reuse ? shared.lastActivity : Date.now();
    needsRefresh = document.hidden;
    if (reuse && shared.loggedOut) {
      stopLocal("Sign in again to continue.");
      return false;
    }
    if (Date.now() - lastActivity >= IDLE_MS) {
      logout();
      return false;
    }
    writeShared();
    document.querySelector("#session-idle-message")?.classList.add("hidden");
    busy(false);
    schedule();
    return true;
  }
  function canRestore() {
    const shared = readShared();
    if (shared?.loggedOut) {
      stopLocal("Sign in again to continue.");
      return false;
    }
    if (shared && Date.now() - shared.lastActivity >= IDLE_MS) {
      session = shared.session;
      identity = shared.identity;
      lastActivity = shared.lastActivity;
      logout();
      return false;
    }
    return !stopped;
  }
  function activity(event) {
    if (!active || document.hidden || !event.isTrusted || expired()) return;
    // Pointer movement is frequent; one update a second is sufficient for idle timing.
    if (Date.now() - lastActivity < 1000) return;
    lastActivity = Date.now();
    writeShared();
    schedule();
  }
  async function resume() {
    if (
      !active ||
      expired() ||
      document.hidden ||
      !needsRefresh ||
      refreshPromise
    )
      return;
    if (!refreshPage) return;
    const started = generation;
    busy(true);
    refreshPromise = Promise.resolve().then(refreshPage);
    try {
      await refreshPromise;
      if (active && generation === started) needsRefresh = false;
    } catch {
      if (active && generation === started)
        logout("Could not refresh league data. Sign in again to continue.");
    } finally {
      if (generation === started) {
        refreshPromise = null;
        busy(false);
      }
    }
  }
  function beforeRequest(url, options) {
    const path = String(url).split("?")[0];
    const entry = [
      "/api/auth/login",
      "/api/auth/guest",
      "/api/auth/logout",
    ].includes(path);
    if (entry) return { assertCurrent() {}, finish() {} };
    if (expired() || (!active && (stopped || path !== "/api/auth/me")))
      throw new Error("Your session has ended. Sign in again to continue.");
    if (document.hidden)
      throw new Error("League requests pause while this tab is hidden.");
    const read = ["GET", "HEAD"].includes(
      (options.method || "GET").toUpperCase(),
    );
    if (!read && (needsRefresh || refreshPromise))
      throw new Error("League data is refreshing. Wait before submitting.");
    const started = generation;
    const controller = read ? new AbortController() : null;
    const originalSignal = options.signal;
    const abort = () => controller?.abort();
    if (controller) {
      reads.add(controller);
      if (originalSignal?.aborted) controller.abort();
      else originalSignal?.addEventListener("abort", abort, { once: true });
      options.signal = controller.signal;
    }
    return {
      assertCurrent() {
        if (generation !== started || stopped || controller?.signal.aborted)
          throw new Error(
            "The session or page changed before this request finished.",
          );
      },
      finish() {
        if (controller) reads.delete(controller);
        originalSignal?.removeEventListener("abort", abort);
      },
    };
  }
  globalThis.leagueSession = {
    begin,
    canRestore,
    logout,
    beforeRequest,
    isEnded() {
      return stopped;
    },
    canPoll() {
      return (
        active &&
        !expired() &&
        !document.hidden &&
        !needsRefresh &&
        !refreshPromise
      );
    },
    setRefresh(callback) {
      refreshPage = callback;
    },
  };
  for (const name of [
    "pointerdown",
    "pointermove",
    "keydown",
    "wheel",
    "touchstart",
    "touchmove",
  ])
    document.addEventListener(name, activity, { capture: true, passive: true });
  // Do not count programmatic scrolls or polling responses as activity.
  document.addEventListener("visibilitychange", () => {
    if (!active || expired()) return;
    if (document.hidden) {
      needsRefresh = true;
      cancelReads();
    } else resume();
  });
  window.addEventListener("focus", () => {
    if (active && !expired()) resume();
  });
  window.addEventListener("storage", (event) => {
    if (event.key !== STORAGE_KEY || !active) return;
    if (!expired()) schedule();
  });
})();
