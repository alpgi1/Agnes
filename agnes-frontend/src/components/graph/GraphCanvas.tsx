import { useEffect, useRef, useState } from 'react';
import { Network, type Options } from 'vis-network';
import { DataSet } from 'vis-data';
import type { GraphResponse, GraphNodeType, GraphEdgeType } from '@/api/types';

// ── Node styles ────────────────────────────────────────────────────────
// Inspired by the reference image: dark nodes with varying sizes,
// steel-blue/purple edges, clean minimal aesthetic

const NODE_STYLES: Record<GraphNodeType, Record<string, unknown>> = {
  company: {
    color: {
      background: '#8b5cf6',
      border: '#a78bfa',
      highlight: { background: '#a78bfa', border: '#c4b5fd' },
      hover: { background: '#a78bfa', border: '#c4b5fd' },
    },
    shape: 'box',
    font: { color: '#fff', size: 16, face: 'Inter, system-ui, sans-serif', bold: { color: '#fff' } },
    borderWidth: 2,
    size: 40,
    shadow: { enabled: true, color: 'rgba(139, 92, 246, 0.3)', size: 12, x: 0, y: 0 },
  },
  supplier: {
    color: {
      background: '#10b981',
      border: '#34d399',
      highlight: { background: '#34d399', border: '#6ee7b7' },
      hover: { background: '#34d399', border: '#6ee7b7' },
    },
    shape: 'diamond',
    font: { color: '#e2e8f0', size: 14, face: 'Inter, system-ui, sans-serif' },
    borderWidth: 2,
    size: 24,
    shadow: { enabled: true, color: 'rgba(16, 185, 129, 0.25)', size: 8, x: 0, y: 0 },
  },
  finished_good: {
    color: {
      background: '#3b82f6',
      border: '#60a5fa',
      highlight: { background: '#60a5fa', border: '#93c5fd' },
      hover: { background: '#60a5fa', border: '#93c5fd' },
    },
    shape: 'dot',
    font: { color: '#cbd5e1', size: 12, face: 'Inter, system-ui, sans-serif' },
    borderWidth: 1.5,
    size: 18,
    shadow: { enabled: true, color: 'rgba(59, 130, 246, 0.2)', size: 6, x: 0, y: 0 },
  },
  raw_material: {
    color: {
      background: '#f59e0b',
      border: '#fbbf24',
      highlight: { background: '#fbbf24', border: '#fcd34d' },
      hover: { background: '#fbbf24', border: '#fcd34d' },
    },
    shape: 'dot',
    font: { color: '#cbd5e1', size: 12, face: 'Inter, system-ui, sans-serif' },
    borderWidth: 1.5,
    size: 16,
    shadow: { enabled: true, color: 'rgba(245, 158, 11, 0.2)', size: 6, x: 0, y: 0 },
  },
};

const EDGE_STYLES: Record<GraphEdgeType, Record<string, unknown>> = {
  sources_from: {
    color: { color: 'rgba(148, 163, 184, 0.35)', highlight: 'rgba(148, 163, 184, 0.7)', hover: 'rgba(148, 163, 184, 0.5)' },
    dashes: false,
    width: 2,
  },
  owns: {
    color: { color: 'rgba(139, 92, 246, 0.35)', highlight: 'rgba(139, 92, 246, 0.7)', hover: 'rgba(139, 92, 246, 0.5)' },
    dashes: false,
    width: 2,
  },
  uses: {
    color: { color: 'rgba(148, 163, 184, 0.2)', highlight: 'rgba(148, 163, 184, 0.5)', hover: 'rgba(148, 163, 184, 0.35)' },
    dashes: [5, 5],
    width: 1,
  },
  supplied_by: {
    color: { color: 'rgba(16, 185, 129, 0.35)', highlight: 'rgba(16, 185, 129, 0.7)', hover: 'rgba(16, 185, 129, 0.5)' },
    dashes: false,
    width: 2,
  },
};

// ── vis-network options ────────────────────────────────────────────────

const NETWORK_OPTIONS: Options = {
  autoResize: true,
  physics: {
    enabled: true,
    solver: 'forceAtlas2Based',
    forceAtlas2Based: {
      gravitationalConstant: -120,
      centralGravity: 0.015,
      springLength: 200,
      springConstant: 0.04,
      damping: 0.6,
      avoidOverlap: 0,
    },
    stabilization: {
      enabled: true,
      iterations: 150,
      updateInterval: 25,
    },
  },
  interaction: {
    hover: true,
    tooltipDelay: 100,
    zoomView: true,
    dragView: true,
    navigationButtons: false,
    keyboard: false,
    multiselect: false,
  },
  edges: {
    smooth: {
      enabled: true,
      type: 'continuous',
      roundness: 0.3,
    },
    arrows: {
      to: { enabled: true, scaleFactor: 0.5, type: 'arrow' },
    },
    selectionWidth: 2,
    hoverWidth: 1.5,
  },
  nodes: {
    borderWidthSelected: 3,
    margin: {
      top: 8,
      right: 12,
      bottom: 8,
      left: 12,
    },
  },
  layout: {
    improvedLayout: true,
    randomSeed: 42,
  },
};

// ── Component ──────────────────────────────────────────────────────────

interface GraphCanvasProps {
  data: GraphResponse | null;
  loading: boolean;
}

