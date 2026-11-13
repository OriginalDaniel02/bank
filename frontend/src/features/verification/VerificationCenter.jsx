import { useEffect, useState } from "react";
import Icon from "../../components/Icon.jsx";
import { accountsApi, kycApi } from "../../services/api.js";

const money = new Intl.NumberFormat("en-NG", { style: "currency", currency: "NGN", maximumFractionDigits: 0 });
const MAX_BYTES = 5 * 1024 * 1024;

export default function VerificationCenter({ user, kycTier, onClose, onVerified }) {
  const [limits, setLimits] = useState(null);
  const [file, setFile] = useState(null);
  const [consent, setConsent] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);
  const verified = kycTier >= 1;

  useEffect(() => { accountsApi.quote(100).then(setLimits).catch(() => {}); }, [kycTier]);

  const choose = event => {
    const chosen = event.target.files[0];
    setError("");
    if (!chosen) return setFile(null);
    if (!["image/jpeg", "image/png"].includes(chosen.type)) { setFile(null); return setError("Please choose a JPEG or PNG photo."); }
    if (chosen.size > MAX_BYTES) { setFile(null); return setError("The photo must be 5 MB or smaller."); }
    setFile(chosen);
  };

  const submit = async event => {
    event.preventDefault();
    if (!consent) return setError("Please agree to the identity check to continue.");
    if (!file) return setError("Take or choose a clear photo of your face.");
    setBusy(true);
    try {
      const status = await kycApi.verifyFace(file);
      setResult(status);
      if (status.tier >= 1) onVerified?.(status.tier);
    } catch (requestError) {
      setError(requestError.status === 503 ? "Identity verification isn’t available right now. Please try again later." : requestError.message);
    } finally { setBusy(false); }
  };

  return <div className="verification-dialog" role="dialog" aria-modal="true">
    <button className="verification-backdrop" onClick={onClose} />
    <section className="verification-window">
      <header className="verification-header"><div><span>ACCOUNT VERIFICATION</span><h2>Verify your identity</h2><p>Verify once to unlock higher transfer limits.</p></div><button onClick={onClose}>×</button></header>
      <div className="verification-layout">
        <aside className="tier-panel">
          <div className="tier-badge"><span>{kycTier}</span><div><small>CURRENT ACCOUNT LEVEL</small><strong>{verified ? "Verified" : "Not verified"}</strong><p>{user.firstName} {user.lastName}</p></div></div>
          <div className="tier-progress"><div><span>Verification progress</span><strong>{verified ? "1 of 1 complete" : "0 of 1 complete"}</strong></div><i><b style={{ width: verified ? "100%" : "0%" }} /></i></div>
          <dl>
            <div><dt>Daily transfer limit</dt><dd>{limits ? money.format(limits.dailyLimit) : "…"}</dd></div>
            <div><dt>Limit per transfer</dt><dd>{limits ? money.format(limits.perTransferLimit) : "…"}</dd></div>
            <div><dt>Account status</dt><dd className="active">Active</dd></div>
          </dl>
          <div className="tier-security"><span>✓</span><p><strong>Your information is protected</strong>We keep only the result of the check, never your photo.</p></div>
        </aside>
        <main className="verification-main">
          {result ? <section className="verification-review">
            <span className="review-clock">{result.faceStatus === "APPROVED" ? "✓" : "!"}</span>
            <small>{result.faceStatus === "APPROVED" ? "VERIFIED" : "NOT VERIFIED"}</small>
            <h3>{result.faceStatus === "APPROVED" ? "Your identity is verified" : "We couldn’t verify you"}</h3>
            <p>{result.faceStatus === "APPROVED" ? "Your transfer limits have been raised." : "Please try again with a clear, well-lit photo of your face."}</p>
            <button onClick={result.faceStatus === "APPROVED" ? onClose : () => { setResult(null); setFile(null); }}>{result.faceStatus === "APPROVED" ? "Return to dashboard" : "Try again"}</button>
          </section> : verified ? <section className="verification-review">
            <span className="review-clock">✓</span><small>VERIFIED</small><h3>You’re already verified</h3><p>You have the standard transfer limits. More checks, such as BVN or NIN, will unlock higher levels in a future update.</p><button onClick={onClose}>Return to dashboard</button>
          </section> : <form onSubmit={submit}>
            <div className="verification-intro"><h3>Selfie verification</h3><p>Take a clear photo of your face in good light. We use it once to confirm it’s you, and we don’t keep the photo.</p></div>
            <label className={`document-drop ${file ? "selected" : ""}`}>
              <input type="file" accept="image/jpeg,image/png" capture="user" onChange={choose} />
              <span><Icon name="phone" size={23} /></span><strong>{file ? file.name : "Take or choose a selfie"}</strong><small>{file ? "Ready to upload" : "JPEG or PNG · Maximum 5 MB"}</small><em>{file ? "Change photo" : "Browse"}</em>
            </label>
            <label className="consent"><input type="checkbox" checked={consent} onChange={e => { setConsent(e.target.checked); setError(""); }} /><span>I agree to DanBank checking my photo to verify my identity.</span></label>
            {error && <p className="payment-error">{error}</p>}
            <button className="settings-primary" type="submit" disabled={busy}>{busy ? "Checking…" : "Verify my identity"}</button>
          </form>}
        </main>
      </div>
    </section>
  </div>;
}
