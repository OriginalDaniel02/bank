import { useMemo, useState } from "react";
import Icon from "../../components/Icon.jsx";

const records = [
  { id:"DB847210593", title:"Transfer to Sarah James", category:"Transfers", date:"Today", time:"10:42 AM", amount:-45000, status:"Successful", bank:"Access Bank", account:"•••• 4082", note:"Family support", icon:"send", color:"blue" },
  { id:"DB847196221", title:"Payment from Zenith Bank", category:"Money received", date:"Today", time:"8:16 AM", amount:250000, status:"Successful", bank:"Zenith Bank", account:"•••• 7710", note:"Invoice payment", icon:"arrowDown", color:"green" },
  { id:"DB846902144", title:"MTN Airtime", category:"Airtime & data", date:"Yesterday", time:"6:23 PM", amount:-5000, status:"Successful", bank:"MTN Nigeria", account:"0803 •••• 921", note:"Airtime purchase", icon:"phone", color:"purple" },
  { id:"DB846721008", title:"Shoprite Lekki", category:"Card payment", date:"Jul 31, 2026", time:"2:08 PM", amount:-32750.5, status:"Successful", bank:"DanBank Card", account:"•••• 4826", note:"Point of sale purchase", icon:"card", color:"orange" },
  { id:"DB846510439", title:"DSTV Subscription", category:"Bill payment", date:"Jul 30, 2026", time:"11:35 AM", amount:-24500, status:"Successful", bank:"MultiChoice", account:"Smartcard •••• 117", note:"Compact Plus", icon:"receipt", color:"purple" },
  { id:"DB846308712", title:"Transfer to Michael Okoro", category:"Transfers", date:"Jul 29, 2026", time:"4:51 PM", amount:-70000, status:"Pending", bank:"GTBank", account:"•••• 9271", note:"Project materials", icon:"send", color:"blue" },
];

const money = new Intl.NumberFormat("en-NG", { style:"currency", currency:"NGN", minimumFractionDigits:2 });

export default function TransactionHistory({ onClose, onStatements }) {
  const [query,setQuery] = useState("");
  const [filter,setFilter] = useState("All");
  const [selected,setSelected] = useState(null);
  const filters = ["All","Money in","Money out","Pending"];
  const visible = useMemo(() => records.filter(item => {
    const matchesQuery = `${item.title} ${item.category} ${item.id}`.toLowerCase().includes(query.toLowerCase());
    const matchesFilter = filter === "All" || (filter === "Money in" && item.amount > 0) || (filter === "Money out" && item.amount < 0) || (filter === "Pending" && item.status === "Pending");
    return matchesQuery && matchesFilter;
  }),[query,filter]);
  const grouped = Object.groupBy ? Object.groupBy(visible,item => item.date) : visible.reduce((result,item) => ({...result,[item.date]:[...(result[item.date]||[]),item]}),{});

  return <div className="history-dialog" role="dialog" aria-modal="true" aria-labelledby="history-title">
    <button className="history-backdrop" onClick={onClose} aria-label="Close transaction history"/>
    <section className="history-window">
      <header className="history-header"><div><span>ACCOUNT ACTIVITY</span><h2 id="history-title">Transaction history</h2><p>Search and review every movement on your account.</p></div><button onClick={onClose} aria-label="Close">×</button></header>
      <div className="history-toolbar"><div className="history-search"><Icon name="search" size={18}/><input value={query} onChange={e => setQuery(e.target.value)} placeholder="Search name, category, or reference"/></div><button className="statement-button" onClick={onStatements}><Icon name="receipt" size={17}/> Download statement</button></div>
      <div className="history-filters">{filters.map(item => <button key={item} className={filter===item?"active":""} onClick={() => setFilter(item)}>{item}</button>)}<span>{visible.length} transactions</span></div>
      <div className="history-list">{Object.entries(grouped).map(([date,items]) => <section key={date}><div className="history-date"><strong>{date}</strong><span>{items.length} {items.length===1?"transaction":"transactions"}</span></div>{items.map(item => <button className="history-row" key={item.id} onClick={() => setSelected(item)}><span className={`transaction-icon ${item.color}`}><Icon name={item.icon}/></span><span className="history-copy"><strong>{item.title}</strong><small>{item.category} · {item.time}</small></span><span className="history-status"><i className={item.status.toLowerCase()}/>{item.status}</span><span className={item.amount>0?"history-amount income":"history-amount"}>{item.amount>0?"+ ":"− "}{money.format(Math.abs(item.amount))}</span><Icon name="chevron" size={16}/></button>)}</section>)}{visible.length===0&&<div className="history-empty"><Icon name="search" size={24}/><h3>No matching transactions</h3><p>Try another search or change your filter.</p></div>}</div>
    </section>
    {selected&&<TransactionDetail item={selected} onClose={() => setSelected(null)}/>} 
  </div>;
}

function TransactionDetail({ item,onClose }) {
  const [dispute,setDispute] = useState(false);
  return <div className="detail-layer"><button className="detail-backdrop" onClick={onClose}/><aside className="transaction-detail"><header><div><span>TRANSACTION DETAILS</span><h2>{item.status}</h2></div><button onClick={onClose}>×</button></header><div className={`detail-status ${item.status.toLowerCase()}`}><span>{item.status==="Successful"?"✓":"···"}</span></div><div className="detail-amount"><small>{item.amount>0?"Money received":"Amount paid"}</small><strong>{item.amount>0?"+ ":"− "}{money.format(Math.abs(item.amount))}</strong><p>{item.title}</p></div><dl><div><dt>Transaction reference</dt><dd>{item.id}</dd></div><div><dt>Date and time</dt><dd>{item.date}, {item.time}</dd></div><div><dt>Category</dt><dd>{item.category}</dd></div><div><dt>Institution</dt><dd>{item.bank}</dd></div><div><dt>Account</dt><dd>{item.account}</dd></div><div><dt>Narration</dt><dd>{item.note}</dd></div><div><dt>Fee</dt><dd>₦10.00</dd></div><div><dt>Status</dt><dd className={item.status.toLowerCase()}>{item.status}</dd></div></dl>{dispute?<div className="dispute-message"><span>✓</span><p><strong>Request started</strong>Support will guide you through the dispute process.</p></div>:<><button className="detail-primary" onClick={() => window.print()}><Icon name="receipt" size={17}/> Download receipt</button>{item.amount<0&&<button className="detail-text" onClick={() => setDispute(true)}>Report a problem with this transaction</button>}</>}</aside></div>;
}
