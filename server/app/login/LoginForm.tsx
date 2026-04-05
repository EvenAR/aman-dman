import { VATSIM_LOGIN_PATH } from '@/src/auth/constants';

export function LoginForm({ nextPath }: { nextPath: string }): React.JSX.Element {
  return (
    <div className="login-form">
      <a
        href={`${VATSIM_LOGIN_PATH}?next=${encodeURIComponent(nextPath)}`}
        className="primary-button login-card__submit route-shell__primary-link"
      >
        Sign in with VATSIM Sandbox
      </a>
    </div>
  );
}
