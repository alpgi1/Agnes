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
      {/* 3D Background */}
      <Suspense fallback={null}>
        <DottedSurface />
      </Suspense>

      {/* Gradient overlay for readability */}
      <div className="absolute inset-0 pointer-events-none bg-gradient-to-br from-violet-950/30 via-black/60 to-indigo-950/30" />

      {/* Chat panel */}
      <div className="relative z-10 w-full h-full">
        <ChatPanel />
      </div>
    </div>
  );
}
