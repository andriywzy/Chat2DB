export interface ISqlValidationIssue {
  startLineNumber: number;
  startColumn: number;
  endLineNumber: number;
  endColumn: number;
  message: string;
}

interface ISqlStatementRange {
  text: string;
  startOffset: number;
  endOffset: number;
}

const SQL_STARTERS = [
  'select',
  'insert',
  'update',
  'delete',
  'create',
  'alter',
  'drop',
  'truncate',
  'replace',
  'with',
  'show',
  'describe',
  'desc',
  'explain',
  'use',
  'set',
  'call',
  'commit',
  'rollback',
  'grant',
  'revoke',
  'analyze',
  'optimize',
  'rename',
];

export const validateSqlScript = (sql: string): ISqlValidationIssue[] => {
  if (!sql.trim()) {
    return [];
  }

  const statements = splitSqlStatements(sql);
  return statements
    .map((statement) => {
      const message = getStatementIssue(statement.text);
      if (!message) {
        return null;
      }
      return createIssue(sql, statement, message);
    })
    .filter(Boolean) as ISqlValidationIssue[];
};

const splitSqlStatements = (sql: string): ISqlStatementRange[] => {
  const statements: ISqlStatementRange[] = [];
  let startOffset = 0;
  let quote: "'" | '"' | '`' | null = null;
  let lineComment = false;
  let blockComment = false;

  for (let index = 0; index < sql.length; index += 1) {
    const current = sql[index];
    const next = sql[index + 1];
    const prev = sql[index - 1];

    if (lineComment) {
      if (current === '\n') {
        lineComment = false;
      }
      continue;
    }

    if (blockComment) {
      if (current === '*' && next === '/') {
        blockComment = false;
        index += 1;
      }
      continue;
    }

    if (quote) {
      if (current === quote && prev !== '\\') {
        if (quote !== "'" || next !== "'") {
          quote = null;
        } else {
          index += 1;
        }
      }
      continue;
    }

    if (current === '-' && next === '-') {
      lineComment = true;
      index += 1;
      continue;
    }

    if (current === '#') {
      lineComment = true;
      continue;
    }

    if (current === '/' && next === '*') {
      blockComment = true;
      index += 1;
      continue;
    }

    if (current === "'" || current === '"' || current === '`') {
      quote = current;
      continue;
    }

    if (current === ';') {
      pushStatement(statements, sql, startOffset, index + 1);
      startOffset = index + 1;
    }
  }

  pushStatement(statements, sql, startOffset, sql.length);
  return statements;
};

const pushStatement = (statements: ISqlStatementRange[], sql: string, startOffset: number, endOffset: number) => {
  const text = sql.slice(startOffset, endOffset);
  if (!text.trim()) {
    return;
  }
  statements.push({
    text,
    startOffset,
    endOffset,
  });
};

