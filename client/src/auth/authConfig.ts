/**
 * Auth0 settings, read from Vite environment variables.
 *
 * <p>None of these are secrets. Everything a browser holds is public - a "secret" in a
 * frontend bundle is only obfuscated, never hidden. That is precisely why the SPA uses the
 * Authorization Code flow with PKCE rather than a client secret, and why the OpenWeatherMap
 * key lives on the server instead.
 */
export const authConfig = {
  domain: import.meta.env.VITE_AUTH0_DOMAIN ?? '',
  clientId: import.meta.env.VITE_AUTH0_CLIENT_ID ?? '',
  audience: import.meta.env.VITE_AUTH0_AUDIENCE ?? '',
}

/**
 * Whether Auth0 is configured.
 *
 * When it is not, the app runs unauthenticated against a server started with
 * `APP_SECURITY_MODE=permissive`, so the dashboard can be demonstrated before a tenant
 * exists. The UI shows a clear banner in that state so it can never be mistaken for the
 * secured configuration.
 */
export const isAuthEnabled = Boolean(authConfig.domain && authConfig.clientId)

export const apiBaseUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8081'
