import { useEffect, useState } from "react";
import Icon from "../../components/Icon.jsx";

const services = {
  airtime: [
    { name:"MTN", color:"#ffcb05", text:"#322b00" }, { name:"Airtel", color:"#e32636", text:"#fff" },
    { name:"Glo", color:"#159447", text:"#fff" }, { name:"9mobile", color:"#076c48", text:"#fff" },
  ],
  bills: [
    { name:"Electricity", icon:"⚡", description:"Prepaid and postpaid meters" }, { name:"Cable TV", icon:"▣", description:"DStv, GOtv and Startimes" },
    { name:"Internet", icon:"⌁", description:"Internet subscriptions" }, { name:"Water", icon:"◉", description:"Water utility payments" },
  ],
};
const money = new Intl.NumberFormat("en-NG",{style:"currency",currency:"NGN",minimumFractionDigits:2});

export default function BillPaymentFlow({ initialMode="bills", onClose, onComplete }) {
  const [mode,setMode] = useState(initialMode);
  const [stage,setStage] = useState("details");
  const [provider,setProvider] = useState(null);
  const [identifier,setIdentifier] = useState("");
  const [amount,setAmount] = useState("");
  const [pin,setPin] = useState("");
  const [save,setSave] = useState(true);
  const [error,setError] = useState("");
  const numericAmount = Number(amount);

  useEffect(()=>{if(stage!=="processing")return;const timer=setTimeout(()=>setStage("success"),1400);return()=>clearTimeout(timer)},[stage]);
  const resetMode = value => {setMode(value);setProvider(null);setIdentifier("");setAmount("");setError("");setStage("details")};
  const review = event => {event.preventDefault();if(!provider)return setError("Choose a service provider.");if(identifier.replace(/\D/g,"").length<10)return setError(mode==="airtime"?"Enter a valid 11-digit mobile number.":"Enter a valid customer or meter number.");if(!numericAmount||numericAmount<100)return setError("Enter an amount of at least ₦100.");setError("");setStage("review")};
  const authorize = event => {event.preventDefault();if(!/^\d{4}$/.test(pin))return setError("Enter your complete 4-digit transaction PIN.");setError("");setStage("processing")};
  const finish = () => {onComplete?.();onClose()};
  const label = mode==="airtime"?"Airtime":"Bill payment";

  return <div className="utility-dialog" role="dialog" aria-modal="true"><button className="utility-backdrop" onClick={onClose}/><section className="utility-window"><header className="utility-header"><div><span>PAYMENTS</span><h2>{stage==="success"?"Payment complete":"Pay with DanBank"}</h2></div><button onClick={onClose}>×</button></header>
    {stage==="details"&&<><div className="utility-tabs"><button className={mode==="bills"?"active":""} onClick={()=>resetMode("bills")}><Icon name="receipt" size={17}/> Pay bills</button><button className={mode==="airtime"?"active":""} onClick={()=>resetMode("airtime")}><Icon name="phone" size={17}/> Buy airtime</button></div><form className="utility-form" onSubmit={review}><div className="utility-heading"><h3>{mode==="airtime"?"Choose mobile network":"What would you like to pay?"}</h3><p>{mode==="airtime"?"Select the recipient’s network provider.":"Choose a bill category to continue."}</p></div><div className={mode==="airtime"?"network-grid":"service-grid"}>{services[mode].map(item=><button type="button" key={item.name} className={provider?.name===item.name?"selected":""} onClick={()=>{setProvider(item);setError("")}} style={mode==="airtime"?{"--provider-color":item.color,"--provider-text":item.text}:undefined}>{mode==="airtime"?<span>{item.name[0]}</span>:<span>{item.icon}</span>}<strong>{item.name}</strong>{item.description&&<small>{item.description}</small>}<i>{provider?.name===item.name?"✓":""}</i></button>)}</div><label className="utility-field">{mode==="airtime"?"Mobile number":"Customer or meter number"}<input value={identifier} onChange={e=>{setIdentifier(e.target.value.replace(/[^0-9]/g,"").slice(0,14));setError("")}} inputMode="numeric" placeholder={mode==="airtime"?"0803 000 0000":"Enter customer number"}/></label><div className="utility-amount"><label>Amount</label><div><span>₦</span><input value={amount} onChange={e=>{setAmount(e.target.value.replace(/[^0-9]/g,""));setError("")}} inputMode="numeric" placeholder="0.00"/></div>{mode==="airtime"&&<section>{[1000,2000,5000,10000].map(value=><button type="button" key={value} onClick={()=>setAmount(String(value))}>₦{value.toLocaleString()}</button>)}</section>}<small>Available balance: ₦2,580,420.75</small></div><label className="save-detail"><input type="checkbox" checked={save} onChange={e=>setSave(e.target.checked)}/><span><strong>Save these details</strong>Make future payments faster.</span></label>{error&&<p className="payment-error">{error}</p>}<button className="utility-primary" type="submit">Review payment <Icon name="chevron" size={18}/></button></form></>}
    {stage==="review"&&<section className="utility-review"><div className="utility-heading"><h3>Review your payment</h3><p>Make sure these details are correct before authorizing.</p></div><div className="utility-review-hero"><span style={{background:provider.color,color:provider.text}}>{mode==="airtime"?provider.name[0]:provider.icon}</span><small>{label.toUpperCase()}</small><strong>{money.format(numericAmount)}</strong><p>{provider.name}</p></div><dl><div><dt>{mode==="airtime"?"Mobile number":"Customer number"}</dt><dd>{identifier}</dd></div><div><dt>Provider</dt><dd>{provider.name}</dd></div><div><dt>Payment account</dt><dd>Personal Savings · 6789</dd></div><div><dt>Fee</dt><dd>₦0.00</dd></div><div className="utility-total"><dt>Total debit</dt><dd>{money.format(numericAmount)}</dd></div></dl><div className="payment-actions"><button className="payment-secondary" onClick={()=>setStage("details")}>Edit details</button><button className="utility-primary" onClick={()=>setStage("pin")}>Confirm payment <Icon name="chevron" size={18}/></button></div></section>}
    {stage==="pin"&&<form className="utility-authorize" onSubmit={authorize}><span className="authorize-shield">✓</span><h3>Authorize payment</h3><p>Enter your transaction PIN to approve this {label.toLowerCase()} of <strong>{money.format(numericAmount)}</strong>.</p><div className="pin-dots">{[0,1,2,3].map(i=><span key={i} className={pin.length>i?"filled":""}/>)}</div><input className="hidden-pin" value={pin} onChange={e=>{setPin(e.target.value.replace(/\D/g,"").slice(0,4));setError("")}} inputMode="numeric" type="password" autoFocus/>{error&&<p className="payment-error">{error}</p>}<button className="utility-primary" type="submit">Authorize payment</button><button className="payment-text" type="button" onClick={()=>setStage("review")}>Back to review</button></form>}
    {stage==="processing"&&<section className="utility-state"><span className="secure-loader"><i/></span><h3>Processing payment</h3><p>We’re securely contacting {provider.name}. Please keep this window open.</p></section>}
    {stage==="success"&&<section className="utility-state utility-success"><span className="confirmed-mark">✓</span><small>PAYMENT SUCCESSFUL</small><h3>{money.format(numericAmount)}</h3><p>{label} purchased successfully for <strong>{identifier}</strong></p><dl><div><dt>Provider</dt><dd>{provider.name}</dd></div><div><dt>Reference</dt><dd>DBP{Date.now().toString().slice(-8)}</dd></div><div><dt>Status</dt><dd>Successful</dd></div></dl><button className="utility-primary" onClick={finish}>Return to dashboard</button><button className="payment-text" onClick={()=>window.print()}>Download receipt</button></section>}
  </section></div>;
}
