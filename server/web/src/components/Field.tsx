import type { ReactNode } from 'react';

export function Field(props: {
  label: string;
  children: ReactNode;
  hint?: string;
}): React.JSX.Element {
  return (
    <label className="field">
      <span>{props.label}</span>
      {props.children}
      {props.hint ? <small>{props.hint}</small> : null}
    </label>
  );
}
