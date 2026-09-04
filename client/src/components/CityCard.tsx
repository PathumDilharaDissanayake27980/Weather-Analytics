import type { CityComfort } from '../types/weather'

interface Props {
  city: CityComfort
  expanded: boolean
  onToggle: () => void
}

const SUB_SCORE_LABELS: Record<string, string> = {
  temperature: 'Temperature',
  humidity: 'Humidity',
  wind: 'Wind',
  cloudiness: 'Cloudiness',
  pressure: 'Pressure',
}

const MULTIPLIER_LABELS: Record<string, string> = {
  rain: 'Rain',
  snow: 'Snow',
  feelsGap: 'Feels-like gap',
  heat: 'Extreme heat',
  cold: 'Extreme cold',
  gale: 'Gale-force wind',
}

/**
 * One city in the ranking.
 *
 * Expanding a card reveals the score breakdown. A constructed index with no ground truth is
 * only trustworthy if you can interrogate it, so the working is shown rather than hidden
 * behind a single opaque number.
 */
export function CityCard({ city, expanded, onToggle }: Props) {
  const activePenalties = Object.entries(city.multipliers).filter(([, value]) => value < 1)

  return (
    <article className={`city-card ${expanded ? 'is-expanded' : ''}`}>
      <button
        type="button"
        className="city-card__summary"
        onClick={onToggle}
        aria-expanded={expanded}
      >
        <span className="city-card__rank" aria-label={`Rank ${city.rank}`}>
          {city.rank}
        </span>

        <span className="city-card__identity">
          <span className="city-card__name">
            {city.cityName}
            {city.country && <span className="city-card__country">{city.country}</span>}
          </span>
          <span className="city-card__description">
            {city.icon && (
              <img
                className="city-card__icon"
                src={`https://openweathermap.org/img/wn/${city.icon}.png`}
                alt=""
                width={28}
                height={28}
                loading="lazy"
              />
            )}
            {city.description}
          </span>
        </span>

        <span className="city-card__metrics">
          <span className="metric">
            <span className="metric__value">{city.temperatureC.toFixed(1)}&deg;</span>
            <span className="metric__label">temp</span>
          </span>
          <span className="metric metric--muted">
            <span className="metric__value">{city.feelsLikeC.toFixed(1)}&deg;</span>
            <span className="metric__label">feels</span>
          </span>
          <span className="metric metric--muted">
            <span className="metric__value">{city.humidityPct}%</span>
            <span className="metric__label">humidity</span>
          </span>
          <span className="metric metric--muted">
            <span className="metric__value">{city.windSpeedMs.toFixed(1)}</span>
            <span className="metric__label">m/s</span>
          </span>
        </span>

        <span className={`score score--${city.comfortLabel.toLowerCase()}`}>
          <span className="score__value">{city.comfortScore.toFixed(1)}</span>
          <span className="score__label">{city.comfortLabel}</span>
        </span>

        <span className="city-card__chevron" aria-hidden="true">
          {expanded ? '−' : '+'}
        </span>
      </button>

      {expanded && (
        <div className="city-card__detail">
          <div className="breakdown">
            <h4 className="breakdown__title">Weighted sub-scores</h4>
            <ul className="breakdown__list">
              {Object.entries(city.subScores).map(([key, value]) => (
                <li key={key} className="breakdown__row">
                  <span className="breakdown__name">{SUB_SCORE_LABELS[key] ?? key}</span>
                  <span className="breakdown__bar" aria-hidden="true">
                    <span
                      className="breakdown__fill"
                      style={{ width: `${Math.round(value * 100)}%` }}
                    />
                  </span>
                  <span className="breakdown__value">{(value * 100).toFixed(0)}</span>
                </li>
              ))}
            </ul>
          </div>

          <div className="breakdown">
            <h4 className="breakdown__title">Penalties applied</h4>
            {activePenalties.length === 0 ? (
              <p className="breakdown__empty">
                None &mdash; no extreme conditions to penalise.
              </p>
            ) : (
              <ul className="breakdown__list">
                {activePenalties.map(([key, value]) => (
                  <li key={key} className="breakdown__row">
                    <span className="breakdown__name">{MULTIPLIER_LABELS[key] ?? key}</span>
                    <span className="breakdown__bar breakdown__bar--penalty" aria-hidden="true">
                      <span
                        className="breakdown__fill breakdown__fill--penalty"
                        style={{ width: `${Math.round((1 - value) * 100)}%` }}
                      />
                    </span>
                    <span className="breakdown__value">&times;{value.toFixed(2)}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <dl className="facts">
            <div className="facts__item">
              <dt>Cloudiness</dt>
              <dd>{city.cloudinessPct}%</dd>
            </div>
            <div className="facts__item">
              <dt>Pressure</dt>
              <dd>{city.pressureHpa} hPa</dd>
            </div>
            <div className="facts__item">
              <dt>Rainfall</dt>
              <dd>{city.rainMmPerHour > 0 ? `${city.rainMmPerHour} mm/h` : 'None'}</dd>
            </div>
            <div className="facts__item">
              <dt>Observed</dt>
              <dd>{new Date(city.observedAt).toLocaleTimeString()}</dd>
            </div>
          </dl>
        </div>
      )}
    </article>
  )
}
