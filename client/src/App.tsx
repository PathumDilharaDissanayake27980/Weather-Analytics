import { useAuth0 } from '@auth0/auth0-react'
import { isAuthEnabled } from './auth/authConfig'
import { Dashboard } from './components/Dashboard'
import { Login } from './components/Login'
import { LoadingState } from './components/LoadingState'

/**
 * Chooses between the login screen and the dashboard.
 *
 * <p>Worth being explicit about what this gate is and is not: it is a <em>convenience</em>.
 * It stops an unauthenticated visitor seeing an empty dashboard, nothing more. The actual
 * security boundary is the API, which rejects any request without a valid access token -
 * because anyone can bypass this component entirely by calling the API directly.
 */
export default function App() {
  const { isAuthenticated, isLoading } = useAuth0()

  // Auth0 not configured: run unsecured for local development. The dashboard shows a
  // prominent banner in this state so it cannot be mistaken for the secured build.
  if (!isAuthEnabled) {
    return <Dashboard />
  }

  if (isLoading) {
    return <LoadingState />
  }

  return isAuthenticated ? <Dashboard /> : <Login />
}
