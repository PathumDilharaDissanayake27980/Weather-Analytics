export function LoadingState() {
  return (
    <div className="state" role="status" aria-live="polite">
      <div className="spinner" aria-hidden="true" />
      <p className="state__title">Fetching weather for every city…</p>
      <p className="state__detail">
        A cold cache means one request per city, so the first load is the slow one.
      </p>
    </div>
  )
}
