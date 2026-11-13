import { useEffect, useMemo, useState } from "react";
import Icon from "../../components/Icon.jsx";
import { accountsApi, beneficiariesApi } from "../../services/api.js";

const colors = ["mint", "blue", "plum", "orange"];
const initialsOf = name => name.split(" ").filter(Boolean).slice(0, 2).map(word => word[0]).join("").toUpperCase() || "?";
const mask = value => `${value.slice(0, 4)}  ••••  ${value.slice(-3)}`;
const toPerson = (item, index) => ({
  id: item.id, name: item.accountName, nickname: item.nickname || item.accountName.split(" ")[0],
  bank: "DanBank", account: item.accountNumber, initials: initialsOf(item.accountName), color: colors[index % colors.length],
});

export default function BeneficiaryManagement({ onClose, onSendMoney }) {
  const [people, setPeople] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [query, setQuery] = useState("");
  const [editor, setEditor] = useState(null);
  const [remove, setRemove] = useState(null);
  const [notice, setNotice] = useState("");

  const load = () => beneficiariesApi.list()
    .then(list => setPeople(list.map(toPerson)))
    .catch(error => setLoadError(error.message))
    .finally(() => setLoading(false));
  useEffect(() => { load(); }, []);

  const visible = useMemo(() => people.filter(person => `${person.name} ${person.nickname} ${person.bank} ${person.account}`.toLowerCase().includes(query.toLowerCase())), [people, query]);
  const notify = message => { setNotice(message); setTimeout(() => setNotice(""), 2800); };
  const saved = () => { setEditor(null); notify("Beneficiary added successfully."); load(); };
  const removePerson = async () => {
    try { await beneficiariesApi.remove(remove.id); setPeople(value => value.filter(person => person.id !== remove.id)); notify("Beneficiary removed."); }
    catch (error) { notify(error.message); }
    setRemove(null);
  };

  return <div className="beneficiary-dialog" role="dialog" aria-modal="true">
    <button className="beneficiary-backdrop" onClick={onClose} />
    <section className="beneficiary-window">
      <header className="beneficiary-header"><div><span>PAYMENTS</span><h2>Beneficiaries</h2><p>Manage the people and accounts you pay regularly.</p></div><button onClick={onClose}>×</button></header>
      <div className="beneficiary-toolbar"><div className="beneficiary-search"><Icon name="search" size={18} /><input value={query} onChange={e => setQuery(e.target.value)} placeholder="Search name or account" /></div><button className="add-beneficiary" onClick={() => setEditor({ mode: "add" })}><Icon name="plus" size={17} /> Add beneficiary</button></div>
      <div className="beneficiary-filters"><button className="active">All beneficiaries <span>{people.length}</span></button></div>
      <div className="beneficiary-list">
        <div className="beneficiary-list-heading"><span /><span /><span>BENEFICIARY</span><span>BANK ACCOUNT</span><span>ACTIONS</span></div>
        {visible.map(person => <article key={person.id}>
          <span />
          <span className={`recipient-avatar ${person.color}`}>{person.initials}</span>
          <div className="beneficiary-name"><strong>{person.nickname}</strong><small>{person.name}</small></div>
          <div className="beneficiary-bank"><strong>{person.bank}</strong><small>{mask(person.account)}</small></div>
          <div className="beneficiary-actions"><button onClick={() => { onClose(); onSendMoney(); }}>Send money</button><button aria-label={`More options for ${person.nickname}`} onClick={() => setEditor({ mode: "edit", person })}>•••</button></div>
        </article>)}
        {loading && <div className="beneficiary-empty"><p>Loading your beneficiaries…</p></div>}
        {loadError && <div className="beneficiary-empty"><p className="payment-error">{loadError}</p></div>}
        {!loading && !loadError && visible.length === 0 && <div className="beneficiary-empty"><Icon name="search" size={24} /><h3>{people.length ? "No beneficiaries found" : "No beneficiaries yet"}</h3><p>{people.length ? "Try another search." : "Add an account to pay it quickly next time."}</p></div>}
      </div>
      <footer className="beneficiary-footer"><span>✓</span><p><strong>Beneficiaries are protected</strong>New accounts are verified before they can be saved.</p></footer>
      {notice && <button className="beneficiary-notice" onClick={() => setNotice("")}>✓ {notice}<span>×</span></button>}
    </section>
    {editor && <BeneficiaryEditor editor={editor} onClose={() => setEditor(null)} onSaved={saved} onRemove={person => { setEditor(null); setRemove(person); }} />}
    {remove && <div className="beneficiary-confirm-layer"><button className="confirm-backdrop" onClick={() => setRemove(null)} /><section className="remove-confirm"><span>!</span><h3>Remove {remove.nickname}?</h3><p>This account will be removed from your beneficiaries. Previous transaction records will not be affected.</p><div><button onClick={() => setRemove(null)}>Keep beneficiary</button><button onClick={removePerson}>Remove</button></div></section></div>}
  </div>;
}

