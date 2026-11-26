/*
 * Copyright 2026 杭州开云集致科技有限公司
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.clougence.clouddm.console.web.component.workspace;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.clougence.clouddm.api.common.exception.ErrorMessageException;
import com.clougence.clouddm.api.sidecar.session.workspace.WorkspaceSourceKind;
import com.clougence.clouddm.console.web.global.i18n.DmI18nUtils;
import com.clougence.clouddm.console.web.global.i18n.I18nDmMsgKeys;
import com.clougence.utils.StringUtils;

/**
 * Lexer-aware DuckDB Workspace SQL splitter and cross-datasource reference rewriter.
 * Supports:
 * <ul>
 *   <li>{@code #ds[.catalog.schema].table}</li>
 *   <li>{@code #ds[.catalog.schema].table.#filter('cond' | $$cond$$)}</li>
 *   <li>{@code #ds[.catalog.schema].#query('sql' | $$sql$$)}</li>
 * </ul>
 */
public final class WorkspaceSqlRewriter {

    private static final Set<String> FORBIDDEN_LEADING_KEYWORDS = Set.of( //
            "ATTACH", "DETACH", "COPY", "INSTALL", "LOAD", "PRAGMA", "SET", "RESET", "EXPORT", "IMPORT", "CALL", "CHECKPOINT", "FORCE" //
    );

    private static final Set<String> ALLOWED_LEADING_KEYWORDS   = Set.of( //
            "SELECT", "WITH", "FROM", "VALUES", "TABLE", "EXPLAIN", "DESCRIBE", "DESC", "SHOW", "SUMMARIZE", "PIVOT", "UNPIVOT", "CREATE", "DROP", "INSERT", "UPDATE", "DELETE" //
    );

    private static final Set<String> FORBIDDEN_FUNCTION_NAMES   = Set.of( //
            "cdm_remote_table", "cdm_remote_query", "read_csv", "read_csv_auto", "read_parquet", "read_json", "read_json_auto", "read_ndjson", "read_text", "read_blob", "glob",
            "parquet_scan", "csv_scan", "sqlite_scan", "postgres_scan", "mysql_scan", "http_get", "duckdb_secrets" //
    );

    private WorkspaceSqlRewriter(){
    }

    public static List<WorkspaceStatementPlan> parseAndRewrite(String script, boolean isExplain) {
        if (StringUtils.isBlank(script)) {
            return List.of();
        }
        List<String> statements = splitStatements(script);
        List<WorkspaceStatementPlan> results = new ArrayList<>();
        int refCounter = 0;

        for (String stmt : statements) {
            String trimmed = stmt.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            validateOuterStatement(trimmed);

            List<WorkspaceSourceRef> refs = new ArrayList<>();
            StringBuilder out = new StringBuilder(trimmed.length() + 64);
            int i = 0;
            int len = trimmed.length();

            while (i < len) {
                char c = trimmed.charAt(i);

                // Skip line comment --
                if (c == '-' && i + 1 < len && trimmed.charAt(i + 1) == '-') {
                    int end = findLineEnd(trimmed, i + 2);
                    out.append(trimmed, i, end);
                    i = end;
                    continue;
                }
                // Skip block comment /* ... */
                if (c == '/' && i + 1 < len && trimmed.charAt(i + 1) == '*') {
                    int end = findBlockCommentEnd(trimmed, i + 2);
                    out.append(trimmed, i, end);
                    i = end;
                    continue;
                }
                // Skip single-quoted string
                if (c == '\'') {
                    int end = findQuotedEnd(trimmed, i, '\'');
                    out.append(trimmed, i, end);
                    i = end;
                    continue;
                }
                // Skip double-quoted identifier
                if (c == '"') {
                    int end = findQuotedEnd(trimmed, i, '"');
                    out.append(trimmed, i, end);
                    i = end;
                    continue;
                }
                // Skip dollar-quoted string $tag$...$tag$
                if (c == '$') {
                    String tag = tryReadDollarTag(trimmed, i);
                    if (tag != null) {
                        int end = findDollarQuoteEnd(trimmed, i, tag);
                        out.append(trimmed, i, end);
                        i = end;
                        continue;
                    }
                }
                // Check forbidden function calls outside literals/comments
                if (isIdentStart(c)) {
                    int wordEnd = readUnquotedIdentEnd(trimmed, i);
                    String word = trimmed.substring(i, wordEnd);
                    String lower = word.toLowerCase(Locale.ROOT);
                    if (FORBIDDEN_FUNCTION_NAMES.contains(lower)) {
                        int nextNonWs = skipWhitespace(trimmed, wordEnd);
                        if (nextNonWs < len && trimmed.charAt(nextNonWs) == '(') {
                            throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.WORKSPACE_SQL_FORBIDDEN_ERROR.name(), word + "(...)"));
                        }
                    }
                    out.append(trimmed, i, wordEnd);
                    i = wordEnd;
                    continue;
                }
                // Cross-datasource reference starting with '#'
                if (c == '#' && i + 1 < len && isDatasourceRefStart(trimmed.charAt(i + 1))) {
                    refCounter++;
                    String refId = "ref_" + refCounter;
                    WorkspaceRefParseResult parsed = parseReference(trimmed, i, refId);
                    refs.add(parsed.getRef());
                    if (parsed.getRef().getKind() == WorkspaceSourceKind.QUERY) {
                        out.append("cdm_remote_query('").append(refId).append("')");
                    } else {
                        out.append("cdm_remote_table('").append(refId).append("')");
                    }
                    i = parsed.getNextIndex();
                    continue;
                }

                out.append(c);
                i++;
            }

