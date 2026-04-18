import { cn } from '@/lib/utils';
import { Sparkles, BookOpen } from 'lucide-react';
import type { Mode } from '@/hooks/useMode';

interface ModeToggleProps {
  mode: Mode;
  onChange: (mode: Mode) => void;
  disabled?: boolean;
}

export function ModeToggle({ mode, onChange, disabled }: ModeToggleProps) {
  return (
    <div className="inline-flex items-center bg-white/5 backdrop-blur-sm border border-white/10 rounded-full p-1">
      <button
        onClick={() => onChange('optimize')}
        disabled={disabled}
        className={cn(
          'flex items-center gap-2 px-4 py-1.5 rounded-full text-sm font-medium transition-all duration-200',
          mode === 'optimize'
            ? 'bg-white text-black shadow-lg'
            : 'text-white/60 hover:text-white',
        )}
      >
        <Sparkles className="w-3.5 h-3.5" />
        Optimize
      </button>
      <button
        onClick={() => onChange('knowledge')}
        disabled={disabled}
        className={cn(
          'flex items-center gap-2 px-4 py-1.5 rounded-full text-sm font-medium transition-all duration-200',
          mode === 'knowledge'
            ? 'bg-white text-black shadow-lg'
            : 'text-white/60 hover:text-white',
        )}
      >
        <BookOpen className="w-3.5 h-3.5" />
        Knowledge
      </button>
    </div>
  );
}
