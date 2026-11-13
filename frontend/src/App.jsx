import { useState } from "react";
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

export default function App() {
  const [session, setSession] = useState(null);
  const appearance = useAppearance();

  return <>
    {session ? <Dashboard user={session} onSignOut={() => setSession(null)} /> : <AuthScreen onAuthenticated={setSession} />}
    <AppearanceMenu appearance={appearance} />
  </>;
}
