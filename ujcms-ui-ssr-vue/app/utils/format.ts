/**
 * Small formatting helpers used by components rendering article lists.
 *
 * Template-theme baseline references (for parity):
 * - `{{ date | date: "%Y-%m-%d" }}` → formatDate(iso, 'yyyy-MM-dd')
 * - `{{ html | strip_html | truncate: 60, '...' }}` → truncate(stripHtml(html), 60)
 *
 * Kept dependency-free (no dayjs). If the UI grows i18n-aware date
 * formatting, swap in `Intl.DateTimeFormat` with a locale prop here.
 */

const PAD2 = (n: number): string => (n < 10 ? `0${n}` : String(n));

export function formatDate(iso: string | undefined | null, pattern: 'yyyy-MM-dd' | 'yyyy-MM-dd HH:mm' = 'yyyy-MM-dd'): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const yyyy = d.getFullYear();
  const mm = PAD2(d.getMonth() + 1);
  const dd = PAD2(d.getDate());
  if (pattern === 'yyyy-MM-dd') return `${yyyy}-${mm}-${dd}`;
  const HH = PAD2(d.getHours());
  const MM = PAD2(d.getMinutes());
  return `${yyyy}-${mm}-${dd} ${HH}:${MM}`;
}

export function stripHtml(html: string | undefined | null): string {
  if (!html) return '';
  return html
    .replace(/<[^>]*>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

export function truncate(text: string | undefined | null, max: number, ellipsis = '...'): string {
  if (!text) return '';
  if (text.length <= max) return text;
  return text.slice(0, max) + ellipsis;
}
