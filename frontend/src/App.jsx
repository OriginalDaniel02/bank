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
  // If a token can no longer be refreshed, send the person back to sign in.
  useEffect(() => { tokenStore.onExpired(() => setSession(null)); }, []);

  return <>
    {session ? <Dashboard user={session} onSignOut={signOut} /> : <AuthScreen onAuthenticated={signIn} />}
    <AppearanceMenu appearance={appearance} />
  </>;
}
