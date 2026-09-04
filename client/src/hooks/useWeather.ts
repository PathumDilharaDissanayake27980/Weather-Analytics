import { useCallback, useEffect, useState } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { apiBaseUrl, authConfig, isAuthEnabled } from '../auth/authConfig'
import type { WeatherDashboard } from '../types/weather'

interface UseWeatherResult {
  data: WeatherDashboard | null
  loading: boolean
  error: string | null
  refresh: () => void
  /** Clears the server-side caches, then refetches. Makes MISS/HIT demonstrable. */
  clearCache: () => Promise<void>
}

/**
 * Fetches the ranked dashboard from our API, attaching an Auth0 access token.
 *
 * <p>The token comes from `getAccessTokenSilently()`, which returns a cached token or
 * quietly renews it against Auth0. It is requested for a specific `audience` - our API's
 * identifier - because a token minted for a different audience is one the server will
 * (correctly) reject.
 *
 * <p>Note what this hook does NOT do: it never computes a comfort score. Ranking and scoring
 * are the server's job, both because the assignment requires it and because a client-side
 * score would differ between browsers and could be tampered with.
 */
export function useWeather(): UseWeatherResult {
  const { getAccessTokenSilently } = useAuth0()
  const [data, setData] = useState<WeatherDashboard | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadToken, setReloadToken] = useState(0)

  const authHeaders = useCallback(async (): Promise<HeadersInit> => {
    if (!isAuthEnabled) return {}
    const token = await getAccessTokenSilently({
      authorizationParams: { audience: authConfig.audience },
    })
    return { Authorization: `Bearer ${token}` }
  }, [getAccessTokenSilently])

  useEffect(() => {
    let cancelled = false

    async function load() {
      setLoading(true)
      setError(null)
      try {
        const response = await fetch(`${apiBaseUrl}/api/weather`, {
          headers: await authHeaders(),
        })

        if (response.status === 401) {
          throw new Error('Your session has expired. Please sign in again.')
        }
        if (response.status === 502) {
          throw new Error('The weather provider is unavailable. Please try again shortly.')
        }
        if (!response.ok) {
          throw new Error(`Request failed with status ${response.status}`)
        }

        const payload: WeatherDashboard = await response.json()
        // A late response from a superseded request must not overwrite fresher state.
        if (!cancelled) setData(payload)
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Something went wrong.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void load()
    return () => {
      cancelled = true
    }
  }, [authHeaders, reloadToken])

  const refresh = useCallback(() => setReloadToken((n) => n + 1), [])

  const clearCache = useCallback(async () => {
    await fetch(`${apiBaseUrl}/api/debug/cache`, {
      method: 'DELETE',
      headers: await authHeaders(),
    })
    setReloadToken((n) => n + 1)
  }, [authHeaders])

  return { data, loading, error, refresh, clearCache }
}
