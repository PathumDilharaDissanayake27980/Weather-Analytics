import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Controls } from '../src/components/Controls'

function renderControls(overrides: Partial<Parameters<typeof Controls>[0]> = {}) {
  const props = {
    search: '',
    onSearchChange: vi.fn(),
    sortKey: 'rank' as const,
    onSortKeyChange: vi.fn(),
    sortDirection: 'asc' as const,
    onSortDirectionToggle: vi.fn(),
    labelFilter: 'all' as const,
    onLabelFilterChange: vi.fn(),
    resultCount: 10,
    totalCount: 10,
    ...overrides,
  }
  render(<Controls {...props} />)
  return props
}

describe('Controls', () => {
  it('reports how many cities survive the current filters', () => {
    renderControls({ resultCount: 3, totalCount: 10 })

    expect(screen.getByRole('status')).toHaveTextContent('3 of 10 cities')
  })

  it('emits each keystroke of the search term', async () => {
    const props = renderControls()

    await userEvent.type(screen.getByLabelText('Search'), 'Osl')

    expect(props.onSearchChange).toHaveBeenCalledTimes(3)
  })

  it('offers every sort option', () => {
    renderControls()

    const select = screen.getByLabelText('Sort by')
    expect(select).toContainHTML('Comfort rank')
    expect(select).toContainHTML('Comfort score')
    expect(select).toContainHTML('City name')
    expect(select).toContainHTML('Temperature')
    expect(select).toContainHTML('Humidity')
    expect(select).toContainHTML('Wind speed')
  })

  it('emits the chosen sort key', async () => {
    const props = renderControls()

    await userEvent.selectOptions(screen.getByLabelText('Sort by'), 'temperature')

    expect(props.onSortKeyChange).toHaveBeenCalledWith('temperature')
  })

  it('toggles the sort direction', async () => {
    const props = renderControls({ sortDirection: 'asc' })

    await userEvent.click(screen.getByRole('button', { name: /sort ascending/i }))

    expect(props.onSortDirectionToggle).toHaveBeenCalledOnce()
  })

  it('shows the current sort direction', () => {
    const { unmount } = render(
      <Controls
        search="" onSearchChange={vi.fn()}
        sortKey="rank" onSortKeyChange={vi.fn()}
        sortDirection="desc" onSortDirectionToggle={vi.fn()}
        labelFilter="all" onLabelFilterChange={vi.fn()}
        resultCount={1} totalCount={1}
      />,
    )
    expect(screen.getByRole('button', { name: /sort descending/i })).toBeInTheDocument()
    unmount()
  })

  it('offers every comfort band plus an all-bands option', () => {
    renderControls()

    const select = screen.getByLabelText('Comfort band')
    expect(select).toContainHTML('All bands')
    expect(select).toContainHTML('Excellent')
    expect(select).toContainHTML('Harsh')
  })

  it('emits the chosen comfort band', async () => {
    const props = renderControls()

    await userEvent.selectOptions(screen.getByLabelText('Comfort band'), 'Moderate')

    expect(props.onLabelFilterChange).toHaveBeenCalledWith('Moderate')
  })
})
