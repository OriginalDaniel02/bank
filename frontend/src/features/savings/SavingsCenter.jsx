import { useEffect, useState } from "react";
import Icon from "../../components/Icon.jsx";
import { savingsApi } from "../../services/api.js";

const money = new Intl.NumberFormat("en-NG", { style: "currency", currency: "NGN", maximumFractionDigits: 0 });
const money2 = new Intl.NumberFormat("en-NG", { style: "currency", currency: "NGN", minimumFractionDigits: 2 });
const colors = ["teal", "blue"];
const dateLabel = value => new Date(`${value}T00:00:00`).toLocaleDateString("en-NG", { day: "numeric", month: "short", year: "numeric" });
const digits = value => value.replace(/\D/g, "");

export default function SavingsCenter({ account, onClose, onChanged }) {
  const [data, setData] = useState({ goals: [], totalSaved: 0 });
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [dialog, setDialog] = useState(null);
  const [notice, setNotice] = useState("");
  const notify = text => { setNotice(text); setTimeout(() => setNotice(""), 3000); };

  const load = () => savingsApi.list().then(setData).catch(error => setLoadError(error.message)).finally(() => setLoading(false));
  useEffect(() => { load(); }, []);
  const changed = message => { setDialog(null); notify(message); load(); onChanged?.(); };
  const locked = data.goals.filter(goal => goal.withdrawalsLocked).length;

  return <div className="savings-dialog" role="dialog" aria-modal="true">
    <button className="savings-backdrop" onClick={onClose} />
    <section className="savings-window">
      <header className="savings-header"><div><span>SAVINGS</span><h2>Grow your money</h2><p>Set money aside for the things that matter.</p></div><button onClick={onClose}>×</button></header>
      <section className="savings-summary">
        <div><small>TOTAL SAVED</small><strong>{money.format(data.totalSaved)}</strong><p>Across {data.goals.length} active {data.goals.length === 1 ? "goal" : "goals"}</p></div>
        <div className="savings-stat"><span>↓</span><p><strong>{money.format(account.balance)}</strong>Available to save</p></div>
        <div className="savings-stat"><span>✓</span><p><strong>{locked} locked</strong>Until their target date</p></div>
        <button onClick={() => setDialog({ type: "create" })}><Icon name="plus" size={17} /> New savings goal</button>
      </section>
      <main className="savings-main">
        <div className="savings-heading"><div><h3>Your goals</h3><p>Saved money is set aside and leaves your available balance.</p></div></div>
        <div className="goal-grid">
          {data.goals.map((goal, index) => <article key={goal.id}>
            <div className="goal-top"><span className={`goal-icon ${colors[index % 2]}`}>{goal.type === "LOCKED" ? "⌂" : "✦"}</span><div><small>{goal.type} SAVINGS</small><h4>{goal.name}</h4></div><button onClick={() => setDialog({ type: "manage", goal })}>•••</button></div>
            <div className="goal-amount"><strong>{money2.format(goal.savedAmount)}</strong><span>of {money.format(goal.targetAmount)}</span></div>
            <div className="goal-track"><i style={{ width: `${goal.progressPercent}%` }} /></div>
            <div className="goal-meta"><span>{goal.progressPercent}% complete</span><span>{goal.deadline ? `Target · ${dateLabel(goal.deadline)}` : "No target date"}</span></div>
            <div className="goal-actions"><button onClick={() => setDialog({ type: "add", goal })}>Add money</button><button onClick={() => setDialog({ type: "manage", goal })}>Manage</button></div>
          </article>)}
        </div>
        {loading && <p className="payment-hint">Loading your goals…</p>}
        {loadError && <p className="payment-error">{loadError}</p>}
        {!loading && !loadError && !data.goals.length && <div className="savings-note"><span>i</span><p><strong>No savings goals yet</strong>Create one to start setting money aside.</p></div>}
        <div className="savings-note"><span>i</span><p><strong>Save at your own pace</strong>Flexible savings can be withdrawn any time. Locked savings stay put until their target date. Withdrawals need your transaction PIN.</p></div>
      </main>
      {notice && <button className="savings-notice" onClick={() => setNotice("")}>✓ {notice}<span>×</span></button>}
    </section>
    {dialog?.type === "create" && <CreateGoal onClose={() => setDialog(null)} onSaved={() => changed("Savings goal created.")} />}
    {dialog?.type === "add" && <AddSavings goal={dialog.goal} account={account} onClose={() => setDialog(null)} onSaved={() => changed("Money added to your goal.")} />}
    {dialog?.type === "manage" && <ManageGoal goal={dialog.goal} account={account} onClose={() => setDialog(null)} onChanged={changed} />}
  </div>;
}

