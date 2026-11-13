import { useState } from "react";
import Icon from "../../components/Icon.jsx";

const money = new Intl.NumberFormat("en-NG", { style: "currency", currency: "NGN", minimumFractionDigits: 2 });

export default function AddMoneyFlow({ account, onClose }) {
  const [copied, setCopied] = useState("");
  const copy = async (value, label) => {
    try { await navigator.clipboard.writeText(value); setCopied(label); }
    catch { setCopied("Copy unavailable"); }
    setTimeout(() => setCopied(""), 1800);
  };

  return <div className="funding-dialog" role="dialog" aria-modal="true">
    <button className="funding-backdrop" onClick={onClose} />
    <section className="funding-window">
      <header className="funding-header"><div><span>FUND YOUR ACCOUNT</span><h2>Add money</h2><p>Receive money by bank transfer to your DanBank account.</p></div><button onClick={onClose}>×</button></header>
      <div className="funding-account"><div><span>Money will be added to</span><strong>{account.name} · {account.number.slice(-4)}</strong></div><b>{money.format(account.balance)}</b></div>
      <section className="transfer-funding">
        <div className="funding-badge"><Icon name="transfer" size={21} /></div>
        <h3>Your DanBank account details</h3>
        <p>Anyone with a DanBank account can send money to the account number below. Your balance updates as soon as the transfer completes.</p>
        <dl>
          <div><dt>Bank name</dt><dd>DanBank <button onClick={() => copy("DanBank", "Bank name")}>▣</button></dd></div>
          <div><dt>Account number</dt><dd className="funding-number">{account.number} <button onClick={() => copy(account.number, "Account number")}>▣</button></dd></div>
          <div><dt>Account name</dt><dd>{account.name.toUpperCase()} <button onClick={() => copy(account.name.toUpperCase(), "Account name")}>▣</button></dd></div>
          <div><dt>Account type</dt><dd>{account.type}</dd></div>
        </dl>
        {copied && <div className="copy-notice">✓ {copied} copied</div>}
        <div className="funding-info"><span>i</span><p>Adding money with a debit card and receiving transfers from other Nigerian banks are coming soon.</p></div>
        <button className="funding-primary" onClick={() => window.print()}><Icon name="receipt" size={17} /> Download account details</button>
      </section>
    </section>
  </div>;
}
