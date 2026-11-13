import { useEffect, useState } from "react";
import { insightsApi } from "../../services/api.js";

const money = value => `₦${Number(value).toLocaleString("en-NG", { maximumFractionDigits: 0 })}`;
const palette = ["#0ea5e9", "#7c3aed", "#f97316", "#11a683", "#ec4899", "#64748b"];
const monthName = value => new Date(`${value}-01T00:00:00`).toLocaleDateString("en-NG", { month: "short" });
const monthLong = value => new Date(`${value}-01T00:00:00`).toLocaleDateString("en-NG", { month: "long", year: "numeric" });
const change = (now, before) => before > 0 ? `${now >= before ? "↑" : "↓"} ${Math.abs(Math.round((now - before) / before * 100))}%` : "—";

export default function FinancialInsights({ account, onClose }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    insightsApi.get(account.id)
      .then(result => { if (active) setData(result); })
      .catch(requestError => { if (active) setError(requestError.message); });
    return () => { active = false; };
  }, [account.id]);

  const income = data?.thisMonth.moneyIn ?? 0;
  const spent = data?.thisMonth.moneyOut ?? 0;
  const net = income - spent;
  const tallest = data ? Math.max(1, ...data.months.flatMap(m => [m.moneyIn, m.moneyOut])) : 1;
  const slices = data?.outflow ?? [];
  const sliceTotal = slices.reduce((sum, slice) => sum + slice.amount, 0);
  let angle = 0;
  const donut = sliceTotal > 0
    ? `conic-gradient(${slices.map((slice, index) => { const start = angle; angle += slice.amount / sliceTotal * 360; return `${palette[index % palette.length]} ${start}deg ${angle}deg`; }).join(", ")})`
    : "#e6ecef";

  return <div className="insights-backdrop" onMouseDown={event => event.target === event.currentTarget && onClose()}>
    <section className="insights-shell" role="dialog" aria-modal="true" aria-labelledby="insights-title">
      <header className="insights-header"><div><span className="insights-kicker">Smart money</span><h2 id="insights-title">Financial insights</h2><p>{account.name} · {data ? monthLong(data.thisMonth.month) : "…"}</p></div><button className="insights-close" onClick={onClose} aria-label="Close">×</button></header>
      <div className="insights-content">
        {error && <p className="payment-error">{error}</p>}
        {!data && !error && <p className="payment-hint">Working out your numbers…</p>}
        {data && <>
          <div className="insight-metrics">
            <article><span>Money in</span><strong>{money(income)}</strong><small>{change(income, data.lastMonth.moneyIn)} vs {monthName(data.lastMonth.month)}</small></article>
            <article><span>Money out</span><strong>{money(spent)}</strong><small>{change(spent, data.lastMonth.moneyOut)} vs {monthName(data.lastMonth.month)}</small></article>
            <article><span>Net cash flow</span><strong>{net < 0 ? "−" : ""}{money(Math.abs(net))}</strong><small>{income > 0 ? `${Math.round(net / income * 100)}% of what came in` : "Nothing received yet"}</small></article>
          </div>
          <div className="insights-grid">
            <article className="insight-card">
              <div className="insight-title"><div><h3>Money in and out</h3><p>The last six months</p></div><small><i className="income-key" /> In <i className="expense-key" /> Out</small></div>
              <div className="cash-chart">{data.months.map(m => <div className="cash-group" key={m.month}><div><i className="income-bar" style={{ height: `${Math.max(2, m.moneyIn / tallest * 100)}%` }} /><i className="expense-bar" style={{ height: `${Math.max(2, m.moneyOut / tallest * 100)}%` }} /></div><span>{monthName(m.month)}</span></div>)}</div>
            </article>
            <article className="insight-card">
              <div className="insight-title"><div><h3>Where your money went</h3><p>This month, by type</p></div></div>
              <div className="category-visual">
                <div className="category-donut" style={{ background: donut }}><span><strong>{money(spent)}</strong><small>Total out</small></span></div>
                <div className="category-legend">{slices.map((slice, index) => <div key={slice.label}><i style={{ background: palette[index % palette.length] }} /><span>{slice.label}</span><strong>{sliceTotal ? Math.round(slice.amount / sliceTotal * 100) : 0}%</strong></div>)}{!slices.length && <div><span>No money has gone out yet this month.</span></div>}</div>
              </div>
            </article>
          </div>
          {data.largestExpense && <article className="spending-alert"><b>↑</b><div><strong>Biggest payment this month</strong><p>{money(data.largestExpense.amount)} · {data.largestExpense.description} · {new Date(data.largestExpense.date).toLocaleDateString("en-NG", { day: "numeric", month: "short" })}</p></div></article>}
          {data.savedThisMonth > 0 && <article className="spending-alert"><b>✓</b><div><strong>Saved this month</strong><p>You set aside {money(data.savedThisMonth)} in your savings goals. It isn’t counted as spending.</p></div></article>}
          <p className="payment-hint">DanBank groups money by what happened to it (transfers, fees, withdrawals). Spending categories like “food” need merchant data we don’t have yet.</p>
        </>}
      </div>
    </section>
  </div>;
}
