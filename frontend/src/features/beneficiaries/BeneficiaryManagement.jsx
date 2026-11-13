import { useMemo, useState } from "react";
import Icon from "../../components/Icon.jsx";

const initialPeople = [
  {id:1,name:"Sarah James",nickname:"Sarah",bank:"Access Bank",account:"0123454082",favorite:true,initials:"SJ",color:"mint"},
  {id:2,name:"Michael Okoro",nickname:"Michael",bank:"GTBank",account:"0217689271",favorite:false,initials:"MO",color:"blue"},
  {id:3,name:"Ada Nwosu",nickname:"Ada",bank:"Zenith Bank",account:"1008221356",favorite:true,initials:"AN",color:"plum"},
  {id:4,name:"Ibrahim Musa",nickname:"Ibrahim",bank:"First Bank",account:"3054128840",favorite:false,initials:"IM",color:"orange"},
];
const banks=["Access Bank","First Bank","GTBank","UBA","Zenith Bank"];
const mask=value=>`${value.slice(0,4)}  ••••  ${value.slice(-4)}`;

export default function BeneficiaryManagement({onClose,onSendMoney}) {
  const [people,setPeople]=useState(initialPeople);
  const [query,setQuery]=useState("");
  const [filter,setFilter]=useState("All");
  const [editor,setEditor]=useState(null);
  const [remove,setRemove]=useState(null);
  const [notice,setNotice]=useState("");
  const visible=useMemo(()=>people.filter(person=>(filter==="All"||person.favorite)&&`${person.name} ${person.nickname} ${person.bank} ${person.account}`.toLowerCase().includes(query.toLowerCase())),[people,query,filter]);
  const notify=message=>{setNotice(message);setTimeout(()=>setNotice(""),2800)};
  const toggleFavorite=id=>{setPeople(value=>value.map(person=>person.id===id?{...person,favorite:!person.favorite}:person));notify("Favorite recipients updated.")};
  const savePerson=person=>{setPeople(value=>person.id?value.map(item=>item.id===person.id?person:item):[person,...value]);setEditor(null);notify(person.id?"Beneficiary updated.":"Beneficiary added successfully.")};
  const removePerson=()=>{setPeople(value=>value.filter(person=>person.id!==remove.id));setRemove(null);notify("Beneficiary removed.")};

    return <div className="beneficiary-dialog" role="dialog" aria-modal="true"><button className="beneficiary-backdrop" onClick={onClose}/><section className="beneficiary-window"><header className="beneficiary-header"><div><span>PAYMENTS</span><h2>Beneficiaries</h2><p>Manage the people and accounts you pay regularly.</p></div><button onClick={onClose}>×</button></header><div className="beneficiary-toolbar"><div className="beneficiary-search"><Icon name="search" size={18}/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Search name, bank, or account"/></div><button className="add-beneficiary" onClick={()=>setEditor({mode:"add"})}><Icon name="plus" size={17}/> Add beneficiary</button></div><div className="beneficiary-filters"><button className={filter==="All"?"active":""} onClick={()=>setFilter("All")}>All beneficiaries <span>{people.length}</span></button><button className={filter==="Favorites"?"active":""} onClick={()=>setFilter("Favorites")}>Favorites <span>{people.filter(p=>p.favorite).length}</span></button></div><div className="beneficiary-list"><div className="beneficiary-list-heading"><span/><span/><span>BENEFICIARY</span><span>BANK ACCOUNT</span><span>ACTIONS</span></div>{visible.map(person=><article key={person.id}><button className={`favorite-star ${person.favorite?"active":""}`} onClick={()=>toggleFavorite(person.id)} aria-label={`${person.favorite?"Remove":"Add"} ${person.nickname} ${person.favorite?"from":"to"} favorites`}>★</button><span className={`recipient-avatar ${person.color}`}>{person.initials}</span><div className="beneficiary-name"><strong>{person.nickname}</strong><small>{person.name}</small></div><div className="beneficiary-bank"><strong>{person.bank}</strong><small>{mask(person.account)}</small></div><div className="beneficiary-actions"><button onClick={()=>{onClose();onSendMoney()}}>Send money</button><button aria-label={`More options for ${person.nickname}`} onClick={()=>setEditor({mode:"edit",person})}>•••</button></div></article>)}{visible.length===0&&<div className="beneficiary-empty"><Icon name="search" size={24}/><h3>No beneficiaries found</h3><p>Try another search or add a new beneficiary.</p></div>}</div><footer className="beneficiary-footer"><span>✓</span><p><strong>Beneficiaries are protected</strong>New accounts are verified before they can receive a payment.</p></footer>{notice&&<button className="beneficiary-notice" onClick={()=>setNotice("")}>✓ {notice}<span>×</span></button>}</section>
    {editor&&<BeneficiaryEditor editor={editor} onClose={()=>setEditor(null)} onSave={savePerson} onRemove={person=>{setEditor(null);setRemove(person)}}/>}{remove&&<div className="beneficiary-confirm-layer"><button className="confirm-backdrop" onClick={()=>setRemove(null)}/><section className="remove-confirm"><span>!</span><h3>Remove {remove.nickname}?</h3><p>This account will be removed from your beneficiaries. Previous transaction records will not be affected.</p><div><button onClick={()=>setRemove(null)}>Keep beneficiary</button><button onClick={removePerson}>Remove</button></div></section></div>}
  </div>;
}

