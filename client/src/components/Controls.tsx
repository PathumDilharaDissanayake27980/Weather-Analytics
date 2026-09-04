import type { ComfortLabel, SortDirection, SortKey } from '../types/weather'

interface Props {
  search: string
  onSearchChange: (value: string) => void
  sortKey: SortKey
  onSortKeyChange: (value: SortKey) => void
  sortDirection: SortDirection
  onSortDirectionToggle: () => void
  labelFilter: ComfortLabel | 'all'
  onLabelFilterChange: (value: ComfortLabel | 'all') => void
  resultCount: number
  totalCount: number
}

const SORT_OPTIONS: { value: SortKey; label: string }[] = [
  { value: 'rank', label: 'Comfort rank' },
  { value: 'score', label: 'Comfort score' },
  { value: 'name', label: 'City name' },
  { value: 'temperature', label: 'Temperature' },
  { value: 'humidity', label: 'Humidity' },
  { value: 'wind', label: 'Wind speed' },
]

const LABEL_OPTIONS: (ComfortLabel | 'all')[] = [
  'all',
  'Excellent',
  'Comfortable',
  'Moderate',
  'Uncomfortable',
  'Harsh',
]

/**
 * Sorting and filtering.
 *
 * These operate on data already scored and ranked by the server. Re-sorting in the browser is
 * a presentation choice; the score itself is never recomputed here.
 */
export function Controls({
  search,
  onSearchChange,
  sortKey,
  onSortKeyChange,
  sortDirection,
  onSortDirectionToggle,
  labelFilter,
  onLabelFilterChange,
  resultCount,
  totalCount,
}: Props) {
  return (
    <section className="controls" aria-label="Sort and filter">
      <div className="controls__field controls__field--grow">
        <label className="controls__label" htmlFor="search">
          Search
        </label>
        <input
          id="search"
          className="controls__input"
          type="search"
          placeholder="Filter by city name…"
          value={search}
          onChange={(e) => onSearchChange(e.target.value)}
        />
      </div>

      <div className="controls__field">
        <label className="controls__label" htmlFor="sort">
          Sort by
        </label>
        <div className="controls__sort">
          <select
            id="sort"
            className="controls__input"
            value={sortKey}
            onChange={(e) => onSortKeyChange(e.target.value as SortKey)}
          >
            {SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <button
            type="button"
            className="controls__direction"
            onClick={onSortDirectionToggle}
            aria-label={`Sort ${sortDirection === 'asc' ? 'ascending' : 'descending'}`}
            title={sortDirection === 'asc' ? 'Ascending' : 'Descending'}
          >
            {sortDirection === 'asc' ? '↑' : '↓'}
          </button>
        </div>
      </div>

      <div className="controls__field">
        <label className="controls__label" htmlFor="label-filter">
          Comfort band
        </label>
        <select
          id="label-filter"
          className="controls__input"
          value={labelFilter}
          onChange={(e) => onLabelFilterChange(e.target.value as ComfortLabel | 'all')}
        >
          {LABEL_OPTIONS.map((option) => (
            <option key={option} value={option}>
              {option === 'all' ? 'All bands' : option}
            </option>
          ))}
        </select>
      </div>

      <p className="controls__count" role="status">
        {resultCount} of {totalCount} cities
      </p>
    </section>
  )
}
