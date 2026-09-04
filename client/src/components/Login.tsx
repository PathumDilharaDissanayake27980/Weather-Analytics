import { useAuth0 } from '@auth0/auth0-react'
import { ThemeToggle } from './ThemeToggle'

export function Login() {
  const { loginWithRedirect, isLoading, error } = useAuth0()

  return (
    <div className="login">
      <div className="login__toolbar">
        <ThemeToggle />
      </div>

      <div className="login__card">
        <h1 className="login__title">Weather Comfort Index</h1>
        <p className="login__subtitle">
          Cities ranked by a custom comfort score computed from live weather data.
        </p>

        <button
          type="button"
          className="button button--primary button--wide"
          onClick={() => void loginWithRedirect()}
          disabled={isLoading}
        >
          {isLoading ? 'Loading…' : 'Sign in'}
        </button>

        {error && <p className="login__error">{error.message}</p>}

        <p className="login__note">
          Access is limited to invited accounts. Public sign-up is disabled, and sign-in
          requires a one-time code sent to your email.
        </p>
      </div>
    </div>
  )
}