const getStatementIssue = (statementText: string): string | null => {
  const inspection = inspectSqlText(statementText);
  if (inspection) {
    return inspection;
  }

  const sql = stripSqlComments(statementText).replace(/;\s*$/, '').trim();
  if (!sql) {
    return null;
  }

  const normalized = sql.replace(/\s+/g, ' ');
  const starter = normalized.match(/^([a-z_]+)/i)?.[1]?.toLowerCase();

  if (!starter || !SQL_STARTERS.includes(starter)) {
    return 'SQL statement should start with a supported MySQL keyword.';
  }

  if (/[,+\-*/=<>]$/.test(normalized) || /\b(and|or|where|from|join|on|set|values)\s*$/i.test(normalized)) {
    return 'SQL statement appears incomplete.';
  }

  const rules: Record<string, Array<[RegExp, string]>> = {
    select: [[/^select\s+.+/i, 'SELECT should include selected fields.']],
    insert: [[/^insert\s+(ignore\s+)?into\s+\S+[\s\S]+/i, 'INSERT should include INTO and inserted values/query.']],
    update: [[/^update\s+\S+[\s\S]*\sset\s+.+/i, 'UPDATE should include SET assignments.']],
    delete: [[/^delete\s+[\s\S]*\bfrom\s+\S+/i, 'DELETE should include FROM and a target table.']],
    create: [
      [
        /^create\s+(or\s+replace\s+)?(table|view|index|database|schema|procedure|function|trigger)\b/i,
        'CREATE should include a supported object type.',
      ],
    ],
    alter: [[/^alter\s+(table|database|schema|view)\b/i, 'ALTER should include a supported object type.']],
    drop: [
      [
        /^drop\s+(table|view|database|schema|index|procedure|function|trigger)\b/i,
        'DROP should include a supported object type.',
      ],
    ],
    truncate: [[/^truncate\s+(table\s+)?\S+/i, 'TRUNCATE should include a target table.']],
    replace: [[/^replace\s+(into\s+)?\S+[\s\S]+/i, 'REPLACE should include a target table and values/query.']],
    with: [[/^with\s+.+\bselect\b.+/i, 'WITH should include a CTE and SELECT query.']],
    use: [[/^use\s+\S+/i, 'USE should include a database name.']],
    set: [[/^set\s+.+/i, 'SET should include assignments.']],
    call: [[/^call\s+\S+/i, 'CALL should include a routine name.']],
  };

  const rule = rules[starter]?.[0];
  if (rule && !rule[0].test(normalized)) {
    return rule[1];
  }

  if (starter === 'select' && /\bfrom\s*$/i.test(normalized)) {
    return 'SELECT FROM should include a table name.';
  }

  return null;
};

const inspectSqlText = (sql: string): string | null => {
  let quote: "'" | '"' | '`' | null = null;
  let blockComment = false;
  let parenDepth = 0;

  for (let index = 0; index < sql.length; index += 1) {
    const current = sql[index];
    const next = sql[index + 1];
    const prev = sql[index - 1];

    if (blockComment) {
      if (current === '*' && next === '/') {
        blockComment = false;
        index += 1;
      }
      continue;
    }

    if (quote) {
      if (current === quote && prev !== '\\') {
        if (quote !== "'" || next !== "'") {
          quote = null;
        } else {
          index += 1;
        }
      }
      continue;
    }

    if (current === '/' && next === '*') {
      blockComment = true;
      index += 1;
      continue;
    }

    if (current === "'" || current === '"' || current === '`') {
      quote = current;
      continue;
    }

    if (current === '(') {
      parenDepth += 1;
    } else if (current === ')') {
      parenDepth -= 1;
      if (parenDepth < 0) {
        return 'SQL statement has unmatched parentheses.';
      }
    }
  }

  if (quote) {
    return 'SQL statement has an unclosed quote.';
  }
  if (blockComment) {
    return 'SQL statement has an unclosed block comment.';
  }
  if (parenDepth !== 0) {
    return 'SQL statement has unmatched parentheses.';
  }
  return null;
};

const stripSqlComments = (sql: string) => {
  return sql
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/--[^\n\r]*/g, ' ')
    .replace(/#[^\n\r]*/g, ' ');
};

const createIssue = (sql: string, statement: ISqlStatementRange, message: string): ISqlValidationIssue => {
  const leadingWhitespace = statement.text.match(/^\s*/)?.[0].length || 0;
  const trailingWhitespace = statement.text.match(/\s*$/)?.[0].length || 0;
  const start = offsetToPosition(sql, statement.startOffset + leadingWhitespace);
  const end = offsetToPosition(sql, Math.max(statement.startOffset + leadingWhitespace + 1, statement.endOffset - trailingWhitespace));

  return {
    startLineNumber: start.lineNumber,
    startColumn: start.column,
    endLineNumber: end.lineNumber,
    endColumn: end.column,
    message,
  };
};

const offsetToPosition = (text: string, offset: number) => {
  const safeOffset = Math.max(0, Math.min(offset, text.length));
  const before = text.slice(0, safeOffset);
  const lines = before.split(/\n/);
  return {
    lineNumber: lines.length,
    column: lines[lines.length - 1].length + 1,
  };
};
