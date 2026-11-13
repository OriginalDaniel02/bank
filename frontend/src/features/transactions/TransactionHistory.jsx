import { useEffect, useMemo, useState } from "react";
import Icon from "../../components/Icon.jsx";
import { accountsApi } from "../../services/api.js";

const money = new Intl.NumberFormat("en-NG", { style: "currency", currency: "NGN", minimumFractionDigits: 2 });
const PAGE_SIZE = 25;

// How each ledger type is described to the customer.
const kinds = {
  CREDIT: { title: "Money received", category: "Money received", icon: "arrowDown", color: "green", income: true },
  TRANSFER_IN: { title: "Transfer received", category: "Money received", icon: "arrowDown", color: "green", income: true },
  FEE_INCOME: { title: "Fee income", category: "Fees", icon: "arrowDown", color: "green", income: true },
  DEBIT: { title: "Withdrawal", category: "Withdrawal", icon: "arrowUp", color: "orange", income: false },
  TRANSFER_OUT: { title: "Transfer sent", category: "Transfers", icon: "send", color: "blue", income: false },
  FEE: { title: "Transfer fee", category: "Fees", icon: "receipt", color: "purple", income: false },
};
const dayLabel = date => {
  const start = value => new Date(value.getFullYear(), value.getMonth(), value.getDate()).getTime();
  const diff = Math.round((start(new Date()) - start(date)) / 86400000);
  return diff === 0 ? "Today" : diff === 1 ? "Yesterday" : date.toLocaleDateString("en-NG", { day: "numeric", month: "short", year: "numeric" });
};
const toRecord = item => {
  const kind = kinds[item.type] || kinds.CREDIT;
  const created = new Date(item.createdAt);
  const id = item.reference.replace(/-(OUT|IN|FEE|FEE-IN)$/, "").replace(/-/g, "");
  return {
    key: item.reference, id: `DB${id.slice(0, 10).toUpperCase()}`,
    title: item.description ? `${kind.title} · ${item.description}` : kind.title, category: kind.category,
    date: dayLabel(created), time: created.toLocaleTimeString("en-NG", { hour: "numeric", minute: "2-digit" }),
    amount: kind.income ? Number(item.amount) : -Number(item.amount), status: "Successful",
    note: item.description || "—", icon: kind.icon, color: kind.color, isFee: item.type === "FEE",
  };
};

export default function TransactionHistory({ account, onClose, onStatements }) {
  const [records, setRecords] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("All");
  const [selected, setSelected] = useState(null);
  const filters = ["All", "Money in", "Money out"];

  const load = async nextPage => {
    setLoading(true);
    try {
      const result = await accountsApi.history(account.id, nextPage, PAGE_SIZE);
      setRecords(value => nextPage === 0 ? result.data.content.map(toRecord) : [...value, ...result.data.content.map(toRecord)]);
      setPage(result.data.page); setTotalPages(result.data.totalPages); setError("");
    } catch (requestError) { setError(requestError.message); }
    finally { setLoading(false); }
  };
  useEffect(() => { load(0); }, [account.id]);

  const visible = useMemo(() => records.filter(item => {
    const matchesQuery = `${item.title} ${item.category} ${item.id}`.toLowerCase().includes(query.toLowerCase());
    const matchesFilter = filter === "All" || (filter === "Money in" && item.amount > 0) || (filter === "Money out" && item.amount < 0);
    return matchesQuery && matchesFilter;
  }), [records, query, filter]);
  const grouped = visible.reduce((result, item) => ({ ...result, [item.date]: [...(result[item.date] || []), item] }), {});

  return <div className="history-dialog" role="dialog" aria-modal="true" aria-labelledby="history-title">
    <button className="history-backdrop" onClick={onClose} aria-label="Close transaction history" />
    <section className="history-window">
      <header className="history-header"><div><span>ACCOUNT ACTIVITY</span><h2 id="history-title">Transaction history</h2><p>{account.name} · {account.number}</p></div><button onClick={onClose} aria-label="Close">×</button></header>
      <div className="history-toolbar"><div className="history-search"><Icon name="search" size={18} /><input value={query} onChange={e => setQuery(e.target.value)} placeholder="Search description, category, or reference" /></div><button className="statement-button" onClick={onStatements}><Icon name="receipt" size={17} /> Download statement</button></div>
      <div className="history-filters">{filters.map(item => <button key={item} className={filter === item ? "active" : ""} onClick={() => setFilter(item)}>{item}</button>)}<span>{visible.length} transactions</span></div>
      <div className="history-list">
        {Object.entries(grouped).map(([date, items]) => <section key={date}>
          <div className="history-date"><strong>{date}</strong><span>{items.length} {items.length === 1 ? "transaction" : "transactions"}</span></div>
          {items.map(item => <button className="history-row" key={item.key} onClick={() => setSelected(item)}>
            <span className={`transaction-icon ${item.color}`}><Icon name={item.icon} /></span>
            <span className="history-copy"><strong>{item.title}</strong><small>{item.category} · {item.time}</small></span>
            <span className="history-status"><i className={item.status.toLowerCase()} />{item.status}</span>
            <span className={item.amount > 0 ? "history-amount income" : "history-amount"}>{item.amount > 0 ? "+ " : "− "}{money.format(Math.abs(item.amount))}</span>
            <Icon name="chevron" size={16} />
          </button>)}
        </section>)}
        {loading && <div className="history-empty"><p>Loading your transactions…</p></div>}
        {error && <div className="history-empty"><p className="payment-error">{error}</p></div>}
        {!loading && !error && visible.length === 0 && <div className="history-empty"><Icon name="search" size={24} /><h3>{records.length ? "No matching transactions" : "No transactions yet"}</h3><p>{records.length ? "Try another search or change your filter." : "Your activity will appear here."}</p></div>}
        {!loading && page + 1 < totalPages && <button className="statement-button" onClick={() => load(page + 1)}>Load more</button>}
      </div>
    </section>
    {selected && <TransactionDetail item={selected} account={account} onClose={() => setSelected(null)} />}
  </div>;
}

function TransactionDetail({ item, account, onClose }) {
  return <div className="detail-layer"><button className="detail-backdrop" onClick={onClose} /><aside className="transaction-detail">
    <header><div><span>TRANSACTION DETAILS</span><h2>{item.status}</h2></div><button onClick={onClose}>×</button></header>
    <div className={`detail-status ${item.status.toLowerCase()}`}><span>✓</span></div>
    <div className="detail-amount"><small>{item.amount > 0 ? "Money received" : "Amount paid"}</small><strong>{item.amount > 0 ? "+ " : "− "}{money.format(Math.abs(item.amount))}</strong><p>{item.title}</p></div>
    <dl>
      <div><dt>Transaction reference</dt><dd>{item.id}</dd></div>
      <div><dt>Date and time</dt><dd>{item.date}, {item.time}</dd></div>
      <div><dt>Category</dt><dd>{item.category}</dd></div>
      <div><dt>Account</dt><dd>{account.name} · {account.number}</dd></div>
      <div><dt>Narration</dt><dd>{item.note}</dd></div>
      <div><dt>Status</dt><dd className={item.status.toLowerCase()}>{item.status}</dd></div>
    </dl>
    <button className="detail-primary" onClick={() => window.print()}><Icon name="receipt" size={17} /> Download receipt</button>
  </aside></div>;
}
