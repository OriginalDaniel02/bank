import { useEffect, useMemo, useState } from "react";
import Icon from "../../components/Icon.jsx";

const savedRecipients = [
  { id: 1, name: "Sarah James", bank: "Access Bank", account: "0124  ••••  4082", initials: "SJ", color: "mint" },
  { id: 2, name: "Michael Okoro", bank: "GTBank", account: "0217  ••••  9271", initials: "MO", color: "blue" },
  { id: 3, name: "Ada Nwosu", bank: "Zenith Bank", account: "1008  ••••  1356", initials: "AN", color: "plum" },
];

const money = new Intl.NumberFormat("en-NG", { style: "currency", currency: "NGN", minimumFractionDigits: 2 });
const steps = ["Recipient", "Amount", "Review", "Authorize"];

export default function TransferFlow({ onClose, onComplete }) {
  const [stage, setStage] = useState(0);
  const [recipient, setRecipient] = useState(null);
  const [search, setSearch] = useState("");
  const [amount, setAmount] = useState("");
  const [note, setNote] = useState("");
  const [pin, setPin] = useState("");
  const [error, setError] = useState("");
  const [status, setStatus] = useState("active");
  const numericAmount = Number(amount.replace(/,/g, ""));
  const total = numericAmount + 10;
  const filteredRecipients = useMemo(() => savedRecipients.filter(person => `${person.name} ${person.bank}`.toLowerCase().includes(search.toLowerCase())), [search]);

  useEffect(() => {
    if (status !== "processing") return;
    const timer = setTimeout(() => setStatus("success"), 1500);
    return () => clearTimeout(timer);
  }, [status]);

  const nextFromRecipient = () => recipient ? setStage(1) : setError("Select a recipient to continue.");
  const nextFromAmount = event => {
    event.preventDefault();
    if (!numericAmount || numericAmount < 100) return setError("Enter an amount of at least ₦100.");
    if (total > 2580420.75) return setError("The transfer total is above your available balance.");
    setError(""); setStage(2);
  };
  const authorize = event => {
    event.preventDefault();
    if (!/^\d{4}$/.test(pin)) return setError("Enter your complete 4-digit transaction PIN.");
    setError(""); setStatus("processing");
  };
  const closeCompleted = () => { onComplete?.({ recipient, amount: numericAmount, note }); onClose(); };

  return <div className="payment-dialog" role="dialog" aria-modal="true" aria-labelledby="payment-title">
    <button className="payment-backdrop" onClick={onClose} aria-label="Close transfer" />
    <section className="payment-window">
      <aside className="payment-context">
        <div className="context-brand"><span className="brand-mark"><span /></span><b>DanBank</b></div>
        <div><span className="context-label">PAYMENT FROM</span><h3>Personal Savings</h3><p>0123456789</p></div>
        <div className="context-balance"><span>Available balance</span><strong>₦2,580,420.75</strong></div>
        <div className="context-security"><span>✓</span><p><strong>Protected payment</strong>Your transfer is encrypted and requires your private transaction PIN.</p></div>
        <small>DanBank will never ask you to share your PIN by phone, email, or chat.</small>
      </aside>

      <main className="payment-main">
        <header className="payment-header"><div><span>NEW BANK TRANSFER</span><h2 id="payment-title">{status === "success" ? "Payment confirmed" : "Send money"}</h2></div><button onClick={onClose} aria-label="Close">×</button></header>

        {status === "active" && <>
          <ol className="payment-steps">{steps.map((label,index) => <li key={label} className={index < stage ? "complete" : index === stage ? "current" : ""}><span>{index < stage ? "✓" : index + 1}</span><small>{label}</small></li>)}</ol>

          {stage === 0 && <section className="payment-stage"><div className="stage-heading"><h3>Who are you paying?</h3><p>Select a saved recipient or search by name or bank.</p></div><div className="recipient-search"><Icon name="search" size={18}/><input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search saved recipients" autoFocus/></div><div className="recipient-heading"><span>SAVED RECIPIENTS</span><button>+ New recipient</button></div><div className="recipient-rows">{filteredRecipients.map(person => <button key={person.id} className={recipient?.id === person.id ? "selected" : ""} onClick={() => { setRecipient(person); setError(""); }}><span className={`recipient-avatar ${person.color}`}>{person.initials}</span><span className="recipient-details"><strong>{person.name}</strong><small>{person.bank} &nbsp;•&nbsp; {person.account}</small></span><span className="recipient-select">{recipient?.id === person.id ? "✓" : ""}</span></button>)}</div>{error && <p className="payment-error">{error}</p>}<button className="payment-primary" onClick={nextFromRecipient}>Continue to amount <Icon name="chevron" size={18}/></button></section>}

          {stage === 1 && <form className="payment-stage" onSubmit={nextFromAmount}><div className="stage-heading"><h3>How much are you sending?</h3><p>To <strong>{recipient.name}</strong> · {recipient.bank}</p></div><div className="payment-amount"><label>AMOUNT</label><div><span>₦</span><input value={amount} onChange={e => { setAmount(e.target.value.replace(/[^0-9.]/g,"")); setError(""); }} inputMode="decimal" placeholder="0.00" autoFocus/></div><small>Available: ₦2,580,420.75</small></div><label className="payment-field">Narration <span>Optional</span><input value={note} onChange={e => setNote(e.target.value)} maxLength={50} placeholder="What is this payment for?"/><small>{note.length}/50 characters</small></label><div className="fee-preview"><span>Transfer fee</span><strong>₦10.00</strong></div>{error && <p className="payment-error">{error}</p>}<div className="payment-actions"><button type="button" className="payment-secondary" onClick={() => setStage(0)}>Back</button><button className="payment-primary" type="submit">Review transfer <Icon name="chevron" size={18}/></button></div></form>}

          {stage === 2 && <section className="payment-stage review-stage"><div className="stage-heading"><h3>Review your transfer</h3><p>Confirm every detail. Completed transfers may not be reversible.</p></div><div className="review-hero"><span className={`recipient-avatar ${recipient.color}`}>{recipient.initials}</span><p>YOU ARE SENDING</p><strong>{money.format(numericAmount)}</strong><small>to {recipient.name}</small></div><dl className="payment-summary"><div><dt>Recipient</dt><dd>{recipient.name}</dd></div><div><dt>Receiving bank</dt><dd>{recipient.bank}</dd></div><div><dt>Account number</dt><dd>{recipient.account}</dd></div><div><dt>Narration</dt><dd>{note || "Bank transfer"}</dd></div><div><dt>Transfer fee</dt><dd>₦10.00</dd></div><div className="summary-total"><dt>Total debit</dt><dd>{money.format(total)}</dd></div></dl><div className="intent-warning"><span>!</span><p>Check the recipient and amount carefully before authorizing this payment.</p></div><div className="payment-actions"><button className="payment-secondary" onClick={() => setStage(1)}>Edit details</button><button className="payment-primary" onClick={() => setStage(3)}>Confirm details <Icon name="chevron" size={18}/></button></div></section>}

          {stage === 3 && <form className="payment-stage authorize-stage" onSubmit={authorize}><div className="authorize-lock"><span>✓</span></div><div className="stage-heading"><h3>Authorize securely</h3><p>Enter your 4-digit transaction PIN to approve <strong>{money.format(total)}</strong>.</p></div><div className="pin-dots" onClick={() => document.getElementById("transaction-pin")?.focus()}>{[0,1,2,3].map(index => <span key={index} className={pin.length > index ? "filled" : ""}/>)}</div><input id="transaction-pin" className="hidden-pin" value={pin} onChange={e => { setPin(e.target.value.replace(/\D/g,"").slice(0,4)); setError(""); }} inputMode="numeric" type="password" autoFocus/>{error && <p className="payment-error">{error}</p>}<button className="payment-primary" type="submit">Authorize transfer</button><button className="payment-text" type="button" onClick={() => setStage(2)}>Back to review</button><p className="pin-privacy">Your PIN is encrypted and is never displayed or stored in this prototype.</p></form>}
        </>}

        {status === "processing" && <section className="payment-state"><span className="secure-loader"><i/></span><h3>Securing your payment</h3><p>We’re confirming the transaction. Please keep this window open.</p><div className="processing-steps"><span className="done">Details verified</span><span className="active">Authorizing transfer</span><span>Confirming payment</span></div></section>}

        {status === "success" && <section className="payment-state success-state"><span className="confirmed-mark">✓</span><p className="confirmed-label">TRANSFER SUCCESSFUL</p><h3>{money.format(numericAmount)}</h3><p>sent successfully to <strong>{recipient.name}</strong></p><div className="success-divider"/><dl className="success-receipt"><div><dt>Recipient</dt><dd>{recipient.name}</dd></div><div><dt>Bank</dt><dd>{recipient.bank}</dd></div><div><dt>Reference</dt><dd>DB{Date.now().toString().slice(-9)}</dd></div><div><dt>Date</dt><dd>{new Date().toLocaleString("en-NG",{dateStyle:"medium",timeStyle:"short"})}</dd></div><div><dt>Status</dt><dd className="success-text">Successful</dd></div></dl><button className="payment-primary" onClick={closeCompleted}>Return to dashboard</button><button className="payment-text" onClick={() => window.print()}>Download or print receipt</button></section>}
      </main>
    </section>
  </div>;
}
