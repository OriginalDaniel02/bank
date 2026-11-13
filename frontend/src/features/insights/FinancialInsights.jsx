import { useState } from "react";
import Icon from "../../components/Icon.jsx";

const money = value => `₦${Number(value).toLocaleString("en-NG")}`;
const starterCategories = [
  ["Food & dining", 98600, 120000, "#f97316"],
  ["Transport", 68400, 90000, "#7c3aed"],
  ["Bills & utilities", 74200, 80000, "#0ea5e9"],
  ["Shopping", 52700, 70000, "#ec4899"],
  ["Other", 35000, 60000, "#64748b"],
];
const months = [["Feb",680,402],["Mar",740,466],["Apr",710,390],["May",860,520],["Jun",775,374],["Jul",810,329]];

export default function FinancialInsights({ account, onClose }) {
  const [tab, setTab] = useState("Overview");
  const [categories, setCategories] = useState(starterCategories);
  const [editing, setEditing] = useState(null);
  const spent = categories.reduce((sum, item) => sum + item[1], 0);
  const limit = categories.reduce((sum, item) => sum + item[2], 0);
  const saveLimit = event => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const next = Number(data.get("limit"));
    if (editing.index === -1) setCategories(items => [...items, [data.get("category"), 0, next, "#11a683"]]);
    else setCategories(items => items.map((item,index) => index === editing.index ? [item[0],item[1],next,item[3]] : item));
    setEditing(null);
  };

  return <div className="insights-backdrop" onMouseDown={event => event.target === event.currentTarget && onClose()}>
    <section className="insights-shell" role="dialog" aria-modal="true" aria-labelledby="insights-title">
      <header className="insights-header"><div><span className="insights-kicker">Smart money</span><h2 id="insights-title">Financial insights</h2><p>{account.name} · July 2026</p></div><button className="insights-close" onClick={onClose} aria-label="Close">×</button></header>
      <nav className="insights-tabs">{["Overview","Budgets","Forecast"].map(name => <button key={name} className={tab===name?"active":""} onClick={()=>setTab(name)}>{name}</button>)}</nav>
      <div className="insights-content">
        {tab === "Overview" && <>
          <div className="insight-metrics"><article><span>Income</span><strong>{money(810500)}</strong><small>↑ 4.6% vs June</small></article><article><span>Expenses</span><strong>{money(spent)}</strong><small>↓ 12% vs June</small></article><article><span>Net cash flow</span><strong>{money(810500-spent)}</strong><small>{Math.round((810500-spent)/810500*100)}% savings rate</small></article></div>
          <div className="insights-grid">
            <article className="insight-card"><div className="insight-title"><div><h3>Income and expenses</h3><p>Six-month cash-flow trend</p></div><small><i className="income-key"/> Income <i className="expense-key"/> Expense</small></div><div className="cash-chart">{months.map(([name,income,expense]) => <div className="cash-group" key={name}><div><i className="income-bar" style={{height:`${income/8.6}%`}}/><i className="expense-bar" style={{height:`${expense/8.6}%`}}/></div><span>{name}</span></div>)}</div></article>
            <article className="insight-card"><div className="insight-title"><div><h3>Spending categories</h3><p>Where your money went</p></div></div><div className="category-visual"><div className="category-donut"><span><strong>{money(spent)}</strong><small>Total spent</small></span></div><div className="category-legend">{categories.map(([name,value,,color]) => <div key={name}><i style={{background:color}}/><span>{name}</span><strong>{Math.round(value/spent*100)}%</strong></div>)}</div></div></article>
          </div>
          <article className="spending-alert"><b>!</b><div><strong>Unusual spending detected</strong><p>Your dining spend this week is 42% above your usual average.</p></div><button onClick={()=>setTab("Budgets")}>Review budget</button></article>
        </>}
        {tab === "Budgets" && <>
          <article className="budget-summary"><div><span>Monthly budget</span><strong>{money(limit)}</strong><small>{money(limit-spent)} left for July</small></div><div className="budget-ring" style={{"--used":`${Math.min(100,spent/limit*100)*3.6}deg`}}><span><strong>{Math.round(spent/limit*100)}%</strong><small>used</small></span></div></article>
          <section className="budget-list"><div className="budget-list-head"><div><h3>Category limits</h3><p>Stay intentional with every naira</p></div><button onClick={()=>setEditing({index:-1,limit:50000})}>+ Custom budget</button></div>{categories.map(([name,value,max,color],index) => {const percent=Math.round(value/max*100);return <article className="budget-row" key={name}><span className="budget-icon" style={{background:`${color}18`,color}}>{name[0]}</span><div className="budget-progress"><div><strong>{name}</strong><span>{money(value)} of {money(max)}</span></div><div><i className={percent>=90?"danger":""} style={{width:`${Math.min(100,percent)}%`,background:color}}/></div></div><strong className={percent>=90?"warning":""}>{percent}%</strong><button onClick={()=>setEditing({index,limit:max,name})}>Edit</button></article>})}</section>
        </>}
        {tab === "Forecast" && <>
          <article className="forecast-hero"><div><span>Predicted month-end balance</span><strong>{money(account.balance+273500)}</strong><p>Based on regular income, scheduled bills and your recent spending.</p></div><span><Icon name="shield"/> High confidence</span></article>
          <div className="forecast-grid"><article className="insight-card"><div className="insight-title"><div><h3>Balance outlook</h3><p>Next 30 days</p></div></div><div className="forecast-line"><i/><b/><span>Safety line · ₦500k</span></div><div className="forecast-stats"><span>Today<strong>{money(account.balance)}</strong></span><span>Lowest expected<strong>{money(account.balance-146500)}</strong></span><span>Month end<strong>{money(account.balance+273500)}</strong></span></div><div className="safe-notice"><Icon name="shield"/><span><strong>Your balance looks healthy</strong><small>No low-balance risk predicted this month.</small></span></div></article><article className="insight-card upcoming-card"><div className="insight-title"><div><h3>Upcoming payments</h3><p>Forecasted commitments</p></div><strong>{money(146500)}</strong></div>{[["Aug 04","Rent contribution",85000],["Aug 08","Electricity",35000],["Aug 12","Internet subscription",26500]].map(([date,name,value])=><div className="upcoming-row" key={name}><span>{date}</span><div><strong>{name}</strong><small>Scheduled payment</small></div><b>-{money(value)}</b></div>)}<div className="income-forecast"><span>Expected income · Aug 25</span><strong>+{money(420000)}</strong></div></article></div>
        </>}
      </div>
    </section>
    {editing && <div className="budget-editor-backdrop"><form className="budget-editor" onSubmit={saveLimit}><button type="button" className="insights-close" onClick={()=>setEditing(null)}>×</button><span className="insights-kicker">Budget control</span><h3>{editing.index===-1?"Create a custom budget":`Edit ${editing.name}`}</h3><p>Set a monthly limit. DanBank will alert you as you approach it.</p>{editing.index===-1&&<label>Category name<input name="category" required placeholder="e.g. Entertainment"/></label>}<label>Monthly limit (₦)<input name="limit" type="number" min="1000" step="1000" defaultValue={editing.limit} required/></label><button className="budget-save">Save budget</button></form></div>}
  </div>;
}
