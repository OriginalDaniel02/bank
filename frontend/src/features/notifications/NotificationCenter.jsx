import { useEffect, useMemo, useState } from "react";
import Icon from "../../components/Icon.jsx";
import { notificationsApi } from "../../services/api.js";

// How a backend notification type is shown.
const kinds = {
  CREDIT: { category: "Payments", icon: "arrowDown", tone: "success" },
  DEBIT: { category: "Payments", icon: "send", tone: "info" },
  KYC: { category: "Account", icon: "settings", tone: "success" },
  SECURITY: { category: "Security", icon: "settings", tone: "warning" },
  SYSTEM: { category: "Account", icon: "receipt", tone: "neutral" },
};
const describe = item => {
  const kind = kinds[item.type] || kinds.SYSTEM;
  const time = new Date(item.createdAt).toLocaleString("en-NG", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });
  return { ...kind, id: item.id, title: item.title, message: item.message, unread: !item.read, time };
};

export default function NotificationCenter({ onClose, onChanged }) {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [filter, setFilter] = useState("All");
  const [selected, setSelected] = useState(null);

  useEffect(() => {
    let active = true;
    notificationsApi.list({ size: 50 })
      .then(page => { if (active) setItems(page.content.map(describe)); })
      .catch(requestError => { if (active) setError(requestError.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  const visible = useMemo(
    () => filter === "All" ? items : filter === "Unread" ? items.filter(i => i.unread) : items.filter(i => i.category === filter),
    [items, filter],
  );
  const unread = items.filter(i => i.unread).length;

  const open = item => {
    setSelected({ ...item, unread: false });
    if (!item.unread) return;
    setItems(value => value.map(i => i.id === item.id ? { ...i, unread: false } : i));
    notificationsApi.markRead(item.id).then(() => onChanged?.()).catch(() => {});
  };
  const markAll = () => {
    setItems(value => value.map(i => ({ ...i, unread: false })));
    notificationsApi.markAllRead().then(() => onChanged?.()).catch(() => {});
  };

  return <div className="notifications-dialog" role="dialog" aria-modal="true">
    <button className="notifications-backdrop" onClick={onClose} />
    <section className="notifications-window">
      <header className="notifications-header"><div><span>INBOX</span><h2>Notifications {unread > 0 && <b>{unread}</b>}</h2><p>Important activity and updates from DanBank.</p></div><div><button onClick={onClose}>×</button></div></header>
      <div className="notification-tools"><div>{["All", "Unread", "Payments", "Security", "Account"].map(name => <button key={name} className={filter === name ? "active" : ""} onClick={() => setFilter(name)}>{name}</button>)}</div>{unread > 0 && <button onClick={markAll}>✓ Mark all as read</button>}</div>
      <div className="notification-list">
        {visible.map(item => <button key={item.id} className={item.unread ? "unread" : ""} onClick={() => open(item)}><span className={`notification-icon ${item.tone}`}><Icon name={item.icon} size={18} /></span><span className="notification-copy"><strong>{item.title}{item.unread && <i />}</strong><p>{item.message}</p><small>{item.time}</small></span><Icon name="chevron" size={16} /></button>)}
        {loading && <div className="notifications-empty"><p>Loading your notifications…</p></div>}
        {error && <div className="notifications-empty"><p className="payment-error">{error}</p></div>}
        {!loading && !error && !visible.length && <div className="notifications-empty"><Icon name="bell" size={25} /><h3>You’re all caught up</h3><p>There are no notifications in this category.</p></div>}
      </div>
      <footer><span>✓</span>Alerts are sent when money moves in or out of your account.</footer>
    </section>
    {selected && <div className="notification-layer"><button onClick={() => setSelected(null)} /><aside className="notification-detail"><header><span>NOTIFICATION DETAILS</span><button onClick={() => setSelected(null)}>×</button></header><div className={`detail-notification-icon ${selected.tone}`}><Icon name={selected.icon} size={22} /></div><h3>{selected.title}</h3><p>{selected.message}</p><dl><div><dt>Date</dt><dd>{selected.time}</dd></div><div><dt>Category</dt><dd>{selected.category}</dd></div></dl></aside></div>}
  </div>;
}
