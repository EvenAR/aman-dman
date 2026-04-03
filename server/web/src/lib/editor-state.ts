'use client';

import { useEffect } from 'react';

export function cloneValue<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

export function isEqual(a: unknown, b: unknown): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

export function inputValue(value: string | number | null): string {
  return value === null ? '' : String(value);
}

export function parseNullableNumber(value: string): number | null {
  return value === '' ? null : Number(value);
}

export function useBeforeUnload(shouldWarn: boolean): void {
  useEffect(() => {
    const handler = (event: BeforeUnloadEvent): void => {
      if (!shouldWarn) {
        return;
      }
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [shouldWarn]);
}
