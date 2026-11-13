import Brand from "../../components/Brand.jsx";

/**
 * Resetting a forgotten password needs a way to reach you (email or SMS), and DanBank has no provider for that yet.
 * This page says so, instead of pretending to send a code.
 */
export default function AccountRecovery({ onBack }) {
  return <main className="recovery-page">
    <section className="recovery-story"><Brand />
      <div><span>ACCOUNT RECOVERY</span><h1>Forgot your<br />password?</h1>
        <p>Resetting a password by email or text message isn’t available yet. Here’s what you can do today.</p>
        <ul><li>We never request your transaction PIN</li><li>You can change your password in Settings while signed in</li></ul></div>
      <small>DANBANK SECURITY CENTRE</small></section>
    <section className="recovery-panel"><div className="recovery-card"><Brand mobile />
      <button className="recovery-back" onClick={onBack}>← Back</button>
      <div className="recovery-form">
        <div className="recovery-heading"><span>!</span><h2>Password reset isn’t available yet</h2><p>We can’t email or text you a reset code yet, so a forgotten password can’t be reset from here.</p></div>
        <div className="recovery-assurance"><span>✓</span><p><strong>Still signed in on another device?</strong>Open Settings, then Security, then Password, and choose a new one. You’ll need your current password.</p></div>
        <button className="recovery-primary" type="button" onClick={onBack}>Back to sign in</button>
      </div>
    </div></section>
  </main>;
}