function BeneficiaryEditor({ editor, onClose, onSaved, onRemove }) {
  const existing = editor.person;
  const [stage, setStage] = useState(existing ? "view" : "verify");
  const [number, setNumber] = useState("");
  const [nickname, setNickname] = useState("");
  const [resolved, setResolved] = useState(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const verify = async event => {
    event.preventDefault();
    if (!/^\d{10}$/.test(number)) return setError("Enter a valid 10-digit account number.");
    setBusy(true);
    try { setResolved(await accountsApi.lookup(number)); setStage("form"); setError(""); }
    catch (requestError) { setError(requestError.status === 404 ? "We could not find an account with that number." : requestError.message); }
    finally { setBusy(false); }
  };
  const save = async event => {
    event.preventDefault();
    setBusy(true);
    try { await beneficiariesApi.add(resolved.accountNumber, nickname.trim() || undefined); onSaved(); }
    catch (requestError) { setError(requestError.message); setBusy(false); }
  };

  return <div className="beneficiary-editor-layer"><button className="editor-backdrop" onClick={onClose} />
    <aside className="beneficiary-editor">
      <header><div><span>{existing ? "BENEFICIARY" : "NEW BENEFICIARY"}</span><h2>{existing ? "Beneficiary details" : "Add account"}</h2></div><button onClick={onClose}>×</button></header>
      {stage === "view" && <div>
        <div className="verified-account"><span>✓</span><div><small>ACCOUNT NAME</small><strong>{existing.name}</strong><p>{existing.bank} · {mask(existing.account)}</p></div></div>
        <button className="editor-remove" type="button" onClick={() => onRemove(existing)}>Remove beneficiary</button>
      </div>}
      {stage === "verify" && <form onSubmit={verify}>
        <div className="editor-heading"><h3>Enter account number</h3><p>We’ll look up the account name so you can confirm who you’re paying.</p></div>
        <label>Account number<input value={number} onChange={e => { setNumber(e.target.value.replace(/\D/g, "").slice(0, 10)); setError(""); }} inputMode="numeric" placeholder="10-digit account number" autoFocus /></label>
        {error && <p className="payment-error">{error}</p>}
        <button className="editor-primary" type="submit" disabled={busy}>{busy ? "Checking…" : "Verify account"} <Icon name="chevron" size={17} /></button>
      </form>}
      {stage === "form" && <form onSubmit={save}>
        <div className="verified-account"><span>✓</span><div><small>VERIFIED ACCOUNT NAME</small><strong>{resolved.accountName}</strong><p>DanBank · {mask(resolved.accountNumber)}</p></div></div>
        <label>Beneficiary nickname <span>(optional)</span><input value={nickname} onChange={e => setNickname(e.target.value)} maxLength={25} placeholder={`e.g. ${resolved.accountName.split(" ")[0]}`} autoFocus /></label>
        <div className="editor-security"><span>i</span><p>Confirm the account holder’s name before saving. Money sent to the wrong account may not be recoverable.</p></div>
        {error && <p className="payment-error">{error}</p>}
        <button className="editor-primary" type="submit" disabled={busy}>{busy ? "Saving…" : "Add beneficiary"}</button>
      </form>}
    </aside>
  </div>;
}