function Sheet({ title, subtitle, onClose, children }) {
  return <div className="savings-layer"><button onClick={onClose} /><aside><header><div><span>SAVINGS</span><h3>{title}</h3><p>{subtitle}</p></div><button onClick={onClose}>×</button></header>{children}</aside></div>;
}

function CreateGoal({ onClose, onSaved }) {
  const [form, setForm] = useState({ name: "", type: "FLEXIBLE", target: "", deadline: "" });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const update = event => { setForm(v => ({ ...v, [event.target.name]: event.target.value })); setError(""); };
  const tomorrow = new Date(Date.now() + 86400000).toISOString().slice(0, 10);

  const submit = async event => {
    event.preventDefault();
    if (!form.name.trim() || Number(form.target) < 1000) return setError("Enter a goal name and a target of at least ₦1,000.");
    if (form.type === "LOCKED" && !form.deadline) return setError("A locked goal needs a target date.");
    setBusy(true);
    try { await savingsApi.create({ name: form.name.trim(), type: form.type, targetAmount: Number(form.target), deadline: form.deadline || null }); onSaved(); }
    catch (requestError) { setError(requestError.message); setBusy(false); }
  };

  return <Sheet title="Create a savings goal" subtitle="Set a target and choose how it behaves." onClose={onClose}>
    <form className="savings-form" onSubmit={submit}>
      <label>Goal name<input name="name" value={form.name} onChange={update} maxLength={50} placeholder="e.g. Education fund" autoFocus /></label>
      <div className="saving-type">
        <button type="button" className={form.type === "FLEXIBLE" ? "active" : ""} onClick={() => setForm(v => ({ ...v, type: "FLEXIBLE" }))}><strong>Flexible</strong><small>Withdraw whenever needed</small></button>
        <button type="button" className={form.type === "LOCKED" ? "active" : ""} onClick={() => setForm(v => ({ ...v, type: "LOCKED" }))}><strong>Locked</strong><small>Commit until your date</small></button>
      </div>
      <label>Target amount<div className="saving-money"><span>₦</span><input value={form.target} onChange={e => { setForm(v => ({ ...v, target: digits(e.target.value) })); setError(""); }} inputMode="numeric" placeholder="0" /></div></label>
      <label>Target date {form.type === "FLEXIBLE" && <span>(optional)</span>}<input name="deadline" type="date" min={tomorrow} value={form.deadline} onChange={update} /></label>
      {error && <p className="payment-error">{error}</p>}
      <button className="savings-primary" disabled={busy}>{busy ? "Creating…" : "Create goal"}</button>
    </form>
  </Sheet>;
}

function AddSavings({ goal, account, onClose, onSaved }) {
  const [amount, setAmount] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const submit = async () => {
    if (Number(amount) < 100) return setError("The least you can add is ₦100.");
    if (Number(amount) > account.balance) return setError("That is more than your available balance.");
    setBusy(true);
    try { await savingsApi.deposit(goal.id, account.id, Number(amount)); onSaved(); }
    catch (requestError) { setError(requestError.message); setBusy(false); }
  };
  return <Sheet title={`Add to ${goal.name}`} subtitle={`Available balance: ${money2.format(account.balance)}`} onClose={onClose}>
    <div className="add-savings">
      <label>Amount<div><span>₦</span><input value={amount} onChange={e => { setAmount(digits(e.target.value)); setError(""); }} inputMode="numeric" placeholder="0.00" autoFocus /></div></label>
      <section>{[1000, 5000, 10000, 50000].map(v => <button key={v} onClick={() => { setAmount(String(v)); setError(""); }}>{money.format(v)}</button>)}</section>
      <div className="saving-destination"><span>{goal.type === "LOCKED" ? "⌂" : "✦"}</span><p><strong>{goal.name}</strong>{money2.format(goal.savedAmount)} currently saved</p></div>
      {error && <p className="payment-error">{error}</p>}
      <button className="savings-primary" onClick={submit} disabled={busy}>{busy ? "Adding…" : "Add money"}</button>
    </div>
  </Sheet>;
}

