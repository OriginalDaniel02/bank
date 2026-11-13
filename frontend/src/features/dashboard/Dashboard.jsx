import { useEffect, useState } from "react";
import Brand from "../../components/Brand.jsx";
import Icon from "../../components/Icon.jsx";
import { navigation, quickActions, transactions, weeklySpending, weekDays } from "../../data/dashboard.js";
import TransferFlow from "../payments/TransferFlow.jsx";
import TransactionHistory from "../transactions/TransactionHistory.jsx";
import CardManagement from "../cards/CardManagement.jsx";
import BillPaymentFlow from "../payments/BillPaymentFlow.jsx";
import AddMoneyFlow from "../funding/AddMoneyFlow.jsx";
import BeneficiaryManagement from "../beneficiaries/BeneficiaryManagement.jsx";
import NotificationCenter from "../notifications/NotificationCenter.jsx";
import ProfileSecurity from "../security/ProfileSecurity.jsx";
import SupportCenter from "../support/SupportCenter.jsx";
import VerificationCenter from "../verification/VerificationCenter.jsx";
import SavingsCenter from "../savings/SavingsCenter.jsx";
import StatementsCenter from "../statements/StatementsCenter.jsx";
import ScheduledPayments from "../scheduled/ScheduledPayments.jsx";
import AccountManagement from "../accounts/AccountManagement.jsx";
import FinancialInsights from "../insights/FinancialInsights.jsx";
import { dashboardApi } from "../../services/api.js";

const demoAccounts = [{id:"primary",kind:"savings",type:"Savings",name:"Personal Savings",number:"0123456789",balance:2580420.75,opened:"14 Mar 2024"},{id:"current",kind:"current",type:"Current",name:"Everyday Account",number:"0129874321",balance:742850.2,opened:"02 Jan 2026"}];
const emptyAccount = {id:"",kind:"savings",type:"Savings",name:"Account unavailable",number:"----------",balance:0,opened:""};
const currency = new Intl.NumberFormat("en-NG",{style:"currency",currency:"NGN"});

function mapAccount(account){return {id:account.id,kind:account.type.toLowerCase(),type:account.type[0]+account.type.slice(1).toLowerCase(),name:account.name,number:account.accountNumber,balance:Number(account.availableBalance),opened:new Date(account.openedAt).toLocaleDateString("en-NG",{day:"2-digit",month:"short",year:"numeric"})}}
function mapTransaction(item){const income=item.direction==="CREDIT",amount=Number(item.amount),type=item.type.toLowerCase().replaceAll("_"," ");return {title:item.description||type.replace(/\b\w/g,value=>value.toUpperCase()),meta:`${new Date(item.createdAt).toLocaleDateString("en-NG",{day:"2-digit",month:"short"})} · ${type}`,amount:`${income?"+ ":"− "}${currency.format(amount)}`,status:item.status==="COMPLETED"?"Successful":item.status[0]+item.status.slice(1).toLowerCase(),income,icon:income?"arrowDown":"arrowUp",color:income?"green":"blue"}}

