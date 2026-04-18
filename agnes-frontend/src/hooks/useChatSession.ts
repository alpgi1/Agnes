import { useState, useCallback, useEffect } from 'react';
import { api, ApiError } from '@/api/client';
import type { ChatMessage, MessageMetadata } from '@/api/types';
import type { Mode } from './useMode';

const STORAGE_KEY = 'agnes-chat-session-v1';
const MAX_HISTORY_FOR_API = 10;

function load(): ChatMessage[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function save(messages: ChatMessage[]) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(messages.slice(-50)));
  } catch {
    // ignore quota errors
  }
}

export function useChatSession() {
  const [messages, setMessages] = useState<ChatMessage[]>(load);
  const [isLoading, setIsLoading] = useState(false);
  const [sessionId, setSessionId] = useState<string | undefined>(undefined);

  useEffect(() => {
    save(messages);
  }, [messages]);

  const clearHistory = useCallback(() => {
    setMessages([]);
    setSessionId(undefined);
    localStorage.removeItem(STORAGE_KEY);
  }, []);

  const sendMessage = useCallback(
    async (prompt: string, mode: Mode) => {
      if (!prompt.trim()) return;
      setIsLoading(true);

      const userMsg: ChatMessage = {
        id: crypto.randomUUID(),
        role: 'user',
        content: prompt,
        mode,
        timestamp: Date.now(),
      };

      // Use functional updater to get latest messages for history
      let currentMessages: ChatMessage[] = [];
      setMessages((prev) => {
        currentMessages = prev;
        return [...prev, userMsg];
      });

      // Build history payload (last N turns)
      const history = currentMessages.slice(-MAX_HISTORY_FOR_API).map((m) => ({
        role: m.role,
        content: m.content,
      }));

      try {
        const req = { prompt, history, sessionId };
        let metadata: MessageMetadata;
        let markdown: string;

        if (mode === 'optimize') {
          const res = await api.optimize(req);
          markdown = res.markdown;
          metadata = {
            sessionId: res.sessionId,
            optimizersRun: res.optimizersRun,
            complianceStatus: res.complianceStatus as MessageMetadata['complianceStatus'],
            findings: res.findings,
            scope: res.scope,
            routerReasoning: res.routerReasoning,
            durationMs: res.durationMs,
          };
          setSessionId(res.sessionId);
        } else {
          const res = await api.knowledge(req);
          markdown = res.markdown;
          metadata = {
            sessionId: res.sessionId,
            sqlUsed: res.sqlUsed,
            rowCount: res.rowCount,
            truncated: res.truncated,
            durationMs: res.durationMs,
          };
          setSessionId(res.sessionId);
        }

        const assistantMsg: ChatMessage = {
          id: crypto.randomUUID(),
          role: 'assistant',
          content: markdown,
          mode,
          timestamp: Date.now(),
          metadata,
        };
        setMessages((prev) => [...prev, assistantMsg]);
      } catch (e) {
        const errMsg: ChatMessage = {
          id: crypto.randomUUID(),
          role: 'assistant',
          content: '',
          mode,
          timestamp: Date.now(),
          error:
            e instanceof ApiError
              ? `${e.status === 0 ? 'Network' : e.status} — ${e.message}`
              : e instanceof Error
                ? e.message
                : 'Unknown error',
        };
        setMessages((prev) => [...prev, errMsg]);
      } finally {
        setIsLoading(false);
      }
    },
    [sessionId],
  );

  return { messages, isLoading, sendMessage, clearHistory, sessionId };
}
