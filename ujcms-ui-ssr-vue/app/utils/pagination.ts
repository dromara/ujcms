/**
 * Spring serialises Long as string for JS precision, so PagedModel's page
 * summary fields (size / number / totalPages / totalElements) are strings.
 * Callers need an actual number to drive arithmetic and template comparisons.
 */
export function toPageInt(value: number | string | null | undefined, fallback = 0): number {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string') {
    const parsed = Number.parseInt(value, 10);
    if (Number.isFinite(parsed)) return parsed;
  }
  return fallback;
}

/** Parse a `?page=N` query value (string | string[] | undefined) into a 1-based page number. */
export function parsePageParam(raw: unknown): number {
  const value = Array.isArray(raw) ? raw[0] : raw;
  const n = Number.parseInt(typeof value === 'string' ? value : '1', 10);
  return Number.isFinite(n) && n > 0 ? n : 1;
}

/** Pick the first string out of a query value (string | string[] | undefined). */
export function pickQueryString(raw: unknown): string {
  if (Array.isArray(raw)) return typeof raw[0] === 'string' ? raw[0] : '';
  return typeof raw === 'string' ? raw : '';
}

/** Flatten a vue-router query object to first-string values. */
export function flattenQuery(query: Record<string, unknown>): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(query)) {
    if (typeof v === 'string') out[k] = v;
    else if (Array.isArray(v) && typeof v[0] === 'string' && v[0]) out[k] = v[0];
  }
  return out;
}
