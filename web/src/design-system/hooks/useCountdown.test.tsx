import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useCountdown } from './useCountdown';

describe('useCountdown', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-11-14T20:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('décompte en secondes puis signale l’expiration une seule fois', () => {
    const onExpire = vi.fn();
    const deadline = Date.now() + 3000;
    const { result } = renderHook(() => useCountdown(deadline, onExpire));

    expect(result.current.secondsLeft).toBe(3);
    expect(result.current.expired).toBe(false);

    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(result.current.secondsLeft).toBe(2);

    act(() => {
      vi.advanceTimersByTime(5000);
    });
    expect(result.current.secondsLeft).toBe(0);
    expect(result.current.expired).toBe(true);
    expect(onExpire).toHaveBeenCalledTimes(1);
  });

  it('est déjà expiré si la date limite est passée', () => {
    const onExpire = vi.fn();
    const { result } = renderHook(() => useCountdown(Date.now() - 1000, onExpire));
    expect(result.current.expired).toBe(true);
    expect(onExpire).toHaveBeenCalledTimes(1);
  });

  it('reste calé sur l’horloge même si les timers sont suspendus', () => {
    const deadline = Date.now() + 60_000;
    const { result } = renderHook(() => useCountdown(deadline));
    act(() => {
      vi.setSystemTime(Date.now() + 30_000);
      vi.advanceTimersByTime(1000);
    });
    expect(result.current.secondsLeft).toBe(29);
  });
});
