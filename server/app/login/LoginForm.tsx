'use client';

import { useActionState } from 'react';
import { useFormStatus } from 'react-dom';

import { loginAction, type LoginActionState } from './actions';

const initialLoginActionState: LoginActionState = {
  error: null,
};

function SubmitButton(): React.JSX.Element {
  const { pending } = useFormStatus();

  return (
    <button type="submit" className="primary-button login-card__submit" disabled={pending}>
      {pending ? 'Signing in...' : 'Sign in'}
    </button>
  );
}

export function LoginForm({ nextPath }: { nextPath: string }): React.JSX.Element {
  const [state, formAction] = useActionState(loginAction, initialLoginActionState);

  return (
    <form action={formAction} className="login-form">
      <input type="hidden" name="next" value={nextPath} />

      <label className="field">
        <span>Username</span>
        <input name="username" autoComplete="username" spellCheck={false} />
      </label>

      <label className="field">
        <span>Password</span>
        <input name="password" type="password" autoComplete="current-password" />
      </label>

      {state.error ? <div className="banner banner--error">{state.error}</div> : null}

      <SubmitButton />
    </form>
  );
}