function ManageGoal({ goal, account, onClose, onChanged }) {
  const [view, setView] = useState("menu");
  const [value, setValue] = useState("");
  const [pin, setPin] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const go = next => { setView(next); setValue(""); setPin(""); setError(""); };

  const run = async (action, message) => {
    setBusy(true);
    try { await action(); onChanged(message); }
    catch (requestError) {
      setBusy(false); setPin("");
      setError(requestError.status === 403 ? "That PIN is incorrect." : requestError.status === 423 ? "Your PIN is locked for 15 minutes after too many wrong attempts."
        : requestError.status === 400 && /PIN/i.test(requestError.message) ? "Set up your transaction PIN in Settings first." : requestError.message);
    }
  };
  const pinOk = () => { if (!/^\d{4}$/.test(pin)) { setError("Enter your 4-digit transaction PIN."); return false; } return true; };

  const unlockText = goal.withdrawalsLocked ? `Locked until ${dateLabel(goal.deadline)}` : "Move money to your account";
  return <Sheet title="Manage savings goal" subtitle={goal.name} onClose={onClose}>
    <div className="manage-saving">
      <div className="manage-balance"><small>CURRENTLY SAVED</small><strong>{money2.format(goal.savedAmount)}</strong><span>{goal.type} savings · target {money.format(goal.targetAmount)}</span></div>
      {view === "menu" && <>
        <button onClick={() => go("target")}><span><strong>Change target</strong><small>Currently {money.format(goal.targetAmount)}</small></span><Icon name="chevron" size={16} /></button>
        <button className="withdraw" onClick={() => !goal.withdrawalsLocked && go("withdraw")} disabled={goal.withdrawalsLocked}><span><strong>Withdraw savings</strong><small>{unlockText}</small></span><Icon name="chevron" size={16} /></button>
        <button className="close-goal" onClick={() => !goal.withdrawalsLocked || goal.savedAmount === 0 ? go("close") : setError(`This goal is locked until ${dateLabel(goal.deadline)}.`)}>Close this savings goal</button>
        {error && <p className="payment-error">{error}</p>}
      </>}
      {view === "target" && <form className="savings-form" onSubmit={e => { e.preventDefault(); if (Number(value) < 1000) return setError("The target must be at least ₦1,000."); run(() => savingsApi.changeTarget(goal.id, Number(value)), "Target updated."); }}>
        <label>New target<div className="saving-money"><span>₦</span><input value={value} onChange={e => { setValue(digits(e.target.value)); setError(""); }} inputMode="numeric" autoFocus /></div></label>
        {error && <p className="payment-error">{error}</p>}
        <button className="savings-primary" disabled={busy}>Save target</button><button type="button" className="payment-text" onClick={() => go("menu")}>Back</button>
      </form>}
      {view === "withdraw" && <form className="savings-form" onSubmit={e => { e.preventDefault(); if (Number(value) < 100) return setError("The least you can withdraw is ₦100."); if (Number(value) > goal.savedAmount) return setError("That is more than you have saved here."); if (pinOk()) run(() => savingsApi.withdraw(goal.id, account.id, Number(value), pin), "Withdrawn to your account."); }}>
        <label>Amount<div className="saving-money"><span>₦</span><input value={value} onChange={e => { setValue(digits(e.target.value)); setError(""); }} inputMode="numeric" autoFocus /></div></label>
        <label>Transaction PIN<input type="password" inputMode="numeric" autoComplete="off" value={pin} onChange={e => { setPin(digits(e.target.value).slice(0, 4)); setError(""); }} /></label>
        {error && <p className="payment-error">{error}</p>}
        <button className="savings-primary" disabled={busy}>{busy ? "Withdrawing…" : "Withdraw"}</button><button type="button" className="payment-text" onClick={() => go("menu")}>Back</button>
      </form>}
      {view === "close" && <form className="savings-form" onSubmit={e => { e.preventDefault(); if (pinOk()) run(() => savingsApi.close(goal.id, account.id, pin), "Savings goal closed."); }}>
        <p>{goal.savedAmount > 0 ? `${money2.format(goal.savedAmount)} will be returned to ${account.name}.` : "This goal has nothing saved in it."} Closing a goal can’t be undone.</p>
        <label>Transaction PIN<input type="password" inputMode="numeric" autoComplete="off" value={pin} onChange={e => { setPin(digits(e.target.value).slice(0, 4)); setError(""); }} autoFocus /></label>
        {error && <p className="payment-error">{error}</p>}
        <button className="savings-primary" disabled={busy}>{busy ? "Closing…" : "Close goal"}</button><button type="button" className="payment-text" onClick={() => go("menu")}>Keep goal</button>
      </form>}
    </div>
  </Sheet>;
}
