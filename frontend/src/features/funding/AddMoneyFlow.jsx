import { useState } from "react";
import Icon from "../../components/Icon.jsx";
import { depositsApi } from "../../services/api.js";

const money = new Intl.NumberFormat("en-NG", { style: "currency", currency: "NGN", minimumFractionDigits: 2 });
const quickAmounts = [5000, 10000, 25000, 50000];
export const PENDING_DEPOSIT_KEY = "pendingDeposit";

export default function AddMoneyFlow({ account, kycTier, onClose, onVerify }) {
  const [amount, setAmount] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState("");
  const numeric = Number(amount);

  const copy = async (value, label) => {
    try { await navigator.clipboard.writeText(value); setCopied(label); }
    catch { setCopied("Copy unavailable"); }
    setTimeout(() => setCopied(""), 1800);
  };

  const pay = async event => {
    event.preventDefault();
    if (!numeric || numeric < 100) return setError("Enter an amount of at least ₦100.");
    setBusy(true); setError("");
    try {
      const started = await depositsApi.start(account.id, numeric);
      // Only the reference is remembered (never a token) so we can confirm the payment when you come back.
      sessionStorage.setItem(PENDING_DEPOSIT_KEY, started.reference);
      window.location.assign(started.authorizationUrl);
    } catch (requestError) {
      setBusy(false);
      setError(requestError.status === 503 ? "Adding money by card isn’t available yet. Please use a bank transfer for now." : requestError.message);
    }
  };

  return <div className="funding-dialog" role="dialog" aria-modal="true">
    <button className="funding-backdrop" onClick={onClose} />
    <section className="funding-window">
      <header className="funding-header"><div><span>FUND YOUR ACCOUNT</span><h2>Add money</h2><p>Pay with your card, or receive a bank transfer.</p></div><button onClick={onClose}>×</button></header>
      <div className="funding-account"><div><span>Money will be added to</span><strong>{account.name} · {account.number.slice(-4)}</strong></div><b>{money.format(account.balance)}</b></div>

      <form className="card-funding-form" onSubmit={pay}>
        <div className="funding-stage-title"><h3>Add money with your card</h3><p>You’ll pay on Paystack’s secure page. DanBank never sees your card details.</p></div>
        <div className="funding-amount">
          <label>AMOUNT TO ADD</label>
          <div><span>₦</span><input value={amount} onChange={e => { setAmount(e.target.value.replace(/[^0-9.]/g, "")); setError(""); }} inputMode="decimal" placeholder="0.00" autoFocus /></div>
          <section>{quickAmounts.map(value => <button type="button" key={value} onClick={() => { setAmount(String(value)); setError(""); }}>₦{value.toLocaleString()}</button>)}</section>
          <small>Minimum ₦100 · {kycTier >= 1 ? "Maximum ₦1,000,000" : "Maximum ₦50,000 until you verify your identity"}</small>
        </div>
        {error && <p className="payment-error">{error}</p>}
        {kycTier < 1 && <button type="button" className="funding-text" onClick={() => { onClose(); onVerify?.(); }}>Verify my identity to add more</button>}
        <button className="funding-primary" type="submit" disabled={busy}>{busy ? "Taking you to Paystack…" : <>Pay securely with Paystack <Icon name="chevron" size={18} /></>}</button>
      </form>

      <section className="transfer-funding">
        <div className="funding-badge"><Icon name="transfer" size={21} /></div>
        <h3>Or receive a bank transfer</h3>
        <p>Anyone with a DanBank account can send money to the details below. Your balance updates as soon as the transfer completes.</p>
        <dl>
          <div><dt>Bank name</dt><dd>DanBank <button onClick={() => copy("DanBank", "Bank name")}>▣</button></dd></div>
          <div><dt>Account number</dt><dd className="funding-number">{account.number} <button onClick={() => copy(account.number, "Account number")}>▣</button></dd></div>
          <div><dt>Account name</dt><dd>{account.name.toUpperCase()} <button onClick={() => copy(account.name.toUpperCase(), "Account name")}>▣</button></dd></div>
        </dl>
        {copied && <div className="copy-notice">✓ {copied} copied</div>}
      </section>
    </section>
  </div>;
}
