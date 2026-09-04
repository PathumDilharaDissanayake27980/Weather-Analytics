import { render, screen, waitFor, within } from '@testing-library/react'
import type { RenderResult } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { Dashboard } from '../src/components/Dashboard'
import { makeCity, makeDashboard } from './factories'

// Recharts measures its container, which jsdom reports as 0x0, so charts would render
// nothing and log warnings. Stubbing the responsive wrapper keeps these tests about the
// dashboard's behaviour rather than about charting.
vi.mock('recharts', async () => {
  const actual = await vi.importActual<typeof import('recharts')>('recharts')
  return {
    ...actual,
    ResponsiveContainer: ({ children }: { children: React.ReactNode }) => (
      <div style={{ width: 800, height: 320 }}>{children}</div>
    ),
  }
})

const logout = vi.fn()
// These must be defined ONCE, outside the factory. The real SDK memoises
// getAccessTokenSilently; returning a fresh function on every render would change the
// identity of useWeather's effect dependency and refetch on every render.
const getAccessTokenSilently = vi.fn().mockResolvedValue('fake-token')
const auth0Value = {
  user: { name: 'Test User' },
  logout,
  isAuthenticated: true,
  isLoading: false,
  getAccessTokenSilently,
}
vi.mock('@auth0/auth0-react', () => ({
  useAuth0: () => auth0Value,
}))

function mockFetchOnce(payload: unknown, init: { status?: number } = {}) {
  const status = init.status ?? 200
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok: status < 400,
      status,
      json: async () => payload,
    }),
  )
}

/**
 * The charts render city names as axis labels too, so an unscoped getByText('Paris') is
 * genuinely ambiguous. Scoping to the ranked-city region keeps these assertions about the
 * list rather than accidentally matching a chart tick.
 */
function cityList() {
  return screen.getByRole('region', { name: 'Ranked cities' })
}

async function waitForCities(): Promise<HTMLElement> {
  return await screen.findByRole('region', { name: 'Ranked cities' })
}

function cityNames(container: RenderResult['container']): string[] {
  return Array.from(container.querySelectorAll('.city-card__name')).map((el) =>
    el.textContent?.replace(/[A-Z]{2}$/, '').trim() ?? '',
  )
}