            String rewritten = out.toString();
            if (isExplain && !leadingWord(rewritten).equals("EXPLAIN")) {
                rewritten = "EXPLAIN " + rewritten;
            }
            results.add(new WorkspaceStatementPlan(trimmed, rewritten, refs));
        }

        return results;
    }

    private static void validateOuterStatement(String sql) {
        String first = leadingWord(sql);
        if (first.isEmpty()) {
            return;
        }
        if (FORBIDDEN_LEADING_KEYWORDS.contains(first)) {
            throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.WORKSPACE_SQL_FORBIDDEN_ERROR.name(), first));
        }
        if (!ALLOWED_LEADING_KEYWORDS.contains(first)) {
            throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.WORKSPACE_SQL_FORBIDDEN_ERROR.name(), first));
        }
    }

    private static String leadingWord(String sql) {
        int i = skipWhitespaceAndComments(sql, 0);
        int len = sql.length();
        while (i < len && sql.charAt(i) == '(') {
            i = skipWhitespaceAndComments(sql, i + 1);
        }
        int start = i;
        while (i < len && Character.isLetter(sql.charAt(i))) {
            i++;
        }
        return sql.substring(start, i).toUpperCase(Locale.ROOT);
    }

    private static WorkspaceRefParseResult parseReference(String sql, int hashPos, String refId) {
        int pos = hashPos + 1;
        WorkspaceParsedToken dsToken = readIdent(sql, pos);
        if (dsToken == null || StringUtils.isBlank(dsToken.getValue())) {
            throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.WORKSPACE_SQL_FORBIDDEN_ERROR.name(), "#"));
        }
        String dsName = dsToken.getValue();
        pos = dsToken.getNextIndex();

        List<String> parts = new ArrayList<>();
        String filterExpr = null;
        String rawQuery = null;
        WorkspaceSourceKind kind = WorkspaceSourceKind.TABLE;

        int len = sql.length();
        while (pos < len && sql.charAt(pos) == '.') {
            pos++;
            if (pos < len && sql.charAt(pos) == '#') {
                WorkspaceParsedToken modifier = readIdent(sql, pos + 1);
                if (modifier == null) {
                    throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.WORKSPACE_SQL_FORBIDDEN_ERROR.name(), ".#"));
                }
                String modName = modifier.getValue().toLowerCase(Locale.ROOT);
                int openParen = skipWhitespace(sql, modifier.getNextIndex());
                if (openParen >= len || sql.charAt(openParen) != '(') {
                    throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.WORKSPACE_SQL_FORBIDDEN_ERROR.name(), ".#" + modName));
                }
                WorkspaceParsedToken arg = readSingleStringArg(sql, openParen);
                pos = arg.getNextIndex();
                if ("filter".equals(modName)) {
                    filterExpr = arg.getValue();
                    kind = WorkspaceSourceKind.TABLE;
                    break;
                } else if ("query".equals(modName)) {
                    rawQuery = arg.getValue();
                    kind = WorkspaceSourceKind.QUERY;
                    break;
                } else {
                    throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.WORKSPACE_SQL_FORBIDDEN_ERROR.name(), ".#" + modName));
                }
            } else {
                WorkspaceParsedToken seg = readIdent(sql, pos);
                if (seg == null || StringUtils.isBlank(seg.getValue())) {
                    throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.WORKSPACE_SQL_FORBIDDEN_ERROR.name(), "#" + dsName + "."));
                }
                parts.add(seg.getValue());
                pos = seg.getNextIndex();
            }
        }

        if (kind == WorkspaceSourceKind.TABLE && parts.isEmpty()) {
            throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.WORKSPACE_SQL_FORBIDDEN_ERROR.name(), "#" + dsName));
        }
        if (kind == WorkspaceSourceKind.QUERY && StringUtils.isBlank(rawQuery)) {
            throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.WORKSPACE_SQL_FORBIDDEN_ERROR.name(), "#query()"));
        }

        WorkspaceSourceRef ref = new WorkspaceSourceRef(refId, kind, dsName, parts, filterExpr, rawQuery);
        return new WorkspaceRefParseResult(ref, pos);
    }

    private static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        int start = 0;
        int i = 0;
        int len = script.length();

        while (i < len) {
            char c = script.charAt(i);
            if (c == '-' && i + 1 < len && script.charAt(i + 1) == '-') {
                i = findLineEnd(script, i + 2);
                continue;
            }
            if (c == '/' && i + 1 < len && script.charAt(i + 1) == '*') {
                i = findBlockCommentEnd(script, i + 2);
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                i = findQuotedEnd(script, i, c);
                continue;
            }
            if (c == '$') {
                String tag = tryReadDollarTag(script, i);
                if (tag != null) {
                    i = findDollarQuoteEnd(script, i, tag);
                    continue;
                }
            }
            if (c == ';') {
                String piece = script.substring(start, i).trim();
                if (!piece.isEmpty()) {
                    statements.add(piece);
                }
                start = i + 1;
            }
            i++;
        }

        if (start < len) {
            String tail = script.substring(start).trim();
            if (!tail.isEmpty()) {
                statements.add(tail);
            }
        }
        return statements;
    }

    private static WorkspaceParsedToken readSingleStringArg(String sql, int openParenPos) {
        int pos = skipWhitespace(sql, openParenPos + 1);
        int len = sql.length();
        if (pos >= len) {
            throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.WORKSPACE_SQL_FORBIDDEN_ERROR.name(), "unclosed ("));
        }

        String value;
        if (sql.charAt(pos) == '\'') {
            int end = findQuotedEnd(sql, pos, '\'');
            String raw = sql.substring(pos + 1, end - 1);
            value = raw.replace("''", "'");
            pos = end;
        } else if (sql.charAt(pos) == '$') {
            String tag = tryReadDollarTag(sql, pos);
            if (tag == null) {
                throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.WORKSPACE_SQL_FORBIDDEN_ERROR.name(), "invalid dollar quote"));
            }
            int end = findDollarQuoteEnd(sql, pos, tag);
            value = sql.substring(pos + tag.length(), end - tag.length());
            pos = end;
        } else {
            throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.WORKSPACE_SQL_FORBIDDEN_ERROR.name(), "expected string literal in #filter/#query"));
        }

        pos = skipWhitespace(sql, pos);
        if (pos >= len || sql.charAt(pos) != ')') {
            throw new ErrorMessageException(DmI18nUtils.getMessage(I18nDmMsgKeys.WORKSPACE_SQL_FORBIDDEN_ERROR.name(), "missing closing ')' in #filter/#query"));
        }
        return new WorkspaceParsedToken(value, pos + 1);
    }

    private static WorkspaceParsedToken readIdent(String sql, int pos) {
        int len = sql.length();
        if (pos >= len) {
            return null;
        }
        char c = sql.charAt(pos);
        if (c == '"' || c == '`') {
            int end = findQuotedEnd(sql, pos, c);
            String raw = sql.substring(pos + 1, end - 1);
            String doubled = String.valueOf(c) + c;
            return new WorkspaceParsedToken(raw.replace(doubled, String.valueOf(c)), end);
        }
        if (isDatasourceRefStart(c)) {
            int end = pos + 1;
            while (end < len && isDatasourceRefPart(sql.charAt(end))) {
                end++;
            }
            return new WorkspaceParsedToken(sql.substring(pos, end), end);
        }
        return null;
    }

    private static boolean isDatasourceRefStart(char c) {
        return c == '"' || c == '`' || c == '_' || Character.isLetterOrDigit(c);
    }

    private static boolean isDatasourceRefPart(char c) {
        return c == '_' || c == '-' || c == '$' || Character.isLetterOrDigit(c);
    }

    private static boolean isIdentStart(char c) {
        return c == '_' || Character.isLetter(c);
    }

    private static int readUnquotedIdentEnd(String sql, int start) {
        int i = start + 1;
        int len = sql.length();
        while (i < len && (sql.charAt(i) == '_' || Character.isLetterOrDigit(sql.charAt(i)))) {
            i++;
        }
        return i;
    }

    private static int skipWhitespace(String s, int pos) {
        int len = s.length();
        while (pos < len && Character.isWhitespace(s.charAt(pos))) {
            pos++;
        }
        return pos;
    }

    private static int skipWhitespaceAndComments(String s, int pos) {
        int len = s.length();
        while (pos < len) {
            pos = skipWhitespace(s, pos);
            if (pos + 1 < len && s.charAt(pos) == '-' && s.charAt(pos + 1) == '-') {
                pos = findLineEnd(s, pos + 2);
                continue;
            }
            if (pos + 1 < len && s.charAt(pos) == '/' && s.charAt(pos + 1) == '*') {
                pos = findBlockCommentEnd(s, pos + 2);
                continue;
            }
            break;
        }
        return pos;
    }

    private static int findLineEnd(String s, int pos) {
        int idx = s.indexOf('\n', pos);
        return idx < 0 ? s.length() : idx + 1;
    }

    private static int findBlockCommentEnd(String s, int pos) {
        int idx = s.indexOf("*/", pos);
        return idx < 0 ? s.length() : idx + 2;
    }

    private static int findQuotedEnd(String s, int start, char quote) {
        int i = start + 1;
        int len = s.length();
        while (i < len) {
            if (s.charAt(i) == quote) {
                if (i + 1 < len && s.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return len;
    }

    private static String tryReadDollarTag(String s, int start) {
        int len = s.length();
        if (start >= len || s.charAt(start) != '$') {
            return null;
        }
        int i = start + 1;
        if (i < len && s.charAt(i) == '$') {
            return "$$";
        }
        if (i < len && (s.charAt(i) == '_' || Character.isLetter(s.charAt(i)))) {
            i++;
            while (i < len && (s.charAt(i) == '_' || Character.isLetterOrDigit(s.charAt(i)))) {
                i++;
            }
            if (i < len && s.charAt(i) == '$') {
                return s.substring(start, i + 1);
            }
        }
        return null;
    }

    private static int findDollarQuoteEnd(String s, int start, String tag) {
        int idx = s.indexOf(tag, start + tag.length());
        return idx < 0 ? s.length() : idx + tag.length();
    }
}
