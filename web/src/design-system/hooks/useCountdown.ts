import { useEffect, useRef, useState } from 'react';

export interface Countdown {
  secondsLeft: number;
  expired: boolean;
}

/**
 * Compte à rebours calé sur l'horloge (Date.now) et non sur le nombre de ticks :
 * un onglet mis en veille ou un téléphone verrouillé ne fait donc jamais dériver le délai.
 * `onExpire` est appelé une seule fois.
 */
export function useCountdown(deadline: Date | number, onExpire?: () => void): Countdown {
  const deadlineMs = deadline instanceof Date ? deadline.getTime() : deadline;
  const [secondsLeft, setSecondsLeft] = useState(() => remaining(deadlineMs));

  const onExpireRef = useRef(onExpire);
  useEffect(() => {
    onExpireRef.current = onExpire;
  });

  useEffect(() => {
    const initial = remaining(deadlineMs);
    setSecondsLeft(initial);
    if (initial === 0) {
      onExpireRef.current?.();
      return undefined;
    }
    const id = window.setInterval(() => {
      const next = remaining(deadlineMs);
      setSecondsLeft(next);
      if (next === 0) {
        window.clearInterval(id);
        onExpireRef.current?.();
      }
    }, 1000);
    return () => window.clearInterval(id);
  }, [deadlineMs]);

  return { secondsLeft, expired: secondsLeft === 0 };
}

function remaining(deadlineMs: number): number {
  return Math.max(0, Math.ceil((deadlineMs - Date.now()) / 1000));
}
