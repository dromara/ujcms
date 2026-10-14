// 敏感词 / 易错词的导入解析与导出格式（docs/spec/word-dictionaries/plan.md Phase 7）。两者同处一地：
// 导出的文件须能原样导入。文本文件，每行一条；易错词一行为“易错词<TAB>正确词”。启用状态不在文件里

export type WordMode = 'sensitive' | 'error';

export interface WordEntry {
  name: string;
  correct?: string;
}

export interface ParsedWords {
  entries: WordEntry[];
  // 无法识别的行号（从 1 起）
  badLines: number[];
}

/** 词条最大长度（与服务端 @Length 一致） */
const WORD_MAX_LENGTH = 30;
// 易错词与正确词的分隔符，按此先后取第一个出现的：Tab（从表格复制两列、本系统导出的文件）优先，手工录入可用等号或逗号
const SEPARATORS = ['\t', '=', ',', '，'];

export const parseWordLines = (text: string, mode: WordMode): ParsedWords => {
  const entries: WordEntry[] = [];
  const badLines: number[] = [];
  text.split(/\r\n|\r|\n/).forEach((line, index) => {
    if (line.trim() === '') {
      return;
    }
    if (mode === 'sensitive') {
      const name = line.trim();
      if (name.length > WORD_MAX_LENGTH) {
        badLines.push(index + 1);
      } else {
        entries.push({ name });
      }
      return;
    }
    const separator = SEPARATORS.find((it) => line.includes(it));
    const position = separator == null ? -1 : line.indexOf(separator);
    const name = position < 0 ? '' : line.slice(0, position).trim();
    const correct = position < 0 ? '' : line.slice(position + 1).trim();
    if (name === '' || correct === '' || name === correct || name.length > WORD_MAX_LENGTH || correct.length > WORD_MAX_LENGTH) {
      badLines.push(index + 1);
    } else {
      entries.push({ name, correct });
    }
  });
  return { entries, badLines };
};

export const serializeWords = (rows: WordEntry[], mode: WordMode): string => rows.map((row) => (mode === 'sensitive' ? row.name : `${row.name}\t${row.correct ?? ''}`)).join('\r\n');