export default function Dashboard({ user, onSignOut }) {
  const [balanceVisible, setBalanceVisible] = useState(true);
  const [activeNav, setActiveNav] = useState("Overview");
  const [transferOpen, setTransferOpen] = useState(false);
  const [toast, setToast] = useState("");
  const [historyOpen, setHistoryOpen] = useState(false);
  const [cardsOpen, setCardsOpen] = useState(false);
  const [utilityMode, setUtilityMode] = useState(null);
  const [fundingOpen, setFundingOpen] = useState(false);
  const [beneficiariesOpen, setBeneficiariesOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [securityOpen, setSecurityOpen] = useState(false);
  const [supportOpen, setSupportOpen] = useState(false);
  const [verificationOpen, setVerificationOpen] = useState(false);
  const [savingsOpen, setSavingsOpen] = useState(false);
  const [statementsOpen, setStatementsOpen] = useState(false);
  const [scheduledOpen, setScheduledOpen] = useState(false);
  const [accountsOpen, setAccountsOpen] = useState(false);
  const [insightsOpen, setInsightsOpen] = useState(false);
  const [accounts, setAccounts] = useState(user.demo?demoAccounts:[]);
  const [selectedAccountId, setSelectedAccountId] = useState(user.demo?"primary":null);
  const [monthlySummary,setMonthlySummary] = useState(user.demo?{moneyIn:810500,moneyOut:328900}:{moneyIn:0,moneyOut:0});
  const [dashboardTransactions,setDashboardTransactions] = useState(user.demo?transactions:[]);
  const [spending,setSpending] = useState(user.demo?weeklySpending:Array(7).fill(0));
  const [spendingDays,setSpendingDays] = useState(weekDays);
  const [dashboardLoading,setDashboardLoading] = useState(!user.demo);
  const selectedAccount = accounts.find(account => account.id === selectedAccountId) || accounts[0] || emptyAccount;
  const [balanceWhole,balanceDecimal] = selectedAccount.balance.toLocaleString("en-NG",{minimumFractionDigits:2,maximumFractionDigits:2}).split(".");
  const peakSpendingIndex = spending.indexOf(Math.max(...spending));
  const firstName = user.firstName || "David";
  const fullName = `${firstName} ${user.lastName || "Adekunle"}`;
  const initials = `${firstName[0]}${(user.lastName || "Adekunle")[0]}`.toUpperCase();

  const applyDashboard = data => {
    setAccounts(data.accounts.map(mapAccount));
    setSelectedAccountId(data.selectedAccountId);
    setMonthlySummary({moneyIn:Number(data.monthlySummary.moneyIn),moneyOut:Number(data.monthlySummary.moneyOut)});
    setDashboardTransactions(data.recentTransactions.map(mapTransaction));
    const amounts=data.dailySpending.map(item=>Number(item.amount)),maximum=Math.max(...amounts,1);
    setSpending(amounts.map(amount=>amount?Math.max(8,Math.round(amount/maximum*100)):4));
    setSpendingDays(data.dailySpending.map(item=>new Date(`${item.date}T00:00:00Z`).toLocaleDateString("en-NG",{weekday:"short"}).slice(0,2)));
  };
  const loadDashboard = async accountId => {setDashboardLoading(true);try{applyDashboard(await dashboardApi.get(user.accessToken,accountId))}catch(error){setToast(error.status===401?"Your session has expired. Please sign in again.":error.message)}finally{setDashboardLoading(false)}};
  useEffect(()=>{if(!user.demo)loadDashboard()},[user.demo,user.accessToken]);
  const selectAccount = accountId => {setSelectedAccountId(accountId);if(!user.demo)loadDashboard(accountId)};

  return <div className="bank-shell">
    <aside className="sidebar"><Brand/><nav className="side-nav" aria-label="Main navigation">{navigation.map(([icon,label]) => <button key={label} className={activeNav === label ? "active" : ""} onClick={() => {setActiveNav(label);if(label==="Cards")setCardsOpen(true);if(label==="Payments")setBeneficiariesOpen(true);if(label==="Settings")setSecurityOpen(true);if(label==="Support")setSupportOpen(true);if(label==="Savings")setSavingsOpen(true)}}><Icon name={icon}/><span>{label}</span></button>)}</nav>
      <div className="help-card"><span className="help-icon"><Icon name="support"/></span><strong>Need some help?</strong><p>Our support team is always ready for you.</p><button onClick={()=>setSupportOpen(true)}>Chat with us</button></div>
      <button className="user-mini user-mini-button" onClick={()=>setSecurityOpen(true)}><div className="avatar">{initials}</div><div><strong>{fullName}</strong><span>Personal account</span></div><Icon name="more"/></button>
    </aside>
      <main className="main-content"><header className="topbar"><div><span className="mobile-brand">DanBank</span><h1>Good morning, {firstName} <span>👋🏾</span></h1><p>Here’s what’s happening with your money today.</p></div><div className="top-actions"><button className="verification-top" onClick={()=>setVerificationOpen(true)}>Tier 2 · Verify</button><button className="search"><Icon name="search"/><span>Search</span></button><button className="notification" onClick={()=>setNotificationsOpen(true)} aria-label="Notifications"><Icon name="bell"/><i/></button><button className="avatar avatar-button" onClick={()=>setSecurityOpen(true)}>{initials}</button></div></header>
      <section className="account-card motion-reveal motion-delay-1"><div className="balance-block"><div className="eyebrow">Available balance <button onClick={() => setBalanceVisible(v => !v)}><Icon name={balanceVisible ? "eye" : "eyeOff"} size={18}/></button></div><div className="balance">{balanceVisible ? `₦${balanceWhole}` : "₦••••••••"}<sup>{balanceVisible ? `.${balanceDecimal}` : ""}</sup></div><div className="account-number">{selectedAccount.name}&nbsp; • &nbsp;{selectedAccount.number} <button title="Copy account number">▣</button></div></div><div className="account-stats"><div><span>Money in this month</span><strong><Icon name="arrowDown" size={16}/> {currency.format(monthlySummary.moneyIn)}</strong></div><div><span>Money out this month</span><strong><Icon name="arrowUp" size={16}/> {currency.format(monthlySummary.moneyOut)}</strong></div></div><button className="account-switch-button" onClick={()=>setAccountsOpen(true)} disabled={dashboardLoading||!accounts.length}>Switch account <Icon name="chevron" size={15}/></button><div className="card-decoration"><i/><i/><i/></div></section>
      <section className="quick-section motion-reveal motion-delay-2"><div className="section-heading"><h2>Quick actions</h2><div className="section-heading-actions"><button onClick={()=>setScheduledOpen(true)}>Scheduled</button><button onClick={()=>setBeneficiariesOpen(true)}>Manage</button></div></div><div className="quick-grid">{quickActions.map(([icon,label], index) => <button className="quick-action" onClick={() => label === "Send money" ? setTransferOpen(true) : label === "Add money" ? setFundingOpen(true) : label === "Buy airtime" ? setUtilityMode("airtime") : setUtilityMode("bills")} style={{ "--item-index": index }} key={label}><span><Icon name={icon}/></span><strong>{label}</strong><Icon name="chevron" size={16}/></button>)}</div></section>
      <div className="content-grid motion-reveal motion-delay-3"><section className="panel transactions-panel"><div className="section-heading"><div><h2>Recent transactions</h2><p>{dashboardLoading?"Updating your account activity…":"Your latest account activity"}</p></div><button onClick={() => setHistoryOpen(true)}>View all <Icon name="chevron" size={15}/></button></div><div className="transactions">{dashboardTransactions.map((item, index) => <button className="transaction" onClick={() => setHistoryOpen(true)} style={{ "--item-index": index }} key={`${item.title}-${index}`}><span className={`transaction-icon ${item.color}`}><Icon name={item.icon}/></span><span className="transaction-copy"><strong>{item.title}</strong><small>{item.meta}</small></span><span className={`transaction-value ${item.income ? "income" : ""}`}><strong>{item.amount}</strong><small>{item.status}</small></span></button>)}{!dashboardLoading&&!dashboardTransactions.length&&<p className="dashboard-empty">No transactions yet.</p>}</div></section>
        <aside className="right-column"><section className="panel spend-panel"><div className="section-heading"><div><h2>Spending overview</h2><p>{new Date().toLocaleDateString("en-NG",{month:"long",year:"numeric"})}</p></div><button onClick={()=>setInsightsOpen(true)} aria-label="Open financial insights"><Icon name="more"/></button></div><div className="spend-total"><span>Total spent this month</span><strong>{currency.format(monthlySummary.moneyOut)}</strong><small>Based on completed transactions</small></div><div className="spend-chart">{spending.map((height,i) => <span key={`${spendingDays[i]}-${i}`} style={{height:`${height}%`}} className={i === peakSpendingIndex&&height>4 ? "peak" : ""}><i>{spendingDays[i]}</i></span>)}</div></section><section className="card-promo"><div><span>Your DanBank card</span><strong>•••• &nbsp; 4826</strong><small>{fullName.toUpperCase()}</small></div><div className="card-chip">▦</div><button onClick={()=>setCardsOpen(true)}>Manage card <Icon name="chevron" size={15}/></button></section></aside>
      </div>
    </main>
    <nav className="mobile-nav">{navigation.slice(0,4).map(([icon,label]) => <button key={label} className={activeNav === label ? "active" : ""} onClick={() => {setActiveNav(label);if(label==="Cards")setCardsOpen(true);if(label==="Payments")setBeneficiariesOpen(true);if(label==="Savings")setSavingsOpen(true)}}><Icon name={icon}/><span>{label}</span></button>)}</nav>
    {transferOpen && <TransferFlow onClose={() => setTransferOpen(false)} onComplete={() => { setToast("Transfer completed successfully."); setTimeout(() => setToast(""), 3500); }}/>} 
    {historyOpen && <TransactionHistory onClose={() => setHistoryOpen(false)} onStatements={() => {setHistoryOpen(false);setStatementsOpen(true)}}/>} 
    {cardsOpen && <CardManagement user={user} onClose={() => setCardsOpen(false)}/>} 
    {utilityMode && <BillPaymentFlow initialMode={utilityMode} onClose={() => setUtilityMode(null)} onComplete={() => {setToast("Payment completed successfully.");setTimeout(()=>setToast(""),3500)}}/>}
    {fundingOpen && <AddMoneyFlow user={user} onClose={() => setFundingOpen(false)} onComplete={() => {setToast("Money added successfully.");setTimeout(()=>setToast(""),3500)}}/>}
    {beneficiariesOpen && <BeneficiaryManagement onClose={() => setBeneficiariesOpen(false)} onSendMoney={() => setTransferOpen(true)}/>} 
    {notificationsOpen && <NotificationCenter onClose={() => setNotificationsOpen(false)}/>} 
    {securityOpen && <ProfileSecurity user={user} onClose={() => setSecurityOpen(false)} onSignOut={onSignOut}/>} 
    {supportOpen && <SupportCenter onClose={() => setSupportOpen(false)}/>} 
    {verificationOpen && <VerificationCenter user={user} onClose={() => setVerificationOpen(false)}/>} 
    {savingsOpen && <SavingsCenter onClose={() => setSavingsOpen(false)}/>} 
    {statementsOpen && <StatementsCenter user={user} onClose={() => setStatementsOpen(false)}/>} 
    {scheduledOpen && <ScheduledPayments onClose={() => setScheduledOpen(false)}/>} 
    {accountsOpen && <AccountManagement accounts={accounts} selectedId={selectedAccountId} onSelect={selectAccount} onUpdate={setAccounts} onClose={() => setAccountsOpen(false)}/>} 
    {insightsOpen && <FinancialInsights account={selectedAccount} onClose={() => setInsightsOpen(false)}/>} 
    {toast && <button className="app-toast" onClick={() => setToast("")}>{toast}<span>×</span></button>}
  </div>;
}
