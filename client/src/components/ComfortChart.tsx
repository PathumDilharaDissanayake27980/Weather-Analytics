import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  ComposedChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { CityComfort } from '../types/weather'

interface Props {
  cities: CityComfort[]
}

const BAND_COLOURS: Record<string, string> = {
  Excellent: 'var(--score-excellent)',
  Comfortable: 'var(--score-comfortable)',
  Moderate: 'var(--score-moderate)',
  Uncomfortable: 'var(--score-uncomfortable)',
  Harsh: 'var(--score-harsh)',
}

/**
 * Comfort score per city, with temperature overlaid.
 *
 * The two series together make the index's behaviour visible: temperature alone does not
 * determine the ranking, which is the whole point of a multi-parameter score. A hot city and
 * a cold city can share a score for entirely different reasons.
 */
export function ComfortChart({ cities }: Props) {
  const data = cities.map((city) => ({
    name: city.cityName,
    score: city.comfortScore,
    temperature: city.temperatureC,
    label: city.comfortLabel,
  }))

  return (
    <section className="panel" aria-label="Comfort score by city">
      <h2 className="panel__title">Comfort score and temperature</h2>
      <div className="chart">
        <ResponsiveContainer width="100%" height={320}>
          <ComposedChart data={data} margin={{ top: 8, right: 16, bottom: 8, left: -12 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--border-subtle)" />
            <XAxis
              dataKey="name"
              tick={{ fill: 'var(--text-muted)', fontSize: 12 }}
              interval={0}
              angle={-35}
              textAnchor="end"
              height={70}
            />
            <YAxis
              yAxisId="score"
              domain={[0, 100]}
              tick={{ fill: 'var(--text-muted)', fontSize: 12 }}
              label={{
                value: 'Comfort',
                angle: -90,
                position: 'insideLeft',
                fill: 'var(--text-muted)',
                fontSize: 12,
              }}
            />
            <YAxis
              yAxisId="temp"
              orientation="right"
              tick={{ fill: 'var(--text-muted)', fontSize: 12 }}
              label={{
                value: '°C',
                angle: 90,
                position: 'insideRight',
                fill: 'var(--text-muted)',
                fontSize: 12,
              }}
            />
            <Tooltip
              contentStyle={{
                background: 'var(--surface-raised)',
                border: '1px solid var(--border)',
                borderRadius: 8,
                color: 'var(--text)',
              }}
              formatter={(value, name) => {
                const numeric = typeof value === 'number' ? value : Number(value)
                return name === 'Temperature'
                  ? [`${numeric.toFixed(1)} °C`, 'Temperature']
                  : [numeric.toFixed(1), 'Comfort']
              }}
            />
            <Legend wrapperStyle={{ fontSize: 12, color: 'var(--text-muted)' }} />
            <Bar yAxisId="score" dataKey="score" name="Comfort" radius={[4, 4, 0, 0]}>
              {data.map((entry) => (
                <Cell key={entry.name} fill={BAND_COLOURS[entry.label] ?? 'var(--accent)'} />
              ))}
            </Bar>
            <Line
              yAxisId="temp"
              type="monotone"
              dataKey="temperature"
              name="Temperature"
              stroke="var(--accent)"
              strokeWidth={2}
              dot={{ r: 3 }}
            />
          </ComposedChart>
        </ResponsiveContainer>
      </div>
    </section>
  )
}

/**
 * Per-parameter sub-scores across cities, so the shape of the index is visible at a glance.
 */
export function SubScoreChart({ cities }: Props) {
  const keys = Array.from(
    new Set(cities.flatMap((city) => Object.keys(city.subScores))),
  )

  const data = cities.map((city) => {
    const row: Record<string, string | number> = { name: city.cityName }
    for (const key of keys) {
      row[key] = Math.round((city.subScores[key] ?? 0) * 100)
    }
    return row
  })

  const seriesColours = [
    'var(--series-1)',
    'var(--series-2)',
    'var(--series-3)',
    'var(--series-4)',
    'var(--series-5)',
  ]

  return (
    <section className="panel" aria-label="Sub-scores by city">
      <h2 className="panel__title">What drives each score</h2>
      <div className="chart">
        <ResponsiveContainer width="100%" height={320}>
          <BarChart data={data} margin={{ top: 8, right: 16, bottom: 8, left: -12 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--border-subtle)" />
            <XAxis
              dataKey="name"
              tick={{ fill: 'var(--text-muted)', fontSize: 12 }}
              interval={0}
              angle={-35}
              textAnchor="end"
              height={70}
            />
            <YAxis domain={[0, 100]} tick={{ fill: 'var(--text-muted)', fontSize: 12 }} />
            <Tooltip
              contentStyle={{
                background: 'var(--surface-raised)',
                border: '1px solid var(--border)',
                borderRadius: 8,
                color: 'var(--text)',
              }}
            />
            <Legend wrapperStyle={{ fontSize: 12, color: 'var(--text-muted)' }} />
            {keys.map((key, index) => (
              <Bar
                key={key}
                dataKey={key}
                name={key.charAt(0).toUpperCase() + key.slice(1)}
                fill={seriesColours[index % seriesColours.length]}
                radius={[3, 3, 0, 0]}
              />
            ))}
          </BarChart>
        </ResponsiveContainer>
      </div>
    </section>
  )
}