function buildTooltip(label: string, type: string, properties: Record<string, unknown>): string {
  const typeLabel = type.replace('_', ' ').replace(/\b\w/g, (c) => c.toUpperCase());
  let html = `<div style="font-family: Inter, system-ui, sans-serif; line-height: 1.5;">`;
  html += `<div style="font-weight: 600; font-size: 13px; margin-bottom: 4px;">${label}</div>`;
  html += `<div style="color: rgba(255,255,255,0.5); font-size: 11px;">Type: ${typeLabel}</div>`;

  for (const [key, val] of Object.entries(properties)) {
    if (val !== undefined && val !== null && val !== '') {
      const displayKey = key.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
      html += `<div style="color: rgba(255,255,255,0.5); font-size: 11px;">${displayKey}: ${val}</div>`;
    }
  }
  html += `</div>`;
  return html;
}

export function GraphCanvas({ data, loading }: GraphCanvasProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const networkRef = useRef<Network | null>(null);
  const [selectedEdge, setSelectedEdge] = useState<{ raw: any, fromLabel: string, toLabel: string } | null>(null);

  useEffect(() => {
    if (!containerRef.current || !data || data.nodes.length === 0) {
      // Clean up previous network if data is empty
      if (networkRef.current) {
        networkRef.current.destroy();
        networkRef.current = null;
      }
      return;
    }

    setSelectedEdge(null);

    // Build vis-network DataSets
    const visNodes = new DataSet(
      data.nodes.map((n) => {
        const style = NODE_STYLES[n.type] || NODE_STYLES.raw_material;
        // Scale node size by connection count (for company-supplier: product_count)
        const productCount = n.properties.product_count as number | undefined;
        const sizeOverride = productCount ? Math.min(style.size as number + productCount * 0.5, 50) : undefined;

        return {
          id: n.id,
          label: n.label,
          title: buildTooltip(n.label, n.type, n.properties),
          ...style,
          ...(sizeOverride ? { size: sizeOverride } : {}),
        };
      }),
    );

    const visEdges = new DataSet(
      data.edges.map((e, i) => {
        const style = EDGE_STYLES[e.type] || EDGE_STYLES.sources_from;
        // Scale edge width by product_count if present
        const productCount = e.properties.product_count as number | undefined;
        const widthOverride = productCount
          ? Math.min((style.width as number) + Math.log2(productCount + 1) * 1.5, 8)
          : undefined;

        return {
          id: `edge-${i}`,
          from: e.from,
          to: e.to,
          rawEdge: e,
          ...style,
          ...(widthOverride ? { width: widthOverride } : {}),
        };
      }),
    );

    // Destroy previous network before creating new one
    if (networkRef.current) {
      networkRef.current.destroy();
    }

    const network = new Network(containerRef.current, { nodes: visNodes, edges: visEdges }, NETWORK_OPTIONS);
    networkRef.current = network;

    // Handle edge clicks
    network.on('click', (params) => {
      if (params.edges.length > 0 && params.nodes.length === 0) {
        const edgeId = params.edges[0];
        const edgeData = visEdges.get(edgeId) as any;
        if (edgeData && edgeData.rawEdge) {
          const fromNode = data.nodes.find(n => n.id === edgeData.rawEdge.from);
          const toNode = data.nodes.find(n => n.id === edgeData.rawEdge.to);
          setSelectedEdge({
            raw: edgeData.rawEdge,
            fromLabel: fromNode?.label || 'Unknown',
            toLabel: toNode?.label || 'Unknown',
          });
        }
      } else {
        setSelectedEdge(null);
      }
    });

    return () => {
      network.destroy();
      networkRef.current = null;
    };
  }, [data]);

  return (
    <div className="relative w-full h-full">
      {/* Loading overlay */}
      {loading && (
        <div className="absolute inset-0 z-10 flex items-center justify-center bg-black/40 backdrop-blur-sm rounded-xl">
          <div className="flex items-center gap-3 text-white/60 text-sm">
            <div className="w-5 h-5 border-2 border-violet-500/30 border-t-violet-500 rounded-full animate-spin" />
            Loading graph…
          </div>
        </div>
      )}

      {/* Empty state */}
      {!loading && data && data.nodes.length === 0 && (
        <div className="absolute inset-0 z-10 flex items-center justify-center">
          <div className="text-white/30 text-sm">No data available for this view</div>
        </div>
      )}

      {/* vis-network container */}
      <div
        ref={containerRef}
        className="w-full h-full rounded-xl"
        style={{ minHeight: '400px' }}
      />

      {/* Edge details panel */}
      {selectedEdge && (
        <div className="absolute bottom-4 right-4 z-20 bg-black/80 backdrop-blur-md border border-white/10 rounded-xl p-4 shadow-2xl min-w-[250px] max-w-[320px] pointer-events-auto">
          <div className="flex justify-between items-start mb-2">
            <h3 className="text-white/90 text-sm font-semibold">Relationship Details</h3>
            <button onClick={() => setSelectedEdge(null)} className="text-white/40 hover:text-white/80 transition-colors">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>
            </button>
          </div>
          <div className="text-xs text-white/60 mb-3 pb-3 border-b border-white/10">
            <span className="text-white/80 font-medium">{selectedEdge.fromLabel}</span>
            <span className="mx-2 text-violet-400">→</span>
            <span className="text-white/80 font-medium">{selectedEdge.toLabel}</span>
          </div>
          <div className="space-y-1.5">
            <div className="flex justify-between text-xs">
              <span className="text-white/40">Type</span>
              <span className="text-white/80">{selectedEdge.raw.type.replace('_', ' ')}</span>
            </div>
            {Object.entries(selectedEdge.raw.properties).map(([key, val]) => (
              val !== undefined && val !== null && val !== '' && (
                <div key={key} className="flex justify-between text-xs">
                  <span className="text-white/40 capitalize">{key.replace(/_/g, ' ')}</span>
                  <span className="text-white/80">{String(val)}</span>
                </div>
              )
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
