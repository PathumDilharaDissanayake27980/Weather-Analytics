/**
 * Shape of what OUR API returns - not OpenWeatherMap's shape.
 *
 * The browser never talks to OpenWeatherMap directly: the API key must stay on the server,
 * and the Comfort Index must be computed there. So the frontend only ever sees these types.
 */

export interface CityComfort {
  rank: number
  cityCode: string
  cityName: string
  country: string | null
  description: string
  icon: string | null
  temperatureC: number
  feelsLikeC: number
  humidityPct: number
  windSpeedMs: number
  cloudinessPct: number
  pressureHpa: number
  rainMmPerHour: number
  /** 0-100, computed server-side. */
  comfortScore: number
  comfortLabel: ComfortLabel
  /** Per-parameter sub-score, 0-1. Lets the UI explain a ranking. */
  subScores: Record<string, number>
  /** Per-penalty multiplier, 0-1. 1.0 means the penalty did not apply. */
  multipliers: Record<string, number>
  observedAt: string
}

export type ComfortLabel =
  | 'Excellent'
  | 'Comfortable'
  | 'Moderate'
  | 'Uncomfortable'
  | 'Harsh'

export interface CityFailure {
  cityCode: string
  cityName: string
  reason: string
}

export interface WeatherDashboard {
  generatedAt: string
  /** HIT or MISS for the server's processed-result cache. */
  cacheStatus: 'HIT' | 'MISS'
  cityCount: number
  cities: CityComfort[]
  /** Cities the server could not fetch. The dashboard degrades rather than failing. */
  failures: CityFailure[]
}

export type SortKey = 'rank' | 'score' | 'name' | 'temperature' | 'humidity' | 'wind'
export type SortDirection = 'asc' | 'desc'
