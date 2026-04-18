import { useEffect, useState, Suspense, lazy } from 'react';
import { AgnesLogo } from '@/components/landing/AgnesLogo';

const SpiralAnimation = lazy(() =>
  import('@/components/ui/spiral-animation').then((m) => ({
    default: m.SpiralAnimation,
  })),
);

interface LandingScreenProps {
  onEnter: () => void;
}

export function LandingScreen({ onEnter }: LandingScreenProps) {
  const [logoVisible, setLogoVisible] = useState(false);

  useEffect(() => {
    const t = setTimeout(() => setLogoVisible(true), 1800);
    return () => clearTimeout(t);
  }, []);

  return (
    <div className="fixed inset-0 w-full h-full overflow-hidden bg-black">
      <Suspense fallback={<div className="absolute inset-0 bg-black" />}>
        <div className="absolute inset-0">
          <SpiralAnimation />
        </div>
      </Suspense>
      <AgnesLogo onEnter={onEnter} visible={logoVisible} />
    </div>
  );
}
