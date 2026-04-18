import { cn } from '@/lib/utils';

const STATUS_STYLES: Record<string, { icon: string; label: string; classes: string }> = {
  compliant: {
    icon: '✅',
    label: 'Compliant',
    classes: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30',
  },
  uncertain: {
    icon: '⚠️',
    label: 'Uncertain',
    classes: 'bg-amber-500/15 text-amber-300 border-amber-500/30',
  },
  'non-compliant': {
    icon: '❌',
    label: 'Non-compliant',
    classes: 'bg-red-500/15 text-red-300 border-red-500/30',
  },
  not_applicable: {
    icon: '—',
    label: 'N/A',
    classes: 'bg-white/5 text-white/50 border-white/10',
  },
  pending: {
    icon: '⏳',
    label: 'Pending',
    classes: 'bg-blue-500/15 text-blue-300 border-blue-500/30',
  },
};

export function ComplianceBadge({ status }: { status: string }) {
  const style = STATUS_STYLES[status] || STATUS_STYLES.pending;
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium border',
        style.classes,
      )}
    >
      <span>{style.icon}</span>
      <span>{style.label}</span>
    </span>
  );
}
