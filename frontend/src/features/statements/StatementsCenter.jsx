import { useState } from "react";
import Icon from "../../components/Icon.jsx";
import { accountsApi } from "../../services/api.js";

const money = new Intl.NumberFormat("en-NG", { style: "currency", currency: "NGN", minimumFractionDigits: 2 });
const iso = date => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;

function rangeFor(period, start, end) {
  const today = new Date();
  if (period === "This month") return { from: iso(new Date(today.getFullYear(), today.getMonth(), 1)), to: iso(today) };
  if (period === "Last month") return { from: iso(new Date(today.getFullYear(), today.getMonth() - 1, 1)), to: iso(new Date(today.getFullYear(), today.getMonth(), 0)) };
  if (period === "Last 3 months") return { from: iso(new Date(today.getFullYear(), today.getMonth() - 2, 1)), to: iso(today) };
  return { from: start, to: end };
}

export default function StatementsCenter({ account, onClose }) {
  const [period, setPeriod] = useState("This month");
  const [start, setStart] = useState("");
  const [end, setEnd] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [statement, setStatement] = useState(null);

  const request = () => {
    const range = rangeFor(period, start, end);
    if (!range.from || !range.to || range.from > range.to) { setError("Choose a valid start and end date."); return null; }
    setError(""); return range;
  };
  const preview = async event => {
    event.preventDefault();
    const range = request(); if (!range) return;
    setBusy(true);
    try { setStatement(await accountsApi.statement(account.id, range)); }
    catch (requestError) { setError(requestError.message); }
    finally { setBusy(false); }
  };
  const download = async () => {
    const range = request(); if (!range) return;
    setBusy(true);
    try {
      const blob = await accountsApi.statementCsv(account.id, range);
      const link = document.createElement("a");
      link.href = URL.createObjectURL(blob);
      link.download = `statement-${account.number}-${range.from}-${range.to}.csv`;
      link.click(); URL.revokeObjectURL(link.href);
    } catch (requestError) { setError(requestError.message); }
    finally { setBusy(false); }
  };

  return <div className="statements-dialog" role="dialog" aria-modal="true">
    <button className="statements-backdrop" onClick={onClose} />
    <section className="statements-window">
      <header className="statements-header"><div><span>DOCUMENT CENTRE</span><h2>Account statement</h2><p>Review your activity and download it as a spreadsheet.</p></div><button onClick={onClose}>×</button></header>
      <main className="statements-main">
        <section className="document-hero"><div><span>{account.name.toUpperCase()} · {account.number}</span><h3>Your financial records, always available</h3><p>Opening and closing balances, every credit and debit, for any period up to a year.</p></div><Icon name="receipt" size={34} /></section>
        <form className="statement-form" onSubmit={preview}>
          <label>Statement period<select value={period} onChange={e => { setPeriod(e.target.value); setError(""); }}><option>This month</option><option>Last month</option><option>Last 3 months</option><option>Custom</option></select></label>
          {period === "Custom" && <div className="statement-dates"><label>Start date<input type="date" value={start} onChange={e => setStart(e.target.value)} /></label><label>End date<input type="date" value={end} onChange={e => setEnd(e.target.value)} /></label></div>}
          {error && <p className="payment-error">{error}</p>}
          <div className="payment-actions"><button type="button" className="payment-secondary" onClick={download} disabled={busy}>Download CSV</button><button className="payment-primary" type="submit" disabled={busy}>{busy ? "Preparing…" : "Preview statement"}</button></div>
        </form>
        <div className="document-security"><span>✓</span><p><strong>Private to you</strong>Statements are generated on request and can only be opened by the account owner.</p></div>
      </main>
    </section>
    {statement && <div className="statement-layer preview-layer"><button onClick={() => setStatement(null)} /><aside>
      <header><div><span>STATEMENT PREVIEW</span><h3>{statement.from} to {statement.to}</h3></div><button onClick={() => setStatement(null)}>×</button></header>
      <div className="paper-preview">
        <div className="paper-brand"><span className="brand-mark"><span /></span><strong>DanBank</strong><small>ACCOUNT STATEMENT</small></div>
        <h4>{statement.accountName}</h4><p>Account {statement.accountNumber}</p>
        <dl>
          <div><dt>Period</dt><dd>{statement.from} – {statement.to}</dd></div>
          <div><dt>Opening balance</dt><dd>{money.format(statement.openingBalance)}</dd></div>
          <div><dt>Total credits</dt><dd>{money.format(statement.totalCredits)}</dd></div>
          <div><dt>Total debits</dt><dd>{money.format(statement.totalDebits)}</dd></div>
          <div><dt>Closing balance</dt><dd>{money.format(statement.closingBalance)}</dd></div>
        </dl>
        <dl>{statement.lines.map(line => <div key={line.reference}><dt>{new Date(line.date).toLocaleDateString("en-NG", { day: "numeric", month: "short" })} · {line.description || line.type.replace(/_/g, " ").toLowerCase()}</dt><dd>{line.direction === "CREDIT" ? "+" : "−"} {money.format(line.amount)} <small>(bal {money.format(line.balance)})</small></dd></div>)}
          {!statement.lines.length && <div><dt>No transactions in this period</dt><dd /></div>}</dl>
        <div className="paper-footer"><span>{statement.accountNumber}</span><p>This statement was generated electronically by DanBank.</p></div>
      </div>
      <button className="statement-primary" onClick={() => window.print()}><Icon name="receipt" size={17} /> Print or save as PDF</button>
    </aside></div>}
  </div>;
}
