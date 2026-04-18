import { useEffect, useState } from 'react';
import { Loader2 } from 'lucide-react';

const OPTIMIZE_STAGES = [
  { untilSec: 3, label: 'Understanding your request...' },
  { untilSec: 10, label: 'Querying the database...' },
  { untilSec: 20, label: 'Running optimizers...' },
  { untilSec: 35, label: 'Verifying compliance...' },
  { untilSec: 60, label: 'Finalizing report...' },
  { untilSec: 999, label: 'Still working — large portfolio...' },
];

const KNOWLEDGE_STAGES = [
  { untilSec: 3, label: 'Understanding your question...' },
  { untilSec: 8, label: 'Generating SQL query...' },
  { untilSec: 15, label: 'Querying the database...' },
  { untilSec: 999, label: 'Composing answer...' },
];

export function ThinkingIndicator({ mode }: { mode: 'optimize' | 'knowledge' }) {
  const [elapsed, setElapsed] = useState(0);

  useEffect(() => {
    const start = Date.now();
    const interval = setInterval(() => {
      setElapsed(Math.floor((Date.now() - start) / 1000));
    }, 250);
    return () => clearInterval(interval);
  }, []);

  const stages = mode === 'optimize' ? OPTIMIZE_STAGES : KNOWLEDGE_STAGES;
  const stage = stages.find((s) => elapsed < s.untilSec)?.label || stages[stages.length - 1].label;

  return (
    <div className="flex items-center gap-3 text-white/70 px-4 py-3 bg-white/5 backdrop-blur-sm rounded-xl border border-white/10 animate-pulse">
      <Loader2 className="w-4 h-4 animate-spin text-violet-400" />
      <span className="text-sm">{stage}</span>
      <span className="text-xs text-white/40 font-mono ml-auto tabular-nums">{elapsed}s</span>
    </div>
  );
}
