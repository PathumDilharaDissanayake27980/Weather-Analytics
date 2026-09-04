interface Props {
  message: string
  onRetry: () => void
}

export function ErrorState({ message, onRetry }: Props) {
  return (
    <div className="state state--error" role="alert">
      <p className="state__title">Could not load the dashboard</p>
      <p className="state__detail">{message}</p>
      <button type="button" className="button" onClick={onRetry}>
        Try again
      </button>
    </div>
  )
}
