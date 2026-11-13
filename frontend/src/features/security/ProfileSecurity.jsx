import { useEffect, useState } from "react";
import Icon from "../../components/Icon.jsx";
import { accountsApi, securityApi } from "../../services/api.js";

const money = new Intl.NumberFormat("en-NG", { style: "currency", currency: "NGN", maximumFractionDigits: 0 });

export default function ProfileSecurity({ user, kycTier, pinSet, onPinSet, onClose, onSignOut, onVerify }) {
  const [section, setSection] = useState("Profile");
  const [pinDialog, setPinDialog] = useState(false);
  const [passwordDialog, setPasswordDialog] = useState(false);
  const [notice, setNotice] = useState("");
  const notify = text => { setNotice(text); setTimeout(() => setNotice(""), 3000); };
  const initials = `${(user.firstName || "?")[0]}${(user.lastName || "?")[0]}`.toUpperCase();
  const verified = kycTier >= 1;

  return <div className="security-dialog" role="dialog" aria-modal="true">
    <button className="security-backdrop" onClick={onClose} />
    <section className="security-window">
      <aside className="security-nav">
        <div className="profile-summary"><span>{initials}</span><strong>{user.firstName} {user.lastName}</strong><small>Personal account</small><em>{verified ? "Identity verified" : "Not yet verified"}</em></div>
        <nav>{[["Profile", "home"], ["Security", "settings"], ["Limits", "chart"]].map(([name, icon]) =>
          <button key={name} className={section === name ? "active" : ""} onClick={() => setSection(name)}><Icon name={icon} size={18} />{name}<Icon name="chevron" size={14} /></button>)}</nav>
        <button className="security-signout" onClick={onSignOut}>Sign out of DanBank</button>
      </aside>
      <main className="security-main">
        <header><div><span>ACCOUNT SETTINGS</span><h2>{section}</h2></div><button onClick={onClose}>×</button></header>
        {section === "Profile" && <ProfilePanel user={user} verified={verified} onVerify={() => { onClose(); onVerify?.(); }} notify={notify} />}
        {section === "Security" && <section className="settings-panel">
          <div className="settings-intro"><h3>Security and access</h3><p>Control how you authorize sensitive actions.</p></div>
          <SettingRow icon="card" title="Transaction PIN" text={pinSet ? "Required for payments and transfers" : "Not set. You need a PIN before you can send money"} action={pinSet ? "Change" : "Set up"} onClick={() => setPinDialog(true)} />
          <SettingRow icon="settings" title="Password" text="Change it any time. Other devices are signed out" action="Change" onClick={() => setPasswordDialog(true)} />
          <div className="security-health"><div><span>{pinSet ? "✓" : "!"}</span><p><strong>{pinSet ? "Transaction PIN is active" : "Set up your transaction PIN"}</strong>{pinSet ? "Every payment needs your PIN. Five wrong attempts lock it for 15 minutes." : "Without a PIN you cannot send money."}</p></div><b>{pinSet ? "100%" : "50%"}</b></div>
        </section>}
        {section === "Limits" && <LimitsPanel verified={verified} onVerify={() => { onClose(); onVerify?.(); }} />}
      </main>
      {notice && <button className="security-notice" onClick={() => setNotice("")}>✓ {notice}<span>×</span></button>}
    </section>
    {passwordDialog && <PasswordDialog onClose={() => setPasswordDialog(false)} onSaved={() => { setPasswordDialog(false); notify("Password changed. Other devices were signed out."); }} />}
    {pinDialog && <PinDialog hasPin={pinSet} onClose={() => setPinDialog(false)} onSaved={() => { setPinDialog(false); onPinSet?.(); notify("Transaction PIN saved securely."); }} />}
  </div>;
}

function SettingRow({ icon, title, text, action, onClick }) {
  return <button className="setting-row" onClick={onClick}><span className="setting-icon"><Icon name={icon} size={18} /></span><span><strong>{title}</strong><small>{text}</small></span><em>{action}</em><Icon name="chevron" size={15} /></button>;
}

function ProfilePanel({ user, verified, onVerify, notify }) {
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [form, setForm] = useState({ firstName: user.firstName || "", lastName: user.lastName || "", email: user.email || "", phoneNumber: user.phoneNumber || "" });
  const update = event => { setForm(value => ({ ...value, [event.target.name]: event.target.value })); setError(""); };

  const save = async () => {
    if (!editing) return setEditing(true);
    const changes = Object.fromEntries(Object.entries(form).filter(([key, value]) => value !== (user[key] || "")));
    if (!Object.keys(changes).length) return setEditing(false);
    setSaving(true);
    try {
      await securityApi.updateProfile(user.id, changes);
      Object.assign(user, changes); // keep this session's copy in step
      setEditing(false); notify("Profile changes saved.");
    } catch (requestError) {
      setError(Object.values(requestError.fields || {})[0] || requestError.message);
    } finally { setSaving(false); }
  };

  return <section className="profile-panel">
    <div className="settings-intro"><h3>Personal information</h3><p>Keep your details accurate for account security and verification.</p></div>
    <div className="profile-fields">
      <label>First name<input name="firstName" value={form.firstName} onChange={update} disabled={!editing} /></label>
      <label>Last name<input name="lastName" value={form.lastName} onChange={update} disabled={!editing} /></label>
      <label>Email address<input name="email" value={form.email} onChange={update} disabled={!editing} /></label>
      <label>Mobile number<input name="phoneNumber" value={form.phoneNumber} onChange={update} disabled={!editing} /></label>
    </div>
    {error && <p className="payment-error">{error}</p>}
    <div className="profile-verification"><span>{verified ? "✓" : "!"}</span><p><strong>{verified ? "Identity verified" : "Identity not verified"}</strong>{verified ? "Your identity check passed, so you have the higher transfer limits." : "Verify your identity to raise your transfer limits."}</p>{!verified && <button className="settings-primary" onClick={onVerify}>Verify now</button>}</div>
    <button className="settings-primary" onClick={save} disabled={saving}>{saving ? "Saving…" : editing ? "Save changes" : "Edit profile"}</button>
  </section>;
}

