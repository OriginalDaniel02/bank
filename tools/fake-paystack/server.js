#!/usr/bin/env node
// A LOCAL STAND-IN FOR PAYSTACK, FOR DEVELOPMENT AND TESTING ONLY.
//
// It is not Paystack and proves nothing about your real Paystack account. It lets you try the whole card-deposit flow
// on your machine without an account or keys: it implements just enough of
//   POST /transaction/initialize   and   GET /transaction/verify/:reference
// and shows a fake checkout page whose buttons send the same kind of signed webhook Paystack sends
// (x-paystack-signature = HMAC-SHA512 of the raw body, keyed with the secret key).
//
// Run:   node tools/fake-paystack/server.js
// Then start the backend with:
//   PAYSTACK_SECRET_KEY=sk_test_fake_dev_secret  PAYSTACK_BASE_URL=http://localhost:9099
// Uses only Node's built-in modules (nothing to install).
"use strict";
const http = require("http");
const crypto = require("crypto");

const SECRET = process.env.PAYSTACK_SECRET_KEY || "sk_test_fake_dev_secret";
const BACKEND = (process.env.BACKEND_URL || "http://localhost:8080").replace(/\/$/, "");
const PORT = Number(process.env.PORT || 9099);
const PUBLIC_URL = (process.env.PUBLIC_URL || `http://localhost:${PORT}`).replace(/\/$/, "");

const payments = new Map(); // reference -> { email, amount, currency, callback, status }
let sendWebhooks = process.env.SEND_WEBHOOK !== "false"; // turn off to test paying when webhooks cannot reach you

const esc = value => String(value).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const json = (res, status, body) => { res.writeHead(status, { "Content-Type": "application/json" }); res.end(JSON.stringify(body)); };
const readBody = req => new Promise(resolve => { const chunks = []; req.on("data", c => chunks.push(c)); req.on("end", () => resolve(Buffer.concat(chunks).toString("utf8"))); });
const authorized = req => req.headers.authorization === `Bearer ${SECRET}`;

async function sendWebhook(reference) {
  const payment = payments.get(reference);
  const body = JSON.stringify({
    event: "charge.success",
    data: { id: Date.now(), reference, amount: payment.amount, currency: payment.currency, status: "success", customer: { email: payment.email } },
  });
  const signature = crypto.createHmac("sha512", SECRET).update(body).digest("hex");
  try {
    const response = await fetch(`${BACKEND}/api/v1/webhooks/paystack`, {
      method: "POST", headers: { "Content-Type": "application/json", "x-paystack-signature": signature }, body,
    });
    console.log(`webhook for ${reference} -> backend answered ${response.status}`);
    return response.status;
  } catch (error) {
    console.log(`webhook for ${reference} failed: ${error.message}`);
    return 0;
  }
}

const page = (title, body) => `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(title)}</title>
<style>body{font:16px system-ui;background:#f4f6f8;display:grid;place-items:center;min-height:100vh;margin:0}main{background:#fff;padding:32px;border-radius:14px;max-width:420px;box-shadow:0 8px 30px #0001}
h1{font-size:20px;margin:0 0 6px}p{color:#556}.amount{font-size:32px;font-weight:700;margin:18px 0}button{display:block;width:100%;padding:13px;margin-top:10px;border:0;border-radius:9px;font-size:15px;cursor:pointer}
.pay{background:#0b6e4f;color:#fff}.fail{background:#fff;border:1px solid #c33;color:#c33}.cancel{background:#fff;border:1px solid #99a;color:#556}.banner{background:#fff3cd;color:#664d03;padding:8px 12px;border-radius:8px;font-size:13px;margin-bottom:16px}</style>
<main><div class="banner">FAKE PAYSTACK: a local development stand-in. No real money moves.</div>${body}</main>`;