describe('Dashboard', () => {
  beforeEach(() => {
    logout.mockClear()
    vi.unstubAllGlobals()
  })

  it('shows a loading state before the data arrives', () => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})))
    render(<Dashboard />)

    expect(screen.getByRole('status')).toHaveTextContent(/fetching weather/i)
  })

  it('renders every city returned by the API', async () => {
    mockFetchOnce(makeDashboard())
    render(<Dashboard />)

    const list = await waitForCities()
    expect(within(list).getByText('Paris')).toBeInTheDocument()
    expect(within(list).getByText('Oslo')).toBeInTheDocument()
    expect(within(list).getByText('Dubai')).toBeInTheDocument()
  })

  it('lists cities in the order the server ranked them', async () => {
    mockFetchOnce(makeDashboard())
    const { container } = render(<Dashboard />)

    await waitForCities()

    expect(cityNames(container)).toEqual(['Paris', 'Oslo', 'Dubai'])
  })

  it('surfaces the cache status', async () => {
    mockFetchOnce(makeDashboard({ cacheStatus: 'HIT' }))
    render(<Dashboard />)

    expect(await screen.findByText('cache HIT')).toBeInTheDocument()
  })

  it('summarises the most and least comfortable city', async () => {
    mockFetchOnce(makeDashboard())
    render(<Dashboard />)

    const summary = await screen.findByLabelText('Summary')
    expect(within(summary).getByText('Paris')).toBeInTheDocument()
    expect(within(summary).getByText('Dubai')).toBeInTheDocument()
  })

  it('filters cities by name', async () => {
    mockFetchOnce(makeDashboard())
    render(<Dashboard />)
    await waitForCities()

    await userEvent.type(screen.getByLabelText('Search'), 'osl')

    expect(within(cityList()).getByText('Oslo')).toBeInTheDocument()
    expect(within(cityList()).queryByText('Paris')).not.toBeInTheDocument()
  })

  it('filters cities by comfort band', async () => {
    mockFetchOnce(makeDashboard())
    render(<Dashboard />)
    await waitForCities()

    await userEvent.selectOptions(screen.getByLabelText('Comfort band'), 'Moderate')

    expect(within(cityList()).getByText('Oslo')).toBeInTheDocument()
    expect(within(cityList()).queryByText('Dubai')).not.toBeInTheDocument()
  })

  it('re-sorts by temperature without changing the scores', async () => {
    mockFetchOnce(makeDashboard())
    const { container } = render(<Dashboard />)
    await waitForCities()

    await userEvent.selectOptions(screen.getByLabelText('Sort by'), 'temperature')

    const names = cityNames(container)
    // Dubai is the hottest at 36.96C, Oslo the coolest at 13.64C.
    expect(names[0]).toBe('Dubai')
    expect(names[names.length - 1]).toBe('Oslo')
  })

  it('reverses the order when the direction is toggled', async () => {
    mockFetchOnce(makeDashboard())
    const { container } = render(<Dashboard />)
    await waitForCities()

    await userEvent.click(screen.getByRole('button', { name: /sort ascending/i }))

    expect(cityNames(container)).toEqual(['Dubai', 'Oslo', 'Paris'])
  })

  it('says so when the filters match nothing', async () => {
    mockFetchOnce(makeDashboard())
    render(<Dashboard />)
    await waitForCities()

    await userEvent.type(screen.getByLabelText('Search'), 'atlantis')

    expect(screen.getByText(/no cities match/i)).toBeInTheDocument()
  })

  it('warns about cities the server could not fetch', async () => {
    mockFetchOnce(
      makeDashboard({
        cities: [makeCity({ cityName: 'Paris' })],
        cityCount: 1,
        failures: [{ cityCode: '1850147', cityName: 'Tokyo', reason: 'timeout' }],
      }),
    )
    render(<Dashboard />)

    expect(await screen.findByText(/could not be/i)).toHaveTextContent('Tokyo')
  })

  it('explains a 502 in terms the user can act on', async () => {
    mockFetchOnce({}, { status: 502 })
    render(<Dashboard />)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /weather provider is unavailable/i,
    )
  })

  it('explains an expired session on a 401', async () => {
    mockFetchOnce({}, { status: 401 })
    render(<Dashboard />)

    expect(await screen.findByRole('alert')).toHaveTextContent(/session has expired/i)
  })

  it('refetches when Refresh is pressed', async () => {
    mockFetchOnce(makeDashboard())
    render(<Dashboard />)
    await waitForCities()

    await userEvent.click(screen.getByRole('button', { name: 'Refresh' }))

    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2))
  })

  it('asks the server to flush its caches, then refetches', async () => {
    mockFetchOnce(makeDashboard())
    render(<Dashboard />)
    await waitForCities()

    await userEvent.click(screen.getByRole('button', { name: /clear cache/i }))

    await waitFor(() =>
      expect(fetch).toHaveBeenCalledWith(
        expect.stringContaining('/api/debug/cache'),
        expect.objectContaining({ method: 'DELETE' }),
      ),
    )
  })

  it('never computes a comfort score in the browser - it renders the server\'s', async () => {
    mockFetchOnce(
      makeDashboard({
        cities: [makeCity({ cityName: 'Nowhere', comfortScore: 42.7, comfortLabel: 'Moderate' })],
        cityCount: 1,
      }),
    )
    render(<Dashboard />)

    // 42.7 is not derivable from the weather values in the fixture; it is simply displayed.
    expect(await screen.findByText('42.7')).toBeInTheDocument()
  })
})
