import type {
  OptimizeRequest,
  OptimizeResponse,
  KnowledgeRequest,
  KnowledgeResponse,
  GraphResponse,
  CompanyOption,
  SupplierOption,
} from './types';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export class ApiError extends Error {
  status: number;
  body?: unknown;

  constructor(status: number, message: string, body?: unknown) {
    super(message);
    this.status = status;
    this.body = body;
  }
}

async function post<TReq, TRes>(path: string, body: TReq): Promise<TRes> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 180_000);

  try {
    const res = await fetch(`${BASE_URL}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: controller.signal,
    });

    const text = await res.text();
    let parsed: unknown;
    try {
      parsed = text ? JSON.parse(text) : null;
    } catch {
      parsed = text;
    }

    if (!res.ok) {
      const message =
        typeof parsed === 'object' && parsed && 'message' in parsed
          ? String((parsed as Record<string, unknown>).message)
          : `HTTP ${res.status}`;
      throw new ApiError(res.status, message, parsed);
    }

    return parsed as TRes;
  } catch (e) {
    if (e instanceof ApiError) throw e;
    if (e instanceof DOMException && e.name === 'AbortError') {
      throw new ApiError(0, 'Request timed out after 180 seconds');
    }
    throw new ApiError(0, e instanceof Error ? e.message : 'Network error');
  } finally {
    clearTimeout(timeout);
  }
}

async function get<TRes>(path: string): Promise<TRes> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 30_000);

  try {
    const res = await fetch(`${BASE_URL}${path}`, {
      signal: controller.signal,
    });

    const text = await res.text();
    let parsed: unknown;
    try {
      parsed = text ? JSON.parse(text) : null;
    } catch {
      parsed = text;
    }

    if (!res.ok) {
      const message =
        typeof parsed === 'object' && parsed && 'message' in parsed
          ? String((parsed as Record<string, unknown>).message)
          : `HTTP ${res.status}`;
      throw new ApiError(res.status, message, parsed);
    }

    return parsed as TRes;
  } catch (e) {
    if (e instanceof ApiError) throw e;
    if (e instanceof DOMException && e.name === 'AbortError') {
      throw new ApiError(0, 'Request timed out after 30 seconds');
    }
    throw new ApiError(0, e instanceof Error ? e.message : 'Network error');
  } finally {
    clearTimeout(timeout);
  }
}

export const api = {
  optimize: (req: OptimizeRequest) =>
    post<OptimizeRequest, OptimizeResponse>('/api/optimize', req),

  knowledge: (req: KnowledgeRequest) =>
    post<KnowledgeRequest, KnowledgeResponse>('/api/knowledge', req),

  health: async () => {
    try {
      const res = await fetch(`${BASE_URL}/api/health`);
      return res.ok;
    } catch {
      return false;
    }
  },

  // ── Graph endpoints ───────────────────────────────────────────────

  graphCompanySupplier: (companyId?: number, supplierId?: number) => {
    const params = new URLSearchParams();
    if (companyId != null) params.append('companyId', companyId.toString());
    if (supplierId != null) params.append('supplierId', supplierId.toString());
    const qs = params.toString();
    return get<GraphResponse>(`/api/graph/company-supplier${qs ? `?${qs}` : ''}`);
  },

  graphCompanyProduct: (companyId?: number) =>
    get<GraphResponse>(
      `/api/graph/company-product${companyId != null ? `?companyId=${companyId}` : ''}`,
    ),

  graphProductSupplier: () =>
    get<GraphResponse>('/api/graph/product-supplier'),

  graphCompanies: () =>
    get<CompanyOption[]>('/api/graph/companies'),

  graphSuppliers: () =>
    get<SupplierOption[]>('/api/graph/suppliers'),
};
