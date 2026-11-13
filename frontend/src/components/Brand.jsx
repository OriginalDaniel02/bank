export default function Brand({ mobile = false }) {
  return (
    <div className={mobile ? "auth-mobile-brand" : "brand"} aria-label="DanBank">
      <span className="brand-mark"><span /></span>
      {mobile ? <b>DanBank</b> : <span className="brand-name">Dan<span>Bank</span></span>}
    </div>
  );
}
