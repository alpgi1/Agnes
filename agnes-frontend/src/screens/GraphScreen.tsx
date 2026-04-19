import { motion } from 'framer-motion';
import { useGraphData } from '@/hooks/useGraphData';
import { ViewSelector } from '@/components/graph/ViewSelector';
import { FilterDropdown } from '@/components/graph/FilterDropdown';
import { GraphCanvas } from '@/components/graph/GraphCanvas';
import { GraphLegend } from '@/components/graph/GraphLegend';

export function GraphScreen() {
  const {
    view,
    setView,
    companyId,
    setCompanyId,
    csCompanyId,
    setCsCompanyId,
    csSupplierId,
    setCsSupplierId,
    data,
    companies,
    suppliers,
    loading,
    error,
  } = useGraphData();

  return (
    <motion.div
      className="flex flex-col w-full h-full"
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, ease: 'easeOut' }}
    >
      {/* Toolbar */}
      <div className="flex flex-wrap items-center gap-3 px-5 py-3 border-b border-white/[0.05]">
        <ViewSelector view={view} onChange={setView} disabled={loading} />

        {/* Filters for company-supplier view */}
        {view === 'company-supplier' && (
          <motion.div
            initial={{ opacity: 0, x: -8 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.2 }}
            className="flex gap-2"
          >
            <FilterDropdown
              options={companies}
              value={csCompanyId}
              onChange={setCsCompanyId}
              placeholder="All Companies"
              disabled={loading || companies.length === 0}
            />
            <FilterDropdown
              options={suppliers}
              value={csSupplierId}
              onChange={setCsSupplierId}
              placeholder="All Suppliers"
              disabled={loading || suppliers.length === 0}
            />
          </motion.div>
        )}

        {/* Filter for company-product view */}
        {view === 'company-product' && companies.length > 0 && (
          <motion.div
            initial={{ opacity: 0, x: -8 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.2 }}
          >
            <FilterDropdown
              options={companies}
              value={companyId}
              onChange={setCompanyId}
              placeholder="Select a company..."
              disabled={loading}
            />
          </motion.div>
        )}

        {/* Error indicator */}
        {error && (
          <div className="text-xs text-red-400/70 ml-auto">
            {error}
          </div>
        )}
      </div>

      {/* Graph canvas — fills remaining space */}
      <div className="flex-1 min-h-0 px-3 py-2">
        <GraphCanvas data={data} loading={loading} />
      </div>

      {/* Legend */}
      <div className="px-5 py-2.5 border-t border-white/[0.05]">
        <GraphLegend
          view={view}
          nodeCount={data?.meta.nodeCount ?? 0}
          edgeCount={data?.meta.edgeCount ?? 0}
        />
      </div>
    </motion.div>
  );
}
