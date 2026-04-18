export type ChatRole = 'user' | 'assistant';

export interface ChatMessage {
  id: string;
  role: ChatRole;
  content: string;
  mode?: 'optimize' | 'knowledge';
  timestamp: number;
  metadata?: MessageMetadata;
  error?: string;
}

export interface MessageMetadata {
  sessionId?: string;
  optimizersRun?: string[];
  complianceStatus?: 'compliant' | 'uncertain' | 'non-compliant' | 'not_applicable' | 'pending';
  findings?: Finding[];
  sqlUsed?: string;
  rowCount?: number;
  truncated?: boolean;
  durationMs?: number;
  scope?: { type: string; value: string | null };
  routerReasoning?: string;
}

export interface EvidenceItem {
  sourceType: string;
  sourceRef: string;
  note: string;
  url: string | null;
}

export interface ComplianceRelevance {
  affectsAllergens: boolean;
  affectsAnimalOrigin: boolean;
  affectsLabelClaims: string[];
  affectsNovelFoodStatus: boolean;
  changesIngredientChemistry: boolean;
  ingredientKeywordsForLookup: string[];
  preFilterFlags: string[];
}

export interface Finding {
  findingId: string;
  optimizer: string;
  type: string;
  canonicalName: string | null;
  affectedSkus: { sku: string; companyId: number }[];
  affectedFinishedGoods: string[];
  reasoning: string;
  estimatedImpact: string;
  complianceRelevance: ComplianceRelevance;
  complianceStatus: string;
  derivedFrom: string[];
  proposedReplacement: {
    ingredientName: string;
    shortJustification: string;
    equivalenceClass: string;
  } | null;
  redundancyPair: {
    keepSku: string;
    removeSku: string;
    sharedFunction: string;
    keepRationale: string;
  } | null;
  complianceEvidence: EvidenceItem[];
}

export interface OptimizeRequest {
  prompt: string;
  history?: { role: string; content: string }[];
  sessionId?: string;
}

export interface OptimizeResponse {
  sessionId: string;
  markdown: string;
  optimizersRun: string[];
  scope: { type: string; value: string | null };
  routerReasoning: string;
  findings: Finding[];
  complianceStatus: string;
  durationMs: number;
}

export interface KnowledgeRequest extends OptimizeRequest {}

export interface KnowledgeResponse {
  sessionId: string;
  markdown: string;
  sqlUsed: string;
  rowCount: number;
  truncated: boolean;
  durationMs: number;
}
