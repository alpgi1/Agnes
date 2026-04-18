import { Suspense, lazy } from 'react';
import { ChatPanel } from '@/components/chat/ChatPanel';

const DottedSurface = lazy(() =>
  import('@/components/ui/dotted-surface').then((m) => ({
    default: m.DottedSurface,
  })),
);

export function ChatScreen() {
  return (
    <div className="fixed inset-0 w-full h-full overflow-hidden bg-black">
      <Suspense fallback={null}>
        <DottedSurface>
          <div className="absolute inset-0 pointer-events-none bg-gradient-to-br from-violet-950/50 via-black/80 to-indigo-950/50" />
          <div className="relative z-10 w-full h-full">
            <ChatPanel />
          </div>
        </DottedSurface>
      </Suspense>
    </div>
  );
}