http.createServer(async (req, res) => {
  const url = new URL(req.url, PUBLIC_URL);
  const path = url.pathname;
  try {
    if (req.method === "POST" && path === "/transaction/initialize") {
      if (!authorized(req)) return json(res, 401, { status: false, message: "Invalid key" });
      const input = JSON.parse((await readBody(req)) || "{}");
      if (!input.email || !Number.isInteger(input.amount) || input.amount < 100 || !input.reference) {
        return json(res, 400, { status: false, message: "Invalid email, amount (integer, in kobo) or reference" });
      }
      if (payments.has(input.reference)) return json(res, 400, { status: false, message: "Duplicate Transaction Reference" });
      payments.set(input.reference, { email: input.email, amount: input.amount, currency: input.currency || "NGN", callback: input.callback_url, status: "abandoned" });
      console.log(`initialized ${input.reference}: ${input.amount} kobo for ${input.email}`);
      return json(res, 200, { status: true, message: "Authorization URL created",
        data: { authorization_url: `${PUBLIC_URL}/pay/${input.reference}`, access_code: crypto.randomBytes(8).toString("hex"), reference: input.reference } });
    }
    const verify = path.match(/^\/transaction\/verify\/([^/]+)$/);
    if (req.method === "GET" && verify) {
      if (!authorized(req)) return json(res, 401, { status: false, message: "Invalid key" });
      const payment = payments.get(decodeURIComponent(verify[1]));
      if (!payment) return json(res, 404, { status: false, message: "Transaction reference not found" });
      return json(res, 200, { status: true, message: "Verification successful",
        data: { reference: decodeURIComponent(verify[1]), status: payment.status, amount: payment.amount, currency: payment.currency } });
    }
    const checkout = path.match(/^\/pay\/([^/]+)$/);
    if (req.method === "GET" && checkout) {
      const reference = decodeURIComponent(checkout[1]);
      const payment = payments.get(reference);
      if (!payment) return json(res, 404, { status: false, message: "Unknown payment" });
      const naira = (payment.amount / 100).toLocaleString("en-NG", { minimumFractionDigits: 2 });
      res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
      return res.end(page("Fake Paystack checkout", `<h1>Pay with card</h1><p>${esc(payment.email)}</p><div class="amount">₦${esc(naira)}</div>
        <form method="post" action="/complete/${encodeURIComponent(reference)}?result=success"><button class="pay">Pay ₦${esc(naira)}</button></form>
        <form method="post" action="/complete/${encodeURIComponent(reference)}?result=failed"><button class="fail">Simulate a declined card</button></form>
        <form method="post" action="/complete/${encodeURIComponent(reference)}?result=abandoned"><button class="cancel">Cancel</button></form>`));
    }
    const complete = path.match(/^\/complete\/([^/]+)$/);
    if (req.method === "POST" && complete) {
      const reference = decodeURIComponent(complete[1]);
      const payment = payments.get(reference);
      if (!payment) return json(res, 404, { status: false, message: "Unknown payment" });
      const result = url.searchParams.get("result");
      payment.status = result === "success" ? "success" : result === "failed" ? "failed" : "abandoned";
      if (payment.status === "success" && sendWebhooks) await sendWebhook(reference);
      const back = new URL(payment.callback || "http://localhost:5173");
      back.searchParams.set("trxref", reference); back.searchParams.set("reference", reference);
      res.writeHead(303, { Location: back.toString() });
      return res.end();
    }
    // ---- test helpers (not part of Paystack) ----
    const replay = path.match(/^\/admin\/replay-webhook\/([^/]+)$/);
    if (req.method === "POST" && replay) return json(res, 200, { backendStatus: await sendWebhook(decodeURIComponent(replay[1])) });
    if (req.method === "POST" && path === "/admin/webhooks") {
      sendWebhooks = url.searchParams.get("enabled") !== "false";
      return json(res, 200, { sendWebhooks });
    }
    json(res, 404, { status: false, message: "Not found" });
  } catch (error) {
    console.error(error);
    json(res, 500, { status: false, message: "Fake Paystack error" });
  }
}).listen(PORT, () => console.log(`Fake Paystack (development only) on ${PUBLIC_URL}  | secret key: ${SECRET} | webhooks to ${BACKEND}: ${sendWebhooks ? "on" : "off"}`));