function BeneficiaryEditor({editor,onClose,onSave,onRemove}) {
  const existing=editor.person;
  const [stage,setStage]=useState(existing?"form":"verify");
  const [form,setForm]=useState(existing?{...existing}:{bank:"",account:"",nickname:""});
  const [resolved,setResolved]=useState(existing?.name||"");
  const [error,setError]=useState("");
  const update=e=>{setForm(value=>({...value,[e.target.name]:e.target.value}));setError("")};
  const verify=e=>{e.preventDefault();if(!form.bank||form.account.length!==10)return setError("Choose a bank and enter a valid 10-digit account number.");setResolved("Chinedu Eze");setStage("form")};
  const save=e=>{e.preventDefault();if(!form.nickname.trim())return setError("Add a recognizable nickname.");const initials=resolved.split(" ").map(word=>word[0]).slice(0,2).join("");onSave({...form,id:existing?.id||Date.now(),name:resolved,initials,color:existing?.color||"mint",favorite:existing?.favorite||false})};
  return <div className="beneficiary-editor-layer"><button className="editor-backdrop" onClick={onClose}/><aside className="beneficiary-editor"><header><div><span>{existing?"EDIT BENEFICIARY":"NEW BENEFICIARY"}</span><h2>{existing?"Update beneficiary":"Add bank account"}</h2></div><button onClick={onClose}>×</button></header>{stage==="verify"?<form onSubmit={verify}><div className="editor-heading"><h3>Enter account details</h3><p>We’ll verify the account name with the receiving bank.</p></div><label>Bank<select name="bank" value={form.bank} onChange={update}><option value="">Select a bank</option>{banks.map(bank=><option key={bank}>{bank}</option>)}</select></label><label>Account number<input name="account" value={form.account} onChange={e=>setForm(value=>({...value,account:e.target.value.replace(/\D/g,"").slice(0,10)}))} inputMode="numeric" placeholder="10-digit account number"/></label>{error&&<p className="payment-error">{error}</p>}<button className="editor-primary" type="submit">Verify account <Icon name="chevron" size={17}/></button></form>:<form onSubmit={save}><div className="verified-account"><span>✓</span><div><small>VERIFIED ACCOUNT NAME</small><strong>{resolved}</strong><p>{form.bank} · {mask(form.account)}</p></div></div><label>Beneficiary nickname<input name="nickname" value={form.nickname} onChange={update} maxLength={25} placeholder="e.g. Chinedu" autoFocus/></label><div className="editor-security"><span>i</span><p>Confirm the account holder’s name before saving. DanBank cannot recover money sent to the wrong account.</p></div>{error&&<p className="payment-error">{error}</p>}<button className="editor-primary" type="submit">{existing?"Save changes":"Add beneficiary"}</button>{existing&&<button className="editor-remove" type="button" onClick={()=>onRemove(existing)}>Remove beneficiary</button>}</form>}</aside></div>;
}
