import { useCallback } from 'react';
import { motion } from 'framer-motion';
import { ModeToggle } from './ModeToggle';
import { MessageList } from './MessageList';
import { ChatInput } from './ChatInput';
import { Trash2 } from 'lucide-react';
import { useMode } from '@/hooks/useMode';
import { useChatSession } from '@/hooks/useChatSession';

export function ChatPanel() {
  const { mode, setMode, parsePrefix } = useMode();
  const { messages, isLoading, sendMessage, clearHistory } = useChatSession();

  const handleSend = useCallback(
    (text: string) => {
      const parsed = parsePrefix(text);
      if (parsed) {
        setMode(parsed.mode);
        sendMessage(parsed.prompt, parsed.mode);
      } else {
        sendMessage(text, mode);
      }
    },
    [mode, setMode, parsePrefix, sendMessage],
  );

  return (
    <motion.div
      className="relative z-10 w-full h-full flex flex-col"
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.6, ease: 'easeOut' }}
    >
      {/* Header */}
      <header className="border-b border-white/5 backdrop-blur-md bg-black/30 px-6 py-3 flex items-center justify-between shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-7 h-7 rounded-full bg-gradient-to-br from-violet-500 to-indigo-500 flex items-center justify-center text-white text-xs font-bold shadow-lg shadow-violet-500/20">
            A
          </div>
          <div className="text-white font-semibold tracking-widest text-sm">AGNES</div>
          <div className="hidden sm:block text-xs text-white/30">· AI Supply Chain Manager</div>
        </div>
        <div className="flex items-center gap-3">
          <ModeToggle mode={mode} onChange={setMode} disabled={isLoading} />
          <button
            onClick={clearHistory}
            disabled={isLoading || messages.length === 0}
            className="text-white/30 hover:text-white/70 p-2 rounded-full transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
            title="Clear history"
          >
            <Trash2 className="w-4 h-4" />
          </button>
        </div>
      </header>

      {/* Message list */}
      <MessageList messages={messages} isLoading={isLoading} mode={mode} />

      {/* Input area */}
      <div className="border-t border-white/5 backdrop-blur-md bg-black/30 p-4 shrink-0">
        <div className="max-w-3xl mx-auto">
          <ChatInput
            onSend={handleSend}
            disabled={isLoading}
            placeholder={
              mode === 'optimize'
                ? 'Ask Agnes to optimize your sourcing...'
                : 'Ask Agnes about your portfolio...'
            }
            onCommand={setMode}
          />
          <div className="mt-2 text-xs text-white/20 text-center">
            <span className="hidden sm:inline">
              Type <code className="bg-white/5 px-1 rounded text-violet-300/50">/optimize</code> or{' '}
              <code className="bg-white/5 px-1 rounded text-violet-300/50">/knowledge</code> to switch modes ·{' '}
            </span>
            Enter to send · Shift+Enter for newline
          </div>
        </div>
      </div>
    </motion.div>
  );
}
