/* Read each fetch response body exactly once and normalize Spring error responses. */
function apiErrorMessage(status, data) {
  const fallback = {
    400: "The request is invalid. Check your selections and try again.",
    401: "Your session has expired. Please log in again.",
    403: "You do not have permission to do that.",
    404: "The requested item could not be found.",
    405: "That action is not supported.",
    409: "That action conflicts with the current league state. Refresh and try again.",
    422: "Some of the submitted information is invalid.",
    429: "Too many requests. Please wait a moment and try again.",
    500: "The server ran into an unexpected error. Please try again.",
    502: "The server is temporarily unavailable. Please try again.",
    503: "The server is temporarily unavailable. Please try again.",
  };
  if (
    typeof data === "string" &&
    data.trim() &&
    !/^bad request$/i.test(data.trim()) &&
    !data.trim().startsWith("<") &&
    data.length < 400
  )
    return data.trim();
  if (data && typeof data === "object") {
    const candidates = [
      data.message,
      data.detail,
      data.reason,
      data.description,
    ];
    for (const value of candidates) {
      if (
        typeof value === "string" &&
        value.trim() &&
        !/^bad request$/i.test(value.trim())
      )
        return value.trim();
    }
    if (data.errors && typeof data.errors === "object") {
      const values = Array.isArray(data.errors)
        ? data.errors
        : Object.values(data.errors);
      const messages = values
        .flatMap((v) => (Array.isArray(v) ? v : [v]))
        .map((v) =>
          typeof v === "string" ? v : v?.defaultMessage || v?.message || "",
        )
        .filter(Boolean);
      if (messages.length) return messages.join(" · ");
    }
  }
  return fallback[status] || `Request failed (${status}). Please try again.`;
}
api = async function (url, options = {}) {
  const opts = {...options, headers: {
    "Content-Type":"application/json", "X-Requested-With":"XMLHttpRequest",
    ...(options.headers || {})
  }};
  if (options.body && typeof options.body !== "string")
    opts.body = JSON.stringify(options.body);
  let res;
  try {
    res = await fetch(url, opts);
  } catch {
    throw new Error(
      "Could not reach the server. Check your connection and try again.",
    );
  }
  if (res.status === 204) return null;
  const raw = await res.text();
  let data = null;
  if (raw) {
    try {
      data = JSON.parse(raw);
    } catch {
      data = raw;
    }
  }
  if (!res.ok) throw new Error(apiErrorMessage(res.status, data));
  return data;
};
