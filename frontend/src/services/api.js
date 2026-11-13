// Single place that talks to the bank API.
// In development Vite proxies /api to the backend; set VITE_API_BASE_URL to call it directly.
const API_BASE = (import.meta.env.VITE_API_BASE_URL || "/api/v1").replace(/\/$/, "");

export class ApiError extends Error {
  constructor(message, status = 0, code = "NETWORK_ERROR", fields = {}) {
    super(message);
    this.status = status;
    this.code = code;
    this.fields = fields;
  }
}

// ---- session (kept in memory only, never in localStorage) -------------------------------------
let tokens = null; // { accessToken, refreshToken }
let onSessionExpired = () => {};
let refreshing = null;

export const session = {
  set: value => { tokens = value ? { accessToken: value.accessToken, refreshToken: value.refreshToken } : null; },
  clear: () => { tokens = null; },
  onExpired: handler => { onSessionExpired = handler; },
  get refreshToken() { return tokens?.refreshToken; },
};

async function send(path, options = {}, token) {
  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      ...options,
      headers: {
        ...(options.body && !(options.body instanceof FormData) ? { "Content-Type": "application/json" } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...options.headers,
      },
    });
  } catch {
    throw new ApiError("DanBank services are currently unreachable. Please try again shortly.");
  }
  return response;
}

async function parse(response) {
  const body = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) {
    throw new ApiError(
      body?.message || "We could not complete your request.",
      response.status,
      body?.code,
      body?.fields,
    );
  }
  return body;
}

