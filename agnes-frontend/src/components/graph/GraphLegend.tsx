import type { GraphView, GraphNodeType } from '@/api/types';

const NODE_META: Record<GraphNodeType, { color: string; label: string; shape: string }> = {
  company:       { color: '#8b5cf6', label: 'Company',       shape: '■' },
  supplier:      { color: '#10b981', label: 'Supplier',      shape: '◆' },
  finished_good: { color: '#3b82f6', label: 'Finished Good', shape: '●' },
  raw_material:  { color: '#f59e0b', label: 'Raw Material',  shape: '●' },
};

const VIEW_NODE_TYPES: Record<GraphView, GraphNodeType[]> = {
  'company-supplier': ['company', 'supplier'],
  'company-product':  ['company', 'finished_good', 'raw_material'],
  'product-supplier': ['raw_material', 'supplier'],
};

interface GraphLegendProps {
  view: GraphView;
  nodeCount: number;
  edgeCount: number;
}

export function GraphLegend({ view, nodeCount, edgeCount }: GraphLegendProps) {
  const types = VIEW_NODE_TYPES[view];

  return (
    <div className="flex items-center justify-between gap-4 px-1">
      <div className="flex items-center gap-4">
        {types.map((type) => {
          const meta = NODE_META[type];
          return (
            <div key={type} className="flex items-center gap-1.5 text-xs text-white/50">
              <span style={{ color: meta.color }} className="text-sm leading-none">
                {meta.shape}
              </span>
              <span>{meta.label}</span>
            </div>
          );
        })}
      </div>
      <div className="text-xs text-white/25">
        {nodeCount} nodes · {edgeCount} edges
      </div>
    </div>
  );
}
