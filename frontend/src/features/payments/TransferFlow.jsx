import { useEffect, useMemo, useRef, useState } from "react";
import Icon from "../../components/Icon.jsx";
import { accountsApi, beneficiariesApi, newIdempotencyKey } from "../../services/api.js";

const money = new Intl.NumberFormat("en-NG", { style: "currency", currency: "NGN", minimumFractionDigits: 2 });
const steps = ["Recipient", "Amount", "Review", "Authorize"];
const colors = ["mint", "blue", "plum"];
const initialsOf = name => name.split(" ").filter(Boolean).slice(0, 2).map(part => part[0]).join("").toUpperCase() || "?";
const maskNumber = number => `${number.slice(0, 3)}••••${number.slice(-3)}`;
const toRecipient = (person, index) => ({
  key: person.accountNumber,
  name: person.nickname || person.accountName || person.name,
  bank: "DanBank",
  accountNumber: person.accountNumber,
  account: maskNumber(person.accountNumber),
  initials: initialsOf(person.accountName || person.name || ""),
  color: colors[index % colors.length],
});

export default function TransferFlow({ account, onClose, onComplete, onSetupPin }) {
  const [stage, setStage] = useState(0);
  const [recipients, setRecipients] = useState([]);
  const [recipientsLoading, setRecipientsLoading] = useState(true);
  const [recipient, setRecipient] = useState(null);
  const [search, setSearch] = useState("");
  const [newNumber, setNewNumber] = useState("");
  const [showNew, setShowNew] = useState(false);
  const [saveRecipient, setSaveRecipient] = useState(false);
  const [lookingUp, setLookingUp] = useState(false);
  const [amount, setAmount] = useState("");
  const [note, setNote] = useState("");
  const [quote, setQuote] = useState(null);
  const [pin, setPin] = useState("");
  const [error, setError] = useState("");
  const [pinProblem, setPinProblem] = useState(false);
  const [status, setStatus] = useState("active");
  const [receipt, setReceipt] = useState(null);
  const idempotencyKey = useRef(newIdempotencyKey());

  const numericAmount = Number(amount.replace(/,/g, ""));
  const fee = quote?.fee ?? 0;
  const total = numericAmount + fee;
  const balance = account?.balance ?? 0;

  useEffect(() => {
    let active = true;
    beneficiariesApi.list()
      .then(list => { if (active) setRecipients(list.map(toRecipient)); })
      .catch(() => {})
      .finally(() => { if (active) setRecipientsLoading(false); });
    return () => { active = false; };
  }, []);

  const filteredRecipients = useMemo(
    () => recipients.filter(person => `${person.name} ${person.bank}`.toLowerCase().includes(search.toLowerCase())),
    [recipients, search],
  );

  const chooseRecipient = person => { setRecipient(person); setError(""); };

  const lookUp = async event => {
    event.preventDefault();
    if (!/^\d{10}$/.test(newNumber)) return setError("Enter the recipient's 10-digit account number.");
    if (newNumber === account?.number) return setError("You cannot send money to the account you are paying from.");
    setLookingUp(true); setError("");
    try {
      const found = await accountsApi.lookup(newNumber);
      chooseRecipient({ ...toRecipient({ accountNumber: found.accountNumber, accountName: found.accountName }, recipients.length), isNew: true });
      setShowNew(false);
    } catch (requestError) {
      setError(requestError.status === 404 ? "We could not find an account with that number." : requestError.message);
    } finally { setLookingUp(false); }
  };

  const nextFromRecipient = () => recipient ? setStage(1) : setError("Select a recipient to continue.");

  const nextFromAmount = async event => {
    event.preventDefault();
    if (!numericAmount || numericAmount < 100) return setError("Enter an amount of at least ₦100.");
    try {
      const result = await accountsApi.quote(numericAmount);
      if (numericAmount > result.perTransferLimit) {
        return setError(`Your limit is ${money.format(result.perTransferLimit)} per transfer${result.kycTier < 1 ? ". Verify your identity to raise it." : "."}`);
      }
      if (numericAmount + result.fee > balance) return setError("The transfer total is above your available balance.");
      setQuote(result); setError(""); setStage(2);
    } catch (requestError) { setError(requestError.message); }
  };

  const authorize = async event => {
    event.preventDefault();
    if (!/^\d{4}$/.test(pin)) return setError("Enter your complete 4-digit transaction PIN.");
    setError(""); setPinProblem(false); setStatus("processing");
    try {
      const result = await accountsApi.transfer(
        { fromAccountId: account.id, toAccountNumber: recipient.accountNumber, amount: numericAmount, description: note || undefined },
        pin,
        idempotencyKey.current,
      );
      const reference = result.data[0].reference.replace(/-OUT$/, "");
      setReceipt({ reference: `DB${reference.replace(/-/g, "").slice(0, 10).toUpperCase()}`, date: new Date() });
      setStatus("success");
      if (saveRecipient && recipient.isNew) beneficiariesApi.add(recipient.accountNumber).catch(() => {});
    } catch (requestError) {
      setStatus("active"); setPin("");
      if (requestError.status === 400 && /PIN/i.test(requestError.message)) setPinProblem(true);
      setError(requestError.status === 423
        ? "Too many wrong PIN attempts. Your PIN is locked for 15 minutes."
        : requestError.status === 403 ? "That PIN is incorrect. Please try again." : requestError.message);
    }
  };

  const editDetails = target => { idempotencyKey.current = newIdempotencyKey(); setStage(target); };
  const closeCompleted = () => { onComplete?.({ recipient, amount: numericAmount, note }); onClose(); };
  const [wholeBalance, balanceDecimal] = balance.toLocaleString("en-NG", { minimumFractionDigits: 2, maximumFractionDigits: 2 }).split(".");

  return <div className="payment-dialog" role="dialog" aria-modal="true" aria-labelledby="payment-title">
    <button className="payment-backdrop" onClick={onClose} aria-label="Close transfer" />
    <section className="payment-window">
      <aside className="payment-context">
        <div className="context-brand"><span className="brand-mark"><span /></span><b>DanBank</b></div>
        <div><span className="context-label">PAYMENT FROM</span><h3>{account?.name}</h3><p>{account?.number}</p></div>
        <div className="context-balance"><span>Available balance</span><strong>₦{wholeBalance}.{balanceDecimal}</strong></div>
        <div className="context-security"><span>✓</span><p><strong>Protected payment</strong>Your transfer is encrypted and requires your private transaction PIN.</p></div>
        <small>DanBank will never ask you to share your PIN by phone, email, or chat.</small>
      </aside>

      <main className="payment-main">
        <header className="payment-header"><div><span>NEW BANK TRANSFER</span><h2 id="payment-title">{status === "success" ? "Payment confirmed" : "Send money"}</h2></div><button onClick={onClose} aria-label="Close">×</button></header>

        {status === "active" && <>
          <ol className="payment-steps">{steps.map((label, index) => <li key={label} className={index < stage ? "complete" : index === stage ? "current" : ""}><span>{index < stage ? "✓" : index + 1}</span><small>{label}</small></li>)}</ol>

          {stage === 0 && <section className="payment-stage">
            <div className="stage-heading"><h3>Who are you paying?</h3><p>Select a saved recipient or enter a DanBank account number.</p></div>
            {showNew
              ? <form onSubmit={lookUp}>
                  <label className="payment-field">Account number<input value={newNumber} onChange={e => { setNewNumber(e.target.value.replace(/\D/g, "").slice(0, 10)); setError(""); }} inputMode="numeric" placeholder="10-digit account number" autoFocus /></label>
                  <div className="payment-actions"><button type="button" className="payment-secondary" onClick={() => { setShowNew(false); setError(""); }}>Cancel</button><button className="payment-primary" type="submit" disabled={lookingUp}>{lookingUp ? "Checking…" : "Find account"}</button></div>
                </form>
              : <>
                  <div className="recipient-search"><Icon name="search" size={18} /><input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search saved recipients" autoFocus /></div>
                  <div className="recipient-heading"><span>SAVED RECIPIENTS</span><button onClick={() => { setShowNew(true); setError(""); }}>+ New recipient</button></div>
                  <div className="recipient-rows">
                    {recipient?.isNew && <button className="selected" onClick={() => setRecipient(null)}><span className={`recipient-avatar ${recipient.color}`}>{recipient.initials}</span><span className="recipient-details"><strong>{recipient.name}</strong><small>{recipient.bank} &nbsp;•&nbsp; {recipient.account}</small></span><span className="recipient-select">✓</span></button>}
                    {recipientsLoading && <p className="payment-hint">Loading your recipients…</p>}
                    {!recipientsLoading && !filteredRecipients.length && !recipient?.isNew && <p className="payment-hint">No saved recipients yet. Use “New recipient” to pay someone by account number.</p>}
                    {filteredRecipients.map(person => <button key={person.key} className={recipient?.key === person.key ? "selected" : ""} onClick={() => chooseRecipient(person)}><span className={`recipient-avatar ${person.color}`}>{person.initials}</span><span className="recipient-details"><strong>{person.name}</strong><small>{person.bank} &nbsp;•&nbsp; {person.account}</small></span><span className="recipient-select">{recipient?.key === person.key ? "✓" : ""}</span></button>)}
                  </div>
                  {recipient?.isNew && <label className="consent"><input type="checkbox" checked={saveRecipient} onChange={e => setSaveRecipient(e.target.checked)} /><span>Save {recipient.name} to my recipients</span></label>}
                </>}
            {error && <p className="payment-error">{error}</p>}
            {!showNew && <button className="payment-primary" onClick={nextFromRecipient}>Continue to amount <Icon name="chevron" size={18} /></button>}
          </section>}

          {stage === 1 && <form className="payment-stage" onSubmit={nextFromAmount}>
            <div className="stage-heading"><h3>How much are you sending?</h3><p>To <strong>{recipient.name}</strong> · {recipient.bank}</p></div>
            <div className="payment-amount"><label>AMOUNT</label><div><span>₦</span><input value={amount} onChange={e => { setAmount(e.target.value.replace(/[^0-9.]/g, "")); setError(""); }} inputMode="decimal" placeholder="0.00" autoFocus /></div><small>Available: {money.format(balance)}</small></div>
            <label className="payment-field">Narration <span>Optional</span><input value={note} onChange={e => setNote(e.target.value)} maxLength={50} placeholder="What is this payment for?" /><small>{note.length}/50 characters</small></label>
            {error && <p className="payment-error">{error}</p>}
            <div className="payment-actions"><button type="button" className="payment-secondary" onClick={() => setStage(0)}>Back</button><button className="payment-primary" type="submit">Review transfer <Icon name="chevron" size={18} /></button></div>
          </form>}

          {stage === 2 && <section className="payment-stage review-stage">
            <div className="stage-heading"><h3>Review your transfer</h3><p>Confirm every detail. Completed transfers may not be reversible.</p></div>
            <div className="review-hero"><span className={`recipient-avatar ${recipient.color}`}>{recipient.initials}</span><p>YOU ARE SENDING</p><strong>{money.format(numericAmount)}</strong><small>to {recipient.name}</small></div>
            <dl className="payment-summary">
              <div><dt>Recipient</dt><dd>{recipient.name}</dd></div>
              <div><dt>Receiving bank</dt><dd>{recipient.bank}</dd></div>
              <div><dt>Account number</dt><dd>{recipient.account}</dd></div>
              <div><dt>Narration</dt><dd>{note || "Bank transfer"}</dd></div>
              <div><dt>Transfer fee</dt><dd>{money.format(fee)}</dd></div>
              <div className="summary-total"><dt>Total debit</dt><dd>{money.format(total)}</dd></div>
            </dl>
            <div className="intent-warning"><span>!</span><p>Check the recipient and amount carefully before authorizing this payment.</p></div>
            <div className="payment-actions"><button className="payment-secondary" onClick={() => editDetails(1)}>Edit details</button><button className="payment-primary" onClick={() => setStage(3)}>Confirm details <Icon name="chevron" size={18} /></button></div>
          </section>}

          {stage === 3 && <form className="payment-stage authorize-stage" onSubmit={authorize}>
            <div className="authorize-lock"><span>✓</span></div>
            <div className="stage-heading"><h3>Authorize securely</h3><p>Enter your 4-digit transaction PIN to approve <strong>{money.format(total)}</strong>.</p></div>
            <div className="pin-dots" onClick={() => document.getElementById("transaction-pin")?.focus()}>{[0, 1, 2, 3].map(index => <span key={index} className={pin.length > index ? "filled" : ""} />)}</div>
            <input id="transaction-pin" className="hidden-pin" value={pin} onChange={e => { setPin(e.target.value.replace(/\D/g, "").slice(0, 4)); setError(""); }} inputMode="numeric" type="password" autoComplete="off" autoFocus />
            {error && <p className="payment-error">{error}</p>}
            {pinProblem && <button className="payment-text" type="button" onClick={() => { onClose(); onSetupPin?.(); }}>Set up my transaction PIN</button>}
            <button className="payment-primary" type="submit">Authorize transfer</button>
            <button className="payment-text" type="button" onClick={() => setStage(2)}>Back to review</button>
            <p className="pin-privacy">Your PIN is sent over an encrypted connection and is never stored in readable form.</p>
          </form>}
        </>}

        {status === "processing" && <section className="payment-state"><span className="secure-loader"><i /></span><h3>Securing your payment</h3><p>We’re confirming the transaction. Please keep this window open.</p><div className="processing-steps"><span className="done">Details verified</span><span className="active">Authorizing transfer</span><span>Confirming payment</span></div></section>}

        {status === "success" && <section className="payment-state success-state"><span className="confirmed-mark">✓</span><p className="confirmed-label">TRANSFER SUCCESSFUL</p><h3>{money.format(numericAmount)}</h3><p>sent successfully to <strong>{recipient.name}</strong></p><div className="success-divider" />
          <dl className="success-receipt">
            <div><dt>Recipient</dt><dd>{recipient.name}</dd></div>
            <div><dt>Bank</dt><dd>{recipient.bank}</dd></div>
            <div><dt>Reference</dt><dd>{receipt.reference}</dd></div>
            <div><dt>Date</dt><dd>{receipt.date.toLocaleString("en-NG", { dateStyle: "medium", timeStyle: "short" })}</dd></div>
            <div><dt>Status</dt><dd className="success-text">Successful</dd></div>
          </dl>
          <button className="payment-primary" onClick={closeCompleted}>Return to dashboard</button><button className="payment-text" onClick={() => window.print()}>Download or print receipt</button></section>}
      </main>
    </section>
  </div>;
}
