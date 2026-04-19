import { useCallback } from 'react';
import { motion } from 'framer-motion';
import { ModeToggle } from './ModeToggle';
import { MessageList } from './MessageList';
import { ChatInput } from './ChatInput';
import { Trash2, Network, MessageCircle } from 'lucide-react';
import { useMode } from '@/hooks/useMode';
import { useChatSession } from '@/hooks/useChatSession';
import { GraphScreen } from '@/screens/GraphScreen';

export function ChatPanel() {
  const { mode, setMode, parsePrefix } = useMode();
  const { messages, isLoading, sendMessage, clearHistory } = useChatSession();

  const isGraphMode = mode === 'graph';

  const handleSend = useCallback(
    (text: string) => {
      const parsed = parsePrefix(text);
      if (parsed) {
        setMode(parsed.mode);
        sendMessage(parsed.prompt, parsed.mode);
      } else {
        sendMessage(text, mode === 'graph' ? 'optimize' : mode);
      }
    },
    [mode, setMode, parsePrefix, sendMessage],
  );

  const toggleGraph = useCallback(() => {
    setMode(isGraphMode ? 'optimize' : 'graph');
  }, [isGraphMode, setMode]);

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
          <div className="text-white font-semibold tracking-widest text-sm">AGNES</div>
          <div className="hidden sm:block text-xs text-white/30">· AI Supply Chain Manager</div>
        </div>
        <div className="flex items-center gap-1.5">
          {/* Graph / Chat toggle */}
          <button
            onClick={toggleGraph}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-all duration-200 ${
              isGraphMode
                ? 'bg-violet-500/20 text-violet-300 border border-violet-500/30'
                : 'text-white/40 hover:text-white/70 hover:bg-white/[0.04] border border-transparent'
            }`}
            title={isGraphMode ? 'Back to Chat' : 'Supply Chain Graph'}
          >
            {isGraphMode ? (
              <>
                <MessageCircle className="w-3.5 h-3.5" />
                <span className="hidden sm:inline">Chat</span>
              </>
            ) : (
              <>
                <Network className="w-3.5 h-3.5" />
                <span className="hidden sm:inline">Graph</span>
              </>
            )}
          </button>

          {/* Clear history — only in chat mode */}
          {!isGraphMode && (
            <button
              onClick={clearHistory}
              disabled={isLoading || messages.length === 0}
              className="text-white/30 hover:text-white/70 p-2 rounded-full transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
              title="Clear history"
            >
              <Trash2 className="w-4 h-4" />
            </button>
          )}
        </div>
      </header>

      {/* Content: Graph or Chat */}
      {isGraphMode ? (
        <GraphScreen />
      ) : (
        <>
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
              <div className="mt-2 flex items-center justify-between">
                <ModeToggle mode={mode} onChange={setMode} disabled={isLoading} small />
                <div className="text-xs text-white/20 text-center">
                <span className="hidden sm:inline">
                  Type <code className="bg-white/5 px-1 rounded text-violet-300/50">/optimize</code> or{' '}
                  <code className="bg-white/5 px-1 rounded text-violet-300/50">/knowledge</code> to switch modes ·{' '}
                </span>
                  Enter to send · Shift+Enter for newline
                </div>
              </div>
            </div>
          </div>
        </>
      )}
    </motion.div>
  );
}
