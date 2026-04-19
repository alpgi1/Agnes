import { useState, useEffect, useRef, useCallback } from 'react';
import { api } from '@/api/client';
import type { GraphView, GraphResponse, CompanyOption, SupplierOption } from '@/api/types';

export function useGraphData() {
  const [view, setView] = useState<GraphView>('company-supplier');
  const [companyId, setCompanyId] = useState<number | undefined>();
  
  // Specific filters for company-supplier graph
  const [csCompanyId, setCsCompanyId] = useState<number | undefined>();
  const [csSupplierId, setCsSupplierId] = useState<number | undefined>();

  const [data, setData] = useState<GraphResponse | null>(null);
  const [companies, setCompanies] = useState<CompanyOption[]>([]);
  const [suppliers, setSuppliers] = useState<SupplierOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const cache = useRef<Record<string, GraphResponse>>({});

  // Load companies and suppliers on mount
  useEffect(() => {
    Promise.all([api.graphCompanies(), api.graphSuppliers()])
      .then(([c, s]) => {
        setCompanies(c);
        setSuppliers(s);
        // Default to first company for company-product view
        if (c.length > 0 && companyId === undefined) {
          setCompanyId(c[0].Id);
        }
      })
      .catch(() => {
        // silently ignore
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Build cache key
  const cacheKey =
    view === 'company-product'
      ? `${view}-${companyId ?? 'all'}`
      : view === 'company-supplier'
      ? `${view}-${csCompanyId ?? 'all'}-${csSupplierId ?? 'all'}`
      : view;

  // Fetch graph data when view or filters change
  useEffect(() => {
    // Skip if company-product but no companyId selected yet
    if (view === 'company-product' && companyId === undefined) return;

    // Return cached data if available
    if (cache.current[cacheKey]) {
      setData(cache.current[cacheKey]);
      setError(null);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);

    const fetchData = async () => {
      try {
        let response: GraphResponse;
        switch (view) {
          case 'company-supplier':
            response = await api.graphCompanySupplier(csCompanyId, csSupplierId);
            break;
          case 'company-product':
            response = await api.graphCompanyProduct(companyId);
            break;
          case 'product-supplier':
            response = await api.graphProductSupplier();
            break;
        }
        if (!cancelled) {
          cache.current[cacheKey] = response;
          setData(response);
        }
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : 'Failed to load graph');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    fetchData();
    return () => { cancelled = true; };
  }, [view, companyId, csCompanyId, csSupplierId, cacheKey]);

  // Invalidate cache for a specific company-product view
  const invalidateCache = useCallback((key?: string) => {
    if (key) {
      delete cache.current[key];
    } else {
      cache.current = {};
    }
  }, []);

  return {
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
    invalidateCache,
  };
}
