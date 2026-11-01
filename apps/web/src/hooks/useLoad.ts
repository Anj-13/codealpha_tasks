import { useCallback, useEffect, useRef, useState } from 'react';
import { ServiceOfflineError } from '../api/client';

export type LoadState<T> =
  | { phase: 'loading' }
  | { phase: 'offline' }
  | { phase: 'error'; message: string }
  | { phase: 'ready'; data: T };

/** Runs a loader on mount and when reloadKey changes, tracking its loading, offline, error and ready states. */
export function useLoad<T>(loader: () => Promise<T>, reloadKey?: string) {
  const [state, setState] = useState<LoadState<T>>({ phase: 'loading' });
  const latestLoader = useRef(loader);
  latestLoader.current = loader;
  const requestId = useRef(0);

  const reload = useCallback(async () => {
    const id = ++requestId.current;
    try {
      const data = await latestLoader.current();
      if (id === requestId.current) {
        setState({ phase: 'ready', data });
      }
    } catch (error) {
      if (id === requestId.current) {
        setState(error instanceof ServiceOfflineError
          ? { phase: 'offline' }
          : { phase: 'error', message: (error as Error).message });
      }
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload, reloadKey]);

  return { state, reload };
}
