import { useEffect, useState } from "react";
import FaceCapture from "../../components/FaceCapture.jsx";
import { accountsApi, kycApi } from "../../services/api.js";

const money = new Intl.NumberFormat("en-NG", { style: "currency", currency: "NGN", maximumFractionDigits: 0 });

export default function VerificationCenter({ user, kycTier, onClose, onVerified }) {
  const [limits, setLimits] = useState(null);
  const [consent, setConsent] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);
  const verified = kycTier >= 1;

  useEffect(() => { accountsApi.quote(100).then(setLimits).catch(() => {}); }, [kycTier]);

  const submit = async faces => {
    if (!consent) return setError("Please agree to the identity check first.");
    setBusy(true); setError("");
    try {
      const status = await kycApi.verifyFace(faces);
      setResult(status);
      if (status.tier >= 1) onVerified?.(status.tier);
    } catch (requestError) {
      setError(requestError.status === 503 ? "Identity verification isn’t available right now. Please try again later." : requestError.message);
    } finally { setBusy(false); }
  };

  return <div className="verification-dialog" role="dialog" aria-modal="true">
    <button className="verification-backdrop" onClick={onClose} />
    <section className="verification-window">
      <header className="verification-header"><div><span>ACCOUNT VERIFICATION</span><h2>Verify your identity</h2><p>Verify once to unlock higher transfer limits and to sign in safely on new phones.</p></div><button onClick={onClose}>×</button></header>
      <div className="verification-layout">
        <aside className="tier-panel">
          <div className="tier-badge"><span>{kycTier}</span><div><small>CURRENT ACCOUNT LEVEL</small><strong>{verified ? "Verified" : "Not verified"}</strong><p>{user.firstName} {user.lastName}</p></div></div>
          <div className="tier-progress"><div><span>Verification progress</span><strong>{verified ? "1 of 1 complete" : "0 of 1 complete"}</strong></div><i><b style={{ width: verified ? "100%" : "0%" }} /></i></div>
          <dl>
            <div><dt>Daily transfer limit</dt><dd>{limits ? money.format(limits.dailyLimit) : "…"}</dd></div>
            <div><dt>Limit per transfer</dt><dd>{limits ? money.format(limits.perTransferLimit) : "…"}</dd></div>
            <div><dt>Account status</dt><dd className="active">Active</dd></div>
          </dl>
          <div className="tier-security"><span>✓</span><p><strong>Your information is protected</strong>We keep only the result of the check, never your photos.</p></div>
        </aside>
        <main className="verification-main">
          {result ? <section className="verification-review">
            <span className="review-clock">{result.faceStatus === "APPROVED" ? "✓" : "!"}</span>
            <small>{result.faceStatus === "APPROVED" ? "VERIFIED" : "NOT VERIFIED"}</small>
            <h3>{result.faceStatus === "APPROVED" ? "Your identity is verified" : "We couldn’t verify you"}</h3>
            <p>{result.faceStatus === "APPROVED" ? "Your transfer limits have been raised." : "Please try again in good light, facing the camera."}</p>
            <button onClick={result.faceStatus === "APPROVED" ? onClose : () => setResult(null)}>{result.faceStatus === "APPROVED" ? "Return to dashboard" : "Try again"}</button>
          </section> : verified ? <section className="verification-review">
            <span className="review-clock">✓</span><small>VERIFIED</small><h3>You’re already verified</h3><p>You have the standard transfer limits, and a new phone will ask for a quick face check before it can sign in.</p><button onClick={onClose}>Return to dashboard</button>
          </section> : <div>
            <div className="verification-intro"><h3>Selfie verification</h3><p>The camera takes a selfie and a few quick frames to confirm you’re a real person. We don’t keep your photos.</p></div>
            <label className="consent"><input type="checkbox" checked={consent} onChange={e => { setConsent(e.target.checked); setError(""); }} /><span>I agree to DanBank checking my face to verify my identity.</span></label>
            <FaceCapture onCapture={submit} disabled={busy || !consent} label="Start face check" />
            {busy && <p className="payment-hint">Checking…</p>}
            {error && <p className="payment-error">{error}</p>}
          </div>}
        </main>
      </div>
    </section>
  </div>;
}
