import { useState, useCallback } from 'react';

export type Mode = 'optimize' | 'knowledge';

export function useMode(initial: Mode = 'optimize') {
  const [mode, setMode] = useState<Mode>(initial);

  const parsePrefix = useCallback(
    (text: string): { mode: Mode; prompt: string } | null => {
      const trimmed = text.trimStart();
      if (trimmed.toLowerCase().startsWith('/optimize ')) {
        return { mode: 'optimize', prompt: trimmed.slice('/optimize '.length).trim() };
      }
      if (trimmed.toLowerCase().startsWith('/knowledge ')) {
        return { mode: 'knowledge', prompt: trimmed.slice('/knowledge '.length).trim() };
      }
      return null;
    },
    [],
  );

  return { mode, setMode, parsePrefix };
}
