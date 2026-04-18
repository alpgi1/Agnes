import { AssistantMarkdown } from './AssistantMarkdown';
import { ComplianceBadge } from './ComplianceBadge';
import { GlowCard } from '@/components/ui/spotlight-card';
import { AlertCircle, Code2, Sparkles, BookOpen } from 'lucide-react';
import type { ChatMessage } from '@/api/types';

export function MessageBubble({ msg }: { msg: ChatMessage }) {
  const isUser = msg.role === 'user';

  // Error state
  if (msg.error) {
    return (
      <div className="flex items-start gap-3 my-4">
        <div className="w-8 h-8 rounded-full bg-red-500/20 border border-red-500/30 flex items-center justify-center shrink-0">
          <AlertCircle className="w-4 h-4 text-red-300" />
        </div>
        <GlowCard customSize glowColor="red" className="flex-1 px-4 py-3">
          <div className="text-sm text-red-300 font-medium">Request failed</div>
          <div className="text-sm text-white/70 mt-1">{msg.error}</div>
        </GlowCard>
      </div>
    );
  }

  // User message
  if (isUser) {
    return (
      <div className="flex justify-end my-4">
        <GlowCard customSize glowColor="purple" className="max-w-[80%] px-4 py-2.5 rounded-2xl rounded-tr-sm">
          <div className="flex items-center gap-2 mb-1 text-xs text-violet-200/70">
            {msg.mode === 'optimize' ? (
              <Sparkles className="w-3 h-3" />
            ) : (
              <BookOpen className="w-3 h-3" />
            )}
            <span className="capitalize">{msg.mode}</span>
          </div>
          <div className="text-sm text-white whitespace-pre-wrap">{msg.content}</div>
        </GlowCard>
      </div>
    );
  }

  // Assistant message
  return (
    <div className="flex items-start gap-3 my-4">
      <div className="w-8 h-8 rounded-full bg-gradient-to-br from-violet-500 to-indigo-500 flex items-center justify-center shrink-0 text-white text-xs font-bold shadow-lg shadow-violet-500/20">
        A
      </div>
      <div className="flex-1 min-w-0">
        <GlowCard customSize glowColor="blue" className="flex-1 min-w-0 px-5 py-4 rounded-xl rounded-tl-sm">
          {/* Compliance status + optimizers run */}
          {msg.metadata?.complianceStatus && (
            <div className="mb-3 flex items-center gap-2 flex-wrap">
              <span className="text-xs text-white/50">Overall compliance:</span>
              <ComplianceBadge status={msg.metadata.complianceStatus} />
              {msg.metadata.optimizersRun && msg.metadata.optimizersRun.length > 0 && (
                <span className="text-xs text-white/40">
                  · ran: {msg.metadata.optimizersRun.map((o) => o.toLowerCase()).join(', ')}
                </span>
              )}
            </div>
          )}

          {/* Router reasoning */}
          {msg.metadata?.routerReasoning && (
            <details className="mb-3 group">
              <summary className="cursor-pointer text-xs text-white/40 hover:text-white/60 transition-colors">
                🧭 Router reasoning
              </summary>
              <p className="mt-1 text-xs text-white/50 pl-4 border-l border-white/10">
                {msg.metadata.routerReasoning}
              </p>
            </details>
          )}

          {/* Main markdown content */}
          <AssistantMarkdown content={msg.content} />

          {/* SQL used (knowledge mode) */}
          {msg.metadata?.sqlUsed && (
            <details className="mt-3 group">
              <summary className="cursor-pointer text-xs text-white/40 hover:text-white/70 flex items-center gap-1 transition-colors">
                <Code2 className="w-3 h-3" />
                Show SQL used
              </summary>
              <pre className="mt-2 bg-black/40 p-3 rounded-lg text-xs overflow-x-auto text-white/70 font-mono">
                {msg.metadata.sqlUsed}
              </pre>
              {msg.metadata.rowCount !== undefined && (
                <div className="text-xs text-white/40 mt-1">
                  {msg.metadata.rowCount} rows{msg.metadata.truncated && ' (truncated)'}
                </div>
              )}
            </details>
          )}

          {/* Scope info */}
          {msg.metadata?.scope && msg.metadata.scope.type !== 'ALL' && (
            <div className="mt-2 text-xs text-white/30">
              Scope: {msg.metadata.scope.type}
              {msg.metadata.scope.value && ` — ${msg.metadata.scope.value}`}
            </div>
          )}

          {/* Duration */}
          {msg.metadata?.durationMs && (
            <div className="mt-2 text-xs text-white/30 font-mono tabular-nums">
              {(msg.metadata.durationMs / 1000).toFixed(1)}s
            </div>
          )}
        </GlowCard>
      </div>
    </div>
  );
}
