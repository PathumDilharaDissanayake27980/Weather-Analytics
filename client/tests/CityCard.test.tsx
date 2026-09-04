import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CityCard } from '../src/components/CityCard'
import { makeCity } from './factories'

describe('CityCard', () => {
  it('shows every field the assignment requires for a city', () => {
    render(
      <CityCard
        city={makeCity({ rank: 3, cityName: 'Colombo', description: 'overcast clouds',
          temperatureC: 30.02, comfortScore: 61.9, comfortLabel: 'Moderate' })}
        expanded={false}
        onToggle={vi.fn()}
      />,
    )

    expect(screen.getByText('Colombo')).toBeInTheDocument()      // city name
    expect(screen.getByText('overcast clouds')).toBeInTheDocument() // description
    expect(screen.getByText('30.0°')).toBeInTheDocument()        // temperature
    expect(screen.getByText('61.9')).toBeInTheDocument()         // comfort score
    expect(screen.getByText('3')).toBeInTheDocument()            // rank
  })

  it('labels the comfort band', () => {
    render(<CityCard city={makeCity({ comfortLabel: 'Harsh' })} expanded={false} onToggle={vi.fn()} />)

    expect(screen.getByText('Harsh')).toBeInTheDocument()
  })

  it('hides the score breakdown until the card is expanded', () => {
    render(<CityCard city={makeCity()} expanded={false} onToggle={vi.fn()} />)

    expect(screen.queryByText('Weighted sub-scores')).not.toBeInTheDocument()
  })

  it('reveals the sub-scores when expanded', () => {
    render(<CityCard city={makeCity()} expanded onToggle={vi.fn()} />)

    expect(screen.getByText('Weighted sub-scores')).toBeInTheDocument()
    expect(screen.getByText('Temperature')).toBeInTheDocument()
    expect(screen.getByText('Humidity')).toBeInTheDocument()
  })

  it('lists only the penalties that actually applied', () => {
    render(
      <CityCard
        city={makeCity({
          multipliers: { rain: 1, snow: 1, feelsGap: 0.8, heat: 0.65, cold: 1, gale: 1 },
        })}
        expanded
        onToggle={vi.fn()}
      />,
    )

    expect(screen.getByText('Feels-like gap')).toBeInTheDocument()
    expect(screen.getByText('Extreme heat')).toBeInTheDocument()
    // A multiplier of 1.0 means the penalty did not apply, so it is noise on screen.
    expect(screen.queryByText('Extreme cold')).not.toBeInTheDocument()
    expect(screen.queryByText('Rain')).not.toBeInTheDocument()
  })

  it('says so explicitly when no penalties applied', () => {
    render(
      <CityCard
        city={makeCity({ multipliers: { rain: 1, snow: 1, feelsGap: 1, heat: 1, cold: 1, gale: 1 } })}
        expanded
        onToggle={vi.fn()}
      />,
    )

    expect(screen.getByText(/no extreme conditions to penalise/i)).toBeInTheDocument()
  })

  it('shows "None" rather than "0 mm/h" when it is not raining', () => {
    render(<CityCard city={makeCity({ rainMmPerHour: 0 })} expanded onToggle={vi.fn()} />)

    expect(screen.getByText('None')).toBeInTheDocument()
  })

  it('shows the rainfall rate when it is raining', () => {
    render(<CityCard city={makeCity({ rainMmPerHour: 3.87 })} expanded onToggle={vi.fn()} />)

    expect(screen.getByText('3.87 mm/h')).toBeInTheDocument()
  })

  it('calls onToggle when the summary is activated', async () => {
    const onToggle = vi.fn()
    render(<CityCard city={makeCity()} expanded={false} onToggle={onToggle} />)

    await userEvent.click(screen.getByRole('button'))

    expect(onToggle).toHaveBeenCalledOnce()
  })

  it('exposes its expanded state to assistive technology', () => {
    const { rerender } = render(
      <CityCard city={makeCity()} expanded={false} onToggle={vi.fn()} />,
    )
    expect(screen.getByRole('button')).toHaveAttribute('aria-expanded', 'false')

    rerender(<CityCard city={makeCity()} expanded onToggle={vi.fn()} />)
    expect(screen.getByRole('button')).toHaveAttribute('aria-expanded', 'true')
  })

  it('renders without a country or icon', () => {
    render(
      <CityCard city={makeCity({ country: null, icon: null })} expanded={false} onToggle={vi.fn()} />,
    )

    expect(screen.getByText('Boston')).toBeInTheDocument()
  })
})
