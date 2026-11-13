import { useState } from "react";
import Icon from "../../components/Icon.jsx";

const controlOptions = [
  ["online", "Online payments", "Use this card on websites and apps"],
  ["contactless", "Contactless payments", "Tap to pay at supported terminals"],
  ["atm", "ATM withdrawals", "Withdraw cash from supported ATMs"],
  ["international", "International payments", "Pay merchants outside Nigeria"],
];

export default function CardManagement({ user, onClose }) {
  const [frozen,setFrozen] = useState(false);
  const [detailsVisible,setDetailsVisible] = useState(false);
  const [controls,setControls] = useState({ online:true, contactless:true, atm:true, international:false });
  const [limit,setLimit] = useState(500000);
  const [draftLimit,setDraftLimit] = useState("500000");
  const [dialog,setDialog] = useState(null);
  const [notice,setNotice] = useState("");
  const fullName = `${user.firstName || "David"} ${user.lastName || "Adekunle"}`.toUpperCase();

  const notify = message => { setNotice(message); setTimeout(() => setNotice(""),3000); };
  const toggleControl = key => {
    if (frozen) return notify("Unfreeze the card before changing payment controls.");
    setControls(value => ({...value,[key]:!value[key]}));
    notify("Card control updated.");
  };
  const saveLimit = event => {
    event.preventDefault();
    const value = Number(draftLimit);
    if (value < 1000 || value > 5000000) return notify("Choose a daily limit between ₦1,000 and ₦5,000,000.");
    setLimit(value); setDialog(null); notify("Daily spending limit updated.");
  };
  const confirmFreeze = () => { setFrozen(value => !value); setDetailsVisible(false); setDialog(null); notify(frozen ? "Your card is active again." : "Your card has been frozen."); };

  return <div className="cards-dialog" role="dialog" aria-modal="true" aria-labelledby="cards-title"><button className="cards-backdrop" onClick={onClose}/><section className="cards-window">
    <header className="cards-header"><div><span>CARD CENTRE</span><h2 id="cards-title">Manage your cards</h2><p>Control how and where your card can be used.</p></div><button onClick={onClose}>×</button></header>
    <div className="cards-layout"><aside className="card-showcase"><div className={`bank-card ${frozen?"frozen":""}`}><div className="bank-card-top"><div className="card-brand"><span className="brand-mark"><span/></span><b>DanBank</b></div><span className="contactless">)))</span></div><span className="card-chip-visual">▦</span><strong className="card-number">{detailsVisible?"5399  8201  7742  4826":"5399  ••••  ••••  4826"}</strong><div className="card-bottom"><div><small>CARD HOLDER</small><span>{fullName}</span></div><div><small>EXPIRES</small><span>{detailsVisible?"09/30":"••/••"}</span></div><b>VISA</b></div>{frozen&&<div className="frozen-overlay"><span>❄</span><strong>Card frozen</strong></div>}</div><button className="card-visibility" onClick={() => !frozen&&setDetailsVisible(value=>!value)} disabled={frozen}><Icon name={detailsVisible?"eyeOff":"eye"} size={17}/>{detailsVisible?"Hide card details":"Show card details"}</button><div className="card-security-note"><span>✓</span><p><strong>Your details are protected</strong>Card information is automatically hidden after this window closes.</p></div></aside>
      <main className="card-controls"><section className={`freeze-card ${frozen?"is-frozen":""}`}><div><span className="freeze-icon">{frozen?"❄":"✓"}</span><p><strong>{frozen?"Your card is frozen":"Your card is active"}</strong>{frozen?"All new card payments and withdrawals are blocked.":"Your card is ready for payments and withdrawals."}</p></div><button onClick={()=>setDialog("freeze")}>{frozen?"Unfreeze card":"Freeze card"}</button></section>
        <section className="controls-section"><div className="control-heading"><div><h3>Payment controls</h3><p>Changes take effect immediately.</p></div></div><div className="control-list">{controlOptions.map(([key,title,description])=><button key={key} onClick={()=>toggleControl(key)} className={frozen?"disabled":""}><span className="control-copy"><strong>{title}</strong><small>{description}</small></span><span className={`switch ${controls[key]?"on":""}`}><i/></span></button>)}</div></section>
        <section className="limit-section"><div><h3>Daily spending limit</h3><p>Maximum card spending permitted each day.</p></div><button onClick={()=>setDialog("limit")}><span>₦{limit.toLocaleString("en-NG")}</span><Icon name="chevron" size={16}/></button><div className="limit-track"><span style={{width:`${Math.min(limit/5000000*100,100)}%`}}/></div></section>
        <section className="card-actions-list"><button onClick={()=>notify("A new card PIN flow will open securely.")}><span><Icon name="settings" size={18}/></span><div><strong>Change card PIN</strong><small>Create a new four-digit PIN</small></div><Icon name="chevron" size={16}/></button><button className="danger" onClick={()=>setDialog("lost")}><span>!</span><div><strong>Card lost or stolen</strong><small>Block this card and request a replacement</small></div><Icon name="chevron" size={16}/></button></section>
      </main></div>
    {notice&&<button className="card-notice" onClick={()=>setNotice("")}>✓ {notice}<span>×</span></button>}
  </section>
  {dialog&&<div className="card-confirm-layer"><button className="confirm-backdrop" onClick={()=>setDialog(null)}/><section className="card-confirm">{dialog==="freeze"&&<><span className={frozen?"confirm-safe":"confirm-alert"}>{frozen?"✓":"❄"}</span><h3>{frozen?"Unfreeze this card?":"Freeze this card?"}</h3><p>{frozen?"Payments and withdrawals will become available immediately.":"New payments and withdrawals will be blocked until you unfreeze it."}</p><div><button onClick={()=>setDialog(null)}>Cancel</button><button className="confirm-primary" onClick={confirmFreeze}>{frozen?"Unfreeze card":"Freeze card"}</button></div></>}{dialog==="limit"&&<form onSubmit={saveLimit}><span className="confirm-safe">₦</span><h3>Set daily spending limit</h3><p>Choose the maximum amount this card can spend per day.</p><label>Daily limit<div><span>₦</span><input value={draftLimit} onChange={e=>setDraftLimit(e.target.value.replace(/\D/g,""))} inputMode="numeric" autoFocus/></div></label><small>Minimum ₦1,000 · Maximum ₦5,000,000</small><section><button type="button" onClick={()=>setDialog(null)}>Cancel</button><button className="confirm-primary" type="submit">Save limit</button></section></form>}{dialog==="lost"&&<><span className="confirm-danger">!</span><h3>Report card as lost?</h3><p>This permanently blocks the card. You cannot undo this action, and a replacement-card process will begin.</p><div><button onClick={()=>setDialog(null)}>Go back</button><button className="danger-primary" onClick={()=>{setDialog(null);setFrozen(true);notify("Card blocked. Replacement request started.")}}>Block permanently</button></div></>}</section></div>}
  </div>;
}