async function refreshTokens() {
  if (!tokens?.refreshToken) throw new ApiError("Your session has expired. Please sign in again.", 401, "UNAUTHORIZED");
  refreshing ||= (async () => {
    try {
      const result = await parse(await send("/auth/refresh", { method: "POST", body: JSON.stringify({ refreshToken: tokens.refreshToken }) }));
      tokens = { accessToken: result.accessToken, refreshToken: result.refreshToken };
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}

/** Authenticated request. On a 401 it refreshes the token once and retries; if that fails the user is signed out. */
async function request(path, options = {}) {
  if (!tokens) throw new ApiError("Please sign in to continue.", 401, "UNAUTHORIZED");
  let response = await send(path, options, tokens.accessToken);
  if (response.status === 401) {
    try {
      await refreshTokens();
    } catch (error) {
      session.clear();
      onSessionExpired();
      throw error;
    }
    response = await send(path, options, tokens.accessToken);
  }
  return parse(response);
}

const json = (method, body) => ({ method, body: JSON.stringify(body) });
const query = params => {
  const search = new URLSearchParams(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ""));
  return search.toString() ? `?${search}` : "";
};

// ---- public ---------------------------------------------------------------------------------
// The secret for this browser's "trusted device" status. It is not a login: it only tells the bank this browser was
// verified before. Kept in localStorage so it survives closing the tab.
const DEVICE_KEY = "danbank.deviceToken";
export const deviceToken = {
  get: () => { try { return localStorage.getItem(DEVICE_KEY) || ""; } catch { return ""; } },
  set: value => { try { localStorage.setItem(DEVICE_KEY, value); } catch { /* storage blocked: next sign-in will just ask again */ } },
  clear: () => { try { localStorage.removeItem(DEVICE_KEY); } catch { /* nothing to do */ } },
};
const deviceHeader = () => (deviceToken.get() ? { "X-Device-Token": deviceToken.get() } : {});
const remember = result => { if (result?.deviceToken) deviceToken.set(result.deviceToken); return result; };

/** Face images go up as multipart: one "selfie" and any number of "liveness" frames. */
const faceForm = ({ selfie, liveness = [] }, extra = {}) => {
  const form = new FormData();
  Object.entries(extra).forEach(([name, value]) => form.append(name, value));
  form.append("selfie", selfie, "selfie.jpg");
  liveness.forEach((frame, index) => form.append("liveness", frame, `frame-${index}.jpg`));
  return form;
};

export const authApi = {
  signup: payload => send("/auth/signup", json("POST", payload)).then(parse),
  /** May return { status: "DEVICE_VERIFICATION_REQUIRED", challengeToken } instead of tokens: a new phone must verify its face. */
  login: (identifier, password) => send("/auth/login", { ...json("POST", { identifier, password }), headers: deviceHeader() }).then(parse).then(remember),
  verifyDevice: (challengeToken, faces) => send("/auth/device/verify", { method: "POST", body: faceForm(faces, { challengeToken }) }).then(parse).then(remember),
  logout: () => (tokens?.refreshToken
    ? send("/auth/logout", json("POST", { refreshToken: tokens.refreshToken })).then(() => null).catch(() => null)
    : Promise.resolve(null)),
};

// ---- signed in ------------------------------------------------------------------------------
export const dashboardApi = {
  get: accountId => request(`/dashboard${query({ accountId })}`),
};

export const accountsApi = {
  close: (userId, accountId) => request(`/accounts/${accountId}/user/${userId}`, { method: "DELETE" }),
  open: (userId, type) => request(`/accounts/user/${userId}${query({ type })}`, { method: "POST" }),
  lookup: accountNumber => request(`/accounts/lookup/${encodeURIComponent(accountNumber)}`),
  history: (accountId, page = 0, size = 20) => request(`/accounts/${accountId}/transactions${query({ page, size })}`),
  statement: (accountId, params) => request(`/accounts/${accountId}/statement${query(params)}`),
  statementCsv: async (accountId, params) => {
    const call = token => send(`/accounts/${accountId}/statement${query({ ...params, format: "csv" })}`, {}, token);
    let response = await call(tokens?.accessToken);
    if (response.status === 401) { await refreshTokens(); response = await call(tokens.accessToken); }
    if (!response.ok) throw new ApiError("We could not prepare your statement.", response.status);
    return response.blob();
  },
  quote: amount => request(`/transfers/quote${query({ amount })}`),
  /** idempotencyKey must be created once per transfer attempt so a retry can never send the money twice. */
  transfer: ({ fromAccountId, toAccountNumber, amount, description }, pin, idempotencyKey) =>
    request("/accounts/transfer/by-number", {
      ...json("POST", { fromAccountId, toAccountNumber, amount, description }),
      headers: { "X-Transaction-Pin": pin, "Idempotency-Key": idempotencyKey },
    }),
};

export const depositsApi = {
  /** Starts a card payment; send the customer to the returned authorizationUrl. */
  start: (accountId, amount) => request("/deposits", json("POST", { accountId, amount })),
  status: reference => request(`/deposits/${encodeURIComponent(reference)}`),
};

export const savingsApi = {
  list: () => request("/savings"),
  create: goal => request("/savings", json("POST", goal)),
  deposit: (id, accountId, amount) => request(`/savings/${id}/deposit`, json("POST", { accountId, amount })),
  withdraw: (id, accountId, amount, pin) => request(`/savings/${id}/withdraw`, { ...json("POST", { accountId, amount }), headers: { "X-Transaction-Pin": pin } }),
  close: (id, accountId, pin) => request(`/savings/${id}/close`, { ...json("POST", { accountId }), headers: { "X-Transaction-Pin": pin } }),
  changeTarget: (id, targetAmount) => request(`/savings/${id}/target`, json("PUT", { targetAmount })),
};

export const insightsApi = {
  get: accountId => request(`/insights${query({ accountId })}`),
};

export const devicesApi = {
  list: () => request("/devices", { headers: deviceHeader() }),
  remove: id => request(`/devices/${id}`, { method: "DELETE" }),
};

export const beneficiariesApi = {
  list: () => request("/beneficiaries"),
  add: (accountNumber, nickname) => request("/beneficiaries", json("POST", { accountNumber, nickname })),
  remove: id => request(`/beneficiaries/${id}`, { method: "DELETE" }),
};

export const notificationsApi = {
  list: ({ unread, page = 0, size = 30 } = {}) => request(`/notifications${query({ unread, page, size })}`),
  unreadCount: () => request("/notifications/unread-count"),
  markRead: id => request(`/notifications/${id}/read`, { method: "POST" }),
  markAllRead: () => request("/notifications/read-all", { method: "POST" }),
};

export const securityApi = {
  /** Returns a fresh token pair: every other device is signed out, this one stays signed in. */
  changePassword: async (currentPassword, newPassword) => {
    const result = await request("/users/me/password", { ...json("POST", { currentPassword, newPassword }), headers: deviceHeader() });
    tokens = { accessToken: result.accessToken, refreshToken: result.refreshToken };
    return result;
  },
  setPin: (pin, password) => request("/users/me/pin", json("POST", { pin, password })),
  updateProfile: (userId, changes) => request(`/users/updateUser/${userId}`, json("PUT", changes)),
};

export const kycApi = {
  status: () => request("/kyc/status"),
  verifyFace: faces => request("/kyc/face", { method: "POST", body: faceForm(faces) }),
};

export const newIdempotencyKey = () => crypto.randomUUID();
