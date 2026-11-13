import { useCallback, useEffect, useState } from "react";
import AuthScreen from "./features/auth/AuthScreen.jsx";
import Dashboard from "./features/dashboard/Dashboard.jsx";
import "../dashboard.css";
import "./features/support/support.css";
import "./features/verification/verification.css";
import "./features/savings/savings.css";
import "./features/statements/statements.css";
import "./features/recovery/recovery.css";
import "./features/scheduled/scheduled.css";
import "./features/accounts/accounts.css";
import "./features/insights/insights.css";
import useAppearance from "./hooks/useAppearance.js";
import AppearanceMenu from "./components/AppearanceMenu.jsx";
import { authApi, session as tokenStore } from "./services/api.js";

export default function App() {
  const [session, setSession] = useState(null);
  const [notice, setNotice] = useState("");
  const appearance = useAppearance();

  const signIn = useCallback(user => {
    tokenStore.set(user.demo ? null : user);
    setSession(user);
  }, []);
  const signOut = useCallback(async () => {
    await authApi.logout();
    tokenStore.clear();
    setSession(null);
  }, []);
  // A banking session should not stay open on an unattended screen: sign out after a period without any activity.
  useEffect(() => {
    if (!session || session.demo) return undefined;
    const minutes = Number(import.meta.env.VITE_IDLE_MINUTES) || 10;
    let timer;
    const expire = () => { signOut(); setNotice("You were signed out because you were inactive."); };
    const reset = () => { clearTimeout(timer); timer = setTimeout(expire, minutes * 60 * 1000); };
    const events = ["mousemove", "mousedown", "keydown", "touchstart", "scroll"];
    events.forEach(name => window.addEventListener(name, reset, { passive: true }));
    reset();
    return () => { clearTimeout(timer); events.forEach(name => window.removeEventListener(name, reset)); };
  }, [session, signOut]);

  // If a token can no longer be refreshed, send the person back to sign in.
  useEffect(() => { tokenStore.onExpired(() => setSession(null)); }, []);

  return <>
    {session ? <Dashboard user={session} onSignOut={signOut} /> : <AuthScreen onAuthenticated={user => { setNotice(""); signIn(user); }} notice={notice} />}
    <AppearanceMenu appearance={appearance} />
  </>;
}
