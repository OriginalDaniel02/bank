const paths = {
  home: <><path d="M3 10.8 12 3l9 7.8"/><path d="M5 9.8V21h14V9.8M9 21v-6h6v6"/></>,
  transfer: <><path d="M5 8h14M15 4l4 4-4 4M19 16H5M9 12l-4 4 4 4"/></>,
  card: <><rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 10h18M7 15h3"/></>,
  chart: <><path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/></>,
  support: <><path d="M4 14v-2a8 8 0 0 1 16 0v2M4 14h3v6H5a2 2 0 0 1-2-2v-2a2 2 0 0 1 1-2ZM20 14h-3v6h2a2 2 0 0 0 2-2v-2a2 2 0 0 0-1-2Z"/></>,
  settings: <><circle cx="12" cy="12" r="3"/><path d="M19 15a2 2 0 0 0 .4 2l-2.8 2.8a2 2 0 0 0-2-.4 2 2 0 0 0-1 1.6h-4a2 2 0 0 0-1-1.6 2 2 0 0 0-2 .4L3.8 17a2 2 0 0 0 .4-2 2 2 0 0 0-1.8-1v-4a2 2 0 0 0 1.8-1 2 2 0 0 0-.4-2l2.8-2.8a2 2 0 0 0 2 .4A2 2 0 0 0 9.6 3h4a2 2 0 0 0 1 1.6 2 2 0 0 0 2-.4L19.4 7a2 2 0 0 0-.4 2 2 2 0 0 0 1.8 1v4a2 2 0 0 0-1.8 1Z"/></>,
  bell: <><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/></>,
  eye: <><path d="M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12Z"/><circle cx="12" cy="12" r="2.5"/></>,
  eyeOff: <><path d="m3 3 18 18M10.7 6.1A10 10 0 0 1 12 6c6.5 0 10 6 10 6a16 16 0 0 1-2 2.6M6.6 6.6C3.6 8.4 2 12 2 12s3.5 6 10 6c1.4 0 2.6-.3 3.7-.7M9.9 9.9a3 3 0 0 0 4.2 4.2"/></>,
  send: <><path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/></>,
  plus: <path d="M12 5v14M5 12h14"/>,
  phone: <><rect x="7" y="2" width="10" height="20" rx="2"/><path d="M11 18h2"/></>,
  receipt: <><path d="M5 3h14v19l-3-2-4 2-4-2-3 2Z"/><path d="M8 8h8M8 12h8M8 16h5"/></>,
  more: <><circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/></>,
  search: <><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></>,
  arrowUp: <><path d="M12 19V5M6 11l6-6 6 6"/></>,
  arrowDown: <><path d="M12 5v14M18 13l-6 6-6-6"/></>,
  chevron: <path d="m9 18 6-6-6-6"/>,
};

export default function Icon({ name, size = 20 }) {
  return <svg className="icon" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>;
}
