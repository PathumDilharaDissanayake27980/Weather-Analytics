import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { Auth0Provider } from '@auth0/auth0-react'
import App from './App'
import { authConfig, isAuthEnabled } from './auth/authConfig'
import './styles/index.css'

const root = createRoot(document.getElementById('root')!)

/**
 * The Auth0Provider performs the Authorization Code flow with PKCE.
 *
 * <p>PKCE exists because a browser application cannot keep a client secret - anything in the
 * bundle is readable by anyone. Instead the app generates a random verifier, sends only its
 * hash when starting the login, and presents the original verifier when exchanging the code.
 * An attacker who intercepts the authorization code cannot use it without that verifier.
 *
 * <p>`audience` asks Auth0 for an access token intended for OUR API. Without it, Auth0
 * returns an opaque token for its own /userinfo endpoint, which our server cannot validate -
 * a very common first-time mistake.
 */
root.render(
  <StrictMode>
    {isAuthEnabled ? (
      <Auth0Provider
        domain={authConfig.domain}
        clientId={authConfig.clientId}
        authorizationParams={{
          redirect_uri: window.location.origin,
          audience: authConfig.audience,
        }}
        // Keeps the session across a page refresh. Tokens live in memory by default, so a
        // reload would otherwise force a round-trip to Auth0.
        cacheLocation="localstorage"
        useRefreshTokens
      >
        <App />
      </Auth0Provider>
    ) : (
      <App />
    )}
  </StrictMode>,
)
