import { lazy, Suspense, useMemo, useState } from 'react'
import { useAuth0 } from '@auth0/auth0-react'
import { isAuthEnabled } from '../auth/authConfig'
import { useWeather } from '../hooks/useWeather'
import type { CityComfort, ComfortLabel, SortDirection, SortKey } from '../types/weather'
import { CityCard } from './CityCard'
import { Controls } from './Controls'
import { ErrorState } from './ErrorState'
import { LoadingState } from './LoadingState'
import { ThemeToggle } from './ThemeToggle'

/**
 * Recharts is by far the heaviest dependency in the bundle - roughly two thirds of it.
 * Loading it lazily means the ranked list, which is what the page is actually for, paints
 * without waiting for charting code. The charts stream in a moment later.
 */
const ComfortChart = lazy(() =>
  import('./ComfortChart').then((m) => ({ default: m.ComfortChart })),
)
const SubScoreChart = lazy(() =>
  import('./ComfortChart').then((m) => ({ default: m.SubScoreChart })),
)

function compare(a: CityComfort, b: CityComfort, key: SortKey): number {
  switch (key) {
    case 'rank':
      return a.rank - b.rank
    case 'score':
      return b.comfortScore - a.comfortScore
    case 'name':
      return a.cityName.localeCompare(b.cityName)
    case 'temperature':
      return b.temperatureC - a.temperatureC
    case 'humidity':
      return b.humidityPct - a.humidityPct
    case 'wind':
      return b.windSpeedMs - a.windSpeedMs
  }
}

export function Dashboard() {
  const { data, loading, error, refresh, clearCache } = useWeather()
  const { user, logout } = useAuth0()

  const [search, setSearch] = useState('')
  const [sortKey, setSortKey] = useState<SortKey>('rank')
  const [sortDirection, setSortDirection] = useState<SortDirection>('asc')
  const [labelFilter, setLabelFilter] = useState<ComfortLabel | 'all'>('all')
  const [expanded, setExpanded] = useState<string | null>(null)

  const cities = data?.cities ?? []

  const visible = useMemo(() => {
    const term = search.trim().toLowerCase()
    const filtered = cities.filter((city) => {
      const matchesSearch = term === '' || city.cityName.toLowerCase().includes(term)
      const matchesLabel = labelFilter === 'all' || city.comfortLabel === labelFilter
      return matchesSearch && matchesLabel
    })

    const sorted = [...filtered].sort((a, b) => compare(a, b, sortKey))
    return sortDirection === 'asc' ? sorted : sorted.reverse()
  }, [cities, search, sortKey, sortDirection, labelFilter])

  return (
    <div className="app">
      <header className="header">
        <div className="header__brand">
          <h1 className="header__title">Weather Comfort Index</h1>
          <p className="header__subtitle">
            Cities ranked most to least comfortable, scored on the server
          </p>
        </div>

        <div className="header__actions">
          {data && (
            <span
              className={`badge badge--${data.cacheStatus.toLowerCase()}`}
              title="Cache status of the processed dashboard payload"
            >
              cache {data.cacheStatus}
            </span>
          )}
          <ThemeToggle />
          <button type="button" className="button" onClick={refresh} disabled={loading}>
            Refresh
          </button>
          <button
            type="button"
            className="button button--ghost"
            onClick={() => void clearCache()}
            disabled={loading}
            title="Clears the server-side caches so the next request is a guaranteed MISS"
          >
            Clear cache
          </button>
          {isAuthEnabled && (
            <button
              type="button"
              className="button button--ghost"
              onClick={() =>
                logout({ logoutParams: { returnTo: window.location.origin } })
              }
            >
              Sign out{user?.name ? ` (${user.name})` : ''}
            </button>
          )}
        </div>
      </header>

      {!isAuthEnabled && (
        <p className="notice notice--warning">
          <strong>Authentication is disabled.</strong> Auth0 is not configured in this build,
          so the dashboard is running unsecured for local development. See the README to enable it.
        </p>
      )}

      {loading && !data && <LoadingState />}
      {error && <ErrorState message={error} onRetry={refresh} />}

      {data && (
        <>
          {data.failures.length > 0 && (
            <p className="notice notice--warning">
              {data.failures.length} of {data.failures.length + data.cityCount} cities could not be
              fetched and are omitted from the ranking:{' '}
              {data.failures.map((f) => f.cityName).join(', ')}.
            </p>
          )}

          <section className="summary" aria-label="Summary">
            <div className="summary__item">
              <span className="summary__value">{data.cityCount}</span>
              <span className="summary__label">cities ranked</span>
            </div>
            <div className="summary__item">
              <span className="summary__value">{cities[0]?.cityName ?? '—'}</span>
              <span className="summary__label">most comfortable</span>
            </div>
            <div className="summary__item">
              <span className="summary__value">
                {cities[cities.length - 1]?.cityName ?? '—'}
              </span>
              <span className="summary__label">least comfortable</span>
            </div>
            <div className="summary__item">
              <span className="summary__value">
                {new Date(data.generatedAt).toLocaleTimeString()}
              </span>
              <span className="summary__label">data computed at</span>
            </div>
          </section>

          <Controls
            search={search}
            onSearchChange={setSearch}
            sortKey={sortKey}
            onSortKeyChange={setSortKey}
            sortDirection={sortDirection}
            onSortDirectionToggle={() =>
              setSortDirection((d) => (d === 'asc' ? 'desc' : 'asc'))
            }
            labelFilter={labelFilter}
            onLabelFilterChange={setLabelFilter}
            resultCount={visible.length}
            totalCount={cities.length}
          />

          {visible.length === 0 ? (
            <p className="notice">No cities match the current filters.</p>
          ) : (
            <section className="city-list" aria-label="Ranked cities">
              {visible.map((city) => (
                <CityCard
                  key={city.cityCode}
                  city={city}
                  expanded={expanded === city.cityCode}
                  onToggle={() =>
                    setExpanded((current) =>
                      current === city.cityCode ? null : city.cityCode,
                    )
                  }
                />
              ))}
            </section>
          )}

          {cities.length > 0 && (
            <Suspense fallback={<div className="charts-placeholder">Loading charts…</div>}>
              <div className="charts">
                <ComfortChart cities={cities} />
                <SubScoreChart cities={cities} />
              </div>
            </Suspense>
          )}
        </>
      )}

      <footer className="footer">
        <p>
          Comfort Index computed server-side from OpenWeatherMap data. Scores are a constructed
          index, not a measurement &mdash; see the README for the formula and its limitations.
        </p>
      </footer>
    </div>
  )
}
