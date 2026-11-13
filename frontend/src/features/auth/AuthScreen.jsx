import { useState } from "react";
import Brand from "../../components/Brand.jsx";
import Icon from "../../components/Icon.jsx";
import AccountRecovery from "../recovery/AccountRecovery.jsx";
import { authApi } from "../../services/api.js";

const initialForm = { firstName: "", lastName: "", email: "", phone: "", password: "", consent: false };

export default function AuthScreen({ onAuthenticated }) {
  const [mode, setMode] = useState("login");
  const [step, setStep] = useState(1);
  const [showPassword, setShowPassword] = useState(false);
  const [form, setForm] = useState(initialForm);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const update = ({ target }) => {
    setForm(value => ({ ...value, [target.name]: target.type === "checkbox" ? target.checked : target.value }));
    setError("");
  };
  const enter = session => onAuthenticated({ ...session.user, accessToken: session.accessToken, refreshToken: session.refreshToken, expiresInSeconds: session.expiresInSeconds });
  const demo = () => onAuthenticated({ firstName: "David", lastName: "Adekunle", email: "demo@danbank.test", demo: true });
  const login = async event => { event.preventDefault(); if (!form.email || !form.password) return setError("Enter your email and password to continue."); setLoading(true); try { enter(await authApi.login(form.email, form.password)); } catch (requestError) { setError(requestError.status === 401 ? "The email or password you entered is incorrect." : requestError.message); } finally { setLoading(false); } };
  const register = async event => {
    event.preventDefault();
    if (step === 1 && (!form.firstName || !form.lastName || !form.email)) return setError("Complete all personal details to continue.");
    if (step === 2 && (!form.phone || form.password.length < 12)) return setError("Enter a Nigerian mobile number and a password of at least 12 characters.");
    if (step === 3 && !form.consent) return setError("Please accept the terms to open your account.");
    if (step < 3) return setStep(value => value + 1);
    setLoading(true);
    try { await authApi.signup({ firstName:form.firstName,lastName:form.lastName,email:form.email,phoneNumber:form.phone.replace(/\s/g,""),password:form.password,termsAccepted:form.consent }); enter(await authApi.login(form.email,form.password)); }
    catch (requestError) { setError(requestError.status === 409 ? "An account already exists with this email or phone number." : requestError.message); }
    finally { setLoading(false); }
  };
  const changeMode = value => { setMode(value); setStep(1); setError(""); };

  if (mode === "recovery") return <AccountRecovery onBack={() => changeMode("login")} />;

  return <main className="auth-page">
    <section className="auth-story">
      <Brand />
      <div className="story-copy"><span className="secure-pill">Secure digital banking</span><h1>Bank confidently.<br/><em>Live freely.</em></h1><p>Everything you need to manage, move, and grow your money—beautifully simple and protected around the clock.</p><div className="trust-row"><span>256-bit encryption</span><span>Protected access</span><span>24/7 support</span></div></div>
      <div className="story-orbit"><i/><i/><i/></div><small className="sandbox-note">DANBANK SANDBOX • PROTOTYPE ENVIRONMENT</small>
    </section>
    <section className="auth-panel"><div className="auth-card"><Brand mobile />
      {mode === "login" ? <LoginForm form={form} update={update} error={error} loading={loading} showPassword={showPassword} setShowPassword={setShowPassword} onSubmit={login} onDemo={demo} onRegister={() => changeMode("register")} onRecover={() => changeMode("recovery")} />
      : <RegisterForm form={form} update={update} error={error} loading={loading} step={step} setStep={setStep} showPassword={showPassword} setShowPassword={setShowPassword} onSubmit={register} onLogin={() => changeMode("login")} />}
    </div><p className="auth-legal">Protected by industry-standard encryption. Never share your password or transaction PIN.</p></section>
  </main>;
}

function LoginForm({ form, update, error, loading, showPassword, setShowPassword, onSubmit, onDemo, onRegister, onRecover }) {
  return <><div className="auth-heading"><span>WELCOME BACK</span><h2>Sign in to DanBank</h2><p>Access your account securely.</p></div><form onSubmit={onSubmit} noValidate>
    <label>Email address<input name="email" type="email" value={form.email} onChange={update} placeholder="name@example.com" /></label>
    <PasswordInput form={form} update={update} visible={showPassword} toggle={() => setShowPassword(v => !v)} />
    <div className="form-options"><label><input type="checkbox"/> Remember this device</label><button type="button" onClick={onRecover}>Forgot password?</button></div>{error && <p className="form-error" role="alert">{error}</p>}<button className="primary-auth" type="submit" disabled={loading}>{loading ? "Verifying securely…" : <>Sign in securely <Icon name="chevron" size={18}/></>}</button>
  </form><button className="demo-link" onClick={onDemo}>Explore the demo dashboard</button><p className="auth-switch">New to DanBank? <button onClick={onRegister}>Open an account</button></p></>;
}

function RegisterForm({ form, update, error, loading, step, setStep, showPassword, setShowPassword, onSubmit, onLogin }) {
  const titles = ["Let’s get to know you", "Secure your account", "Review and continue"];
  return <><div className="auth-heading"><span>OPEN AN ACCOUNT</span><h2>{titles[step-1]}</h2><p>Step {step} of 3 • It only takes a few minutes.</p></div><div className="stepper">{[1,2,3].map(n => <span key={n} className={n <= step ? "done" : ""}/>)}</div><form onSubmit={onSubmit} noValidate>
    {step === 1 && <><div className="field-row"><label>First name<input name="firstName" value={form.firstName} onChange={update} placeholder="David"/></label><label>Last name<input name="lastName" value={form.lastName} onChange={update} placeholder="Adekunle"/></label></div><label>Email address<input name="email" type="email" value={form.email} onChange={update} placeholder="name@example.com"/></label></>}
    {step === 2 && <><label>Mobile number<input name="phone" value={form.phone} onChange={update} placeholder="0803 123 4567"/></label><PasswordInput form={form} update={update} visible={showPassword} toggle={() => setShowPassword(v => !v)} create/><small className="input-hint">Use 12+ characters with uppercase, lowercase, a number and symbol.</small></>}
    {step === 3 && <div className="review-box"><div><span>Account type</span><strong>Personal savings</strong></div><div><span>Email</span><strong>{form.email}</strong></div><label className="consent"><input name="consent" type="checkbox" checked={form.consent} onChange={update}/><span>I agree to DanBank’s terms and consent to identity verification. This is currently a prototype account.</span></label></div>}
    {error && <p className="form-error">{error}</p>}<div className="register-actions">{step > 1 && <button className="back-auth" type="button" onClick={() => setStep(v => v-1)}>Back</button>}<button className="primary-auth" type="submit">{step === 3 ? "Create demo account" : "Continue"}<Icon name="chevron" size={18}/></button></div>
  </form><p className="auth-switch">Already have an account? <button onClick={onLogin}>Sign in</button></p></>;
}

function PasswordInput({ form, update, visible, toggle, create }) {
  return <label>{create ? "Create password" : "Password"}<div className="password-field"><input name="password" type={visible ? "text" : "password"} value={form.password} onChange={update} placeholder={create ? "At least 8 characters" : "Enter your password"}/><button type="button" onClick={toggle}><Icon name={visible ? "eyeOff" : "eye"} size={18}/></button></div></label>;
}