function LimitsPanel({ verified, onVerify }) {
  const [limits, setLimits] = useState(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => { accountsApi.quote(100).then(setLimits).catch(() => setFailed(true)); }, []);
  if (failed) return <section className="settings-panel"><p className="payment-error">We could not load your limits. Please try again.</p></section>;
  if (!limits) return <section className="settings-panel"><p>Loading your limits…</p></section>;
  return <section className="settings-panel">
    <div className="settings-intro"><h3>Transaction limits</h3><p>These limits apply to every transfer you make.</p></div>
    <div className="limit-overview"><span>Daily transfer limit</span><strong>{money.format(limits.dailyLimit)}</strong><small>Per transfer: {money.format(limits.perTransferLimit)}</small><div><i style={{ width: verified ? "100%" : "35%" }} /></div></div>
    {!verified && <p className="limit-caution">Your limits are lower until you verify your identity.</p>}
    {!verified && <button className="settings-primary" onClick={onVerify}>Verify to raise my limits</button>}
  </section>;
}

function PinDialog({ hasPin, onClose, onSaved }) {
  const [fields, setFields] = useState({ password: "", pin: "", confirm: "" });
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const set = (name, digitsOnly) => event => { const value = digitsOnly ? event.target.value.replace(/\D/g, "").slice(0, 4) : event.target.value; setFields(v => ({ ...v, [name]: value })); setError(""); };

  const submit = async event => {
    event.preventDefault();
    if (!fields.password) return setError("Enter your account password to continue.");
    if (!/^\d{4}$/.test(fields.pin) || fields.pin !== fields.confirm) return setError("Enter the same 4-digit PIN twice.");
    setSaving(true);
    try { await securityApi.setPin(fields.pin, fields.password); onSaved(); }
    catch (requestError) { setError(requestError.status === 403 ? "That password is incorrect." : Object.values(requestError.fields || {})[0] || requestError.message); }
    finally { setSaving(false); }
  };

  return <div className="credential-layer"><button onClick={onClose} aria-label="Close" />
    <form onSubmit={submit}>
      <span>••••</span><h3>{hasPin ? "Change" : "Set up"} transaction PIN</h3><p>You’ll use this 4-digit PIN to authorize payments. We ask for your password to make sure it’s you.</p>
      <label>Account password<input type="password" autoComplete="current-password" value={fields.password} onChange={set("password", false)} /></label>
      <label>New PIN<input type="password" inputMode="numeric" autoComplete="off" value={fields.pin} onChange={set("pin", true)} /></label>
      <label>Confirm new PIN<input type="password" inputMode="numeric" autoComplete="off" value={fields.confirm} onChange={set("confirm", true)} /></label>
      {error && <p className="payment-error">{error}</p>}
      <div><button type="button" onClick={onClose}>Cancel</button><button type="submit" disabled={saving}>{saving ? "Saving…" : "Save securely"}</button></div>
    </form>
  </div>;
}

function PasswordDialog({ onClose, onSaved }) {
  const [fields, setFields] = useState({ current: "", next: "", confirm: "" });
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const set = name => event => { setFields(v => ({ ...v, [name]: event.target.value })); setError(""); };

  const submit = async event => {
    event.preventDefault();
    if (!fields.current) return setError("Enter your current password.");
    if (fields.next.length < 12) return setError("Your new password must be at least 12 characters.");
    if (fields.next !== fields.confirm) return setError("The new passwords don’t match.");
    setSaving(true);
    try { await securityApi.changePassword(fields.current, fields.next); onSaved(); }
    catch (requestError) { setError(requestError.status === 403 ? "Your current password is incorrect." : Object.values(requestError.fields || {})[0] || requestError.message); }
    finally { setSaving(false); }
  };

  return <div className="credential-layer"><button onClick={onClose} aria-label="Close" />
    <form onSubmit={submit}>
      <span>✓</span><h3>Change password</h3><p>Use 12 or more characters with upper and lower case, a number and a symbol. You’ll stay signed in here; other devices are signed out.</p>
      <label>Current password<input type="password" autoComplete="current-password" value={fields.current} onChange={set("current")} /></label>
      <label>New password<input type="password" autoComplete="new-password" value={fields.next} onChange={set("next")} /></label>
      <label>Confirm new password<input type="password" autoComplete="new-password" value={fields.confirm} onChange={set("confirm")} /></label>
      {error && <p className="payment-error">{error}</p>}
      <div><button type="button" onClick={onClose}>Cancel</button><button type="submit" disabled={saving}>{saving ? "Saving…" : "Save securely"}</button></div>
    </form>
  </div>;
}
