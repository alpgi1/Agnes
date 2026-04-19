import { cn } from '@/lib/utils';
import { Building2, Package, Truck } from 'lucide-react';
import type { GraphView } from '@/api/types';

interface ViewSelectorProps {
  view: GraphView;
  onChange: (view: GraphView) => void;
  disabled?: boolean;
}

const VIEWS: { key: GraphView; label: string; icon: typeof Building2 }[] = [
  { key: 'company-supplier', label: 'Company ↔ Supplier', icon: Building2 },
  { key: 'company-product', label: 'Company ↔ Product', icon: Package },
  { key: 'product-supplier', label: 'Product ↔ Supplier', icon: Truck },
];

export function ViewSelector({ view, onChange, disabled }: ViewSelectorProps) {
  return (
    <div className="inline-flex items-center bg-white/[0.04] backdrop-blur-sm border border-white/[0.08] rounded-xl p-1 gap-0.5">
      {VIEWS.map(({ key, label, icon: Icon }) => (
        <button
          key={key}
          onClick={() => onChange(key)}
          disabled={disabled}
          className={cn(
            'flex items-center gap-2 px-4 py-2 rounded-lg text-xs font-medium transition-all duration-300',
            view === key
              ? 'bg-white/[0.12] text-white shadow-lg shadow-violet-500/10 border border-white/[0.08]'
              : 'text-white/40 hover:text-white/70 hover:bg-white/[0.04] border border-transparent',
            disabled && 'opacity-40 cursor-not-allowed',
          )}
        >
          <Icon className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">{label}</span>
        </button>
      ))}
    </div>
  );
}
