import type { CityComfort, WeatherDashboard } from '../src/types/weather'

/**
 * Builders for test data. Defaults describe a pleasant city; each test overrides only the
 * fields it actually cares about, so what a test is asserting stays visible.
 */
export function makeCity(overrides: Partial<CityComfort> = {}): CityComfort {
  return {
    rank: 1,
    cityCode: '4930956',
    cityName: 'Boston',
    country: 'US',
    description: 'scattered clouds',
    icon: '03d',
    temperatureC: 19.07,
    feelsLikeC: 19.46,
    humidityPct: 93,
    windSpeedMs: 1.54,
    cloudinessPct: 36,
    pressureHpa: 1010,
    rainMmPerHour: 0,
    comfortScore: 80.3,
    comfortLabel: 'Excellent',
    subScores: { temperature: 0.94, humidity: 0.18, wind: 0.9, cloudiness: 1 },
    multipliers: { rain: 1, snow: 1, feelsGap: 0.99, heat: 1, cold: 1, gale: 1 },
    observedAt: '2026-09-04T11:54:56Z',
    ...overrides,
  }
}

export function makeDashboard(
  overrides: Partial<WeatherDashboard> = {},
): WeatherDashboard {
  const cities = overrides.cities ?? [
    makeCity({ rank: 1, cityName: 'Paris', cityCode: '2988507', comfortScore: 88.2, comfortLabel: 'Excellent' }),
    makeCity({ rank: 2, cityName: 'Oslo', cityCode: '3143244', comfortScore: 64.6, comfortLabel: 'Moderate', temperatureC: 13.64 }),
    makeCity({ rank: 3, cityName: 'Dubai', cityCode: '292223', comfortScore: 47.9, comfortLabel: 'Uncomfortable', temperatureC: 36.96 }),
  ]

  return {
    generatedAt: '2026-09-04T12:00:00Z',
    cacheStatus: 'MISS',
    cityCount: cities.length,
    cities,
    failures: [],
    ...overrides,
  }
}
