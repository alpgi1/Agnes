import { cn } from '@/lib/utils';
import { Sparkles, BookOpen, Network } from 'lucide-react';
import type { Mode } from '@/hooks/useMode';

interface ModeToggleProps {
  mode: Mode;
  onChange: (mode: Mode) => void;
  disabled?: boolean;
  small?: boolean;
}

const MODES: { key: Mode; label: string; icon: typeof Sparkles }[] = [
  { key: 'optimize', label: 'Optimize', icon: Sparkles },
  { key: 'knowledge', label: 'Knowledge', icon: BookOpen },
  { key: 'graph', label: 'Graph', icon: Network },
];

export function ModeToggle({ mode, onChange, disabled, small }: ModeToggleProps) {
  if (small) {
    return (
      <div className="inline-flex items-center bg-white/5 border border-white/10 rounded-full p-0.5 gap-0.5">
        {MODES.map(({ key, label, icon: Icon }) => (
          <button
            key={key}
            onClick={() => onChange(key)}
            disabled={disabled}
            className={cn(
              'flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium transition-all duration-200',
              mode === key ? 'bg-white/15 text-white' : 'text-white/40 hover:text-white/70',
            )}
          >
            <Icon className="w-3 h-3" />
            {label}
          </button>
        ))}
      </div>
    );
  }

  return (
    <div className="inline-flex items-center bg-white/5 backdrop-blur-sm border border-white/10 rounded-full p-1">
      {MODES.map(({ key, label, icon: Icon }) => (
        <button
          key={key}
          onClick={() => onChange(key)}
          disabled={disabled}
          className={cn(
            'flex items-center gap-2 px-4 py-1.5 rounded-full text-sm font-medium transition-all duration-200',
            mode === key
              ? 'bg-white text-black shadow-lg'
              : 'text-white/60 hover:text-white',
          )}
        >
          <Icon className="w-3.5 h-3.5" />
          {label}
        </button>
      ))}
    </div>
  );
}
