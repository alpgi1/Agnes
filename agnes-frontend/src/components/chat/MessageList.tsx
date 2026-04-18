import { useEffect, useRef } from 'react';
import { MessageBubble } from './MessageBubble';
import { ThinkingIndicator } from './ThinkingIndicator';
import type { ChatMessage } from '@/api/types';
import type { Mode } from '@/hooks/useMode';

interface MessageListProps {
  messages: ChatMessage[];
  isLoading: boolean;
  mode: Mode;
}

export function MessageList({ messages, isLoading, mode }: MessageListProps) {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length, isLoading]);

  if (messages.length === 0 && !isLoading) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <div className="text-center text-white/40 max-w-md px-6">
          <div className="w-16 h-16 mx-auto mb-6 rounded-full bg-gradient-to-br from-violet-500/20 to-indigo-500/20 border border-violet-500/20 flex items-center justify-center">
            <span className="text-2xl font-bold text-violet-300/60">A</span>
          </div>
          <div className="text-lg mb-2 text-white/60">Welcome to Agnes</div>
          <div className="text-sm leading-relaxed">
            Ask Agnes to optimize your sourcing, identify substitutions, or query
            your portfolio. Use the toggle above to switch modes, or type{' '}
            <code className="bg-white/10 px-1.5 py-0.5 rounded text-xs text-violet-300">/optimize</code>{' '}
            or{' '}
            <code className="bg-white/10 px-1.5 py-0.5 rounded text-xs text-violet-300">/knowledge</code>{' '}
            prefixes.
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 overflow-y-auto px-4 py-4">
      <div className="max-w-3xl mx-auto">
        {messages.map((msg) => (
          <MessageBubble key={msg.id} msg={msg} />
        ))}
        {isLoading && (
          <div className="my-4">
            <ThinkingIndicator mode={mode} />
          </div>
        )}
        <div ref={bottomRef} />
      </div>
    </div>
  );
}
