import { useState, useCallback, useRef, useEffect, type KeyboardEvent } from 'react';
import { Send, Sparkles, BookOpen } from 'lucide-react';
import { cn } from '@/lib/utils';

export interface ChatInputProps {
  onSend: (text: string) => void;
  disabled?: boolean;
  placeholder?: string;
  onCommand?: (command: 'optimize' | 'knowledge') => void;
}

export function ChatInput({ onSend, disabled, placeholder, onCommand }: ChatInputProps) {
  const [value, setValue] = useState('');
  const [detectedCommand, setDetectedCommand] = useState<'optimize' | 'knowledge' | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Auto-resize textarea
  useEffect(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = Math.min(el.scrollHeight, 160) + 'px';
  }, [value]);

  // Detect command prefix as user types
  useEffect(() => {
    const trimmed = value.trimStart().toLowerCase();
    if (trimmed.startsWith('/optimize ') || trimmed === '/optimize') {
      setDetectedCommand('optimize');
    } else if (trimmed.startsWith('/knowledge ') || trimmed === '/knowledge') {
      setDetectedCommand('knowledge');
    } else {
      setDetectedCommand(null);
    }
  }, [value]);

  const handleSend = useCallback(() => {
    const trimmed = value.trim();
    if (!trimmed || disabled) return;

    // If command prefix detected, notify parent to switch mode
    if (detectedCommand) {
      onCommand?.(detectedCommand);
    }

    onSend(trimmed);
    setValue('');
    setDetectedCommand(null);
  }, [value, disabled, detectedCommand, onCommand, onSend]);

  const handleKeyDown = useCallback(
    (e: KeyboardEvent<HTMLTextAreaElement>) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        handleSend();
      }
    },
    [handleSend],
  );

  return (
    <div className="relative">
      {/* Command detection indicator */}
      {detectedCommand && (
        <div className="absolute -top-8 left-2 flex items-center gap-1.5 text-xs text-violet-300/80 animate-in fade-in slide-in-from-bottom-1 duration-200">
          {detectedCommand === 'optimize' ? (
            <Sparkles className="w-3 h-3" />
          ) : (
            <BookOpen className="w-3 h-3" />
          )}
          <span>
            Switching to <span className="font-medium capitalize">{detectedCommand}</span> mode
          </span>
        </div>
      )}

      <div
        className={cn(
          'flex items-end gap-2 bg-white/[0.03] backdrop-blur-sm border rounded-xl px-4 py-3 transition-colors',
          detectedCommand
            ? 'border-violet-500/30'
            : 'border-white/10 focus-within:border-white/20',
        )}
      >
        <textarea
          ref={textareaRef}
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={placeholder || 'Ask Agnes...'}
          disabled={disabled}
          rows={1}
          className="flex-1 bg-transparent text-white text-sm placeholder-white/30 resize-none outline-none min-h-[24px] max-h-[160px] disabled:opacity-50"
        />
        <button
          onClick={handleSend}
          disabled={disabled || !value.trim()}
          className={cn(
            'shrink-0 w-8 h-8 rounded-lg flex items-center justify-center transition-all duration-200',
            value.trim() && !disabled
              ? 'bg-violet-500 hover:bg-violet-400 text-white shadow-lg shadow-violet-500/25'
              : 'bg-white/5 text-white/20',
          )}
        >
          <Send className="w-4 h-4" />
        </button>
      </div>
    </div>
  );
}
