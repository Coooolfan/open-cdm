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
package com.clougence.clouddm.worker.component.workspace;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import org.duckdb.DuckDBColumnType;
import org.duckdb.DuckDBWritableVector;

import com.clougence.clouddm.base.metadata.ds.ColMetaData;
import com.clougence.clouddm.sdk.execute.session.result.fetcher.ValueFetcher;
import com.clougence.clouddm.sdk.execute.session.result.fetcher.ValueFetcherContext;
import com.clougence.clouddm.sdk.execute.session.result.fetcher.ValueType;
import com.clougence.utils.HexadecimalUtils;

public final class WorkspaceTypeMapper {

    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private WorkspaceTypeMapper(){
    }

    public static DuckDBColumnType toDuckDbType(ColMetaData meta, ValueFetcher fetcher, boolean forceVarchar) {
        if (forceVarchar || fetcher == null) {
            return DuckDBColumnType.VARCHAR;
        }
        if (meta != null && ColMetaData.JSON_DOCUMENT.equals(meta.getColumnType())) {
            return DuckDBColumnType.VARCHAR;
        }
        switch (fetcher.getType()) {
            case Boolean:
                return DuckDBColumnType.BOOLEAN;
            case Byte:
                return DuckDBColumnType.TINYINT;
            case Short:
                return DuckDBColumnType.SMALLINT;
            case Integer:
                return DuckDBColumnType.INTEGER;
            case Long:
                return DuckDBColumnType.BIGINT;
            case BigInteger:
                return DuckDBColumnType.HUGEINT;
            case Float:
                return DuckDBColumnType.FLOAT;
            case Double:
            case BigDecimal:
                return DuckDBColumnType.DOUBLE;
            case Date:
                return DuckDBColumnType.DATE;
            case DateTime:
                return DuckDBColumnType.TIMESTAMP;
            case DateTimeZ:
                return DuckDBColumnType.TIMESTAMP_WITH_TIME_ZONE;
            case String:
                return inferNumericFromMeta(meta);
            default:
                return DuckDBColumnType.VARCHAR;
        }
    }

    private static DuckDBColumnType inferNumericFromMeta(ColMetaData meta) {
        if (meta == null || meta.getJdbcType() == null) {
            return DuckDBColumnType.VARCHAR;
        }
        JDBCType jdbcType = meta.getJdbcType();
        if (jdbcType == JDBCType.DECIMAL || jdbcType == JDBCType.NUMERIC || jdbcType == JDBCType.DOUBLE || jdbcType == JDBCType.FLOAT || jdbcType == JDBCType.REAL) {
            return DuckDBColumnType.DOUBLE;
        }
        if (jdbcType == JDBCType.BIGINT) {
            return DuckDBColumnType.HUGEINT;
        }
        return DuckDBColumnType.VARCHAR;
    }

    public static Object readFromResultSet(ResultSet rs, String colName, ValueFetcher fetcher, ValueFetcherContext ctx) throws SQLException {
        if (fetcher == null) {
            return rs.getString(colName);
        }
        switch (fetcher.getType()) {
            case Boolean:
                return fetcher.getBoolean(colName, rs, ctx);
            case Byte:
                return fetcher.getByte(colName, rs, ctx);
            case Short:
                return fetcher.getShort(colName, rs, ctx);
            case Integer:
                return fetcher.getInteger(colName, rs, ctx);
            case Long:
                return fetcher.getLong(colName, rs, ctx);
            case BigInteger:
                return fetcher.getBigInteger(colName, rs, ctx);
            case BigDecimal:
                return fetcher.getBigDecimal(colName, rs, ctx);
            case Float:
                return fetcher.getFloat(colName, rs, ctx);
            case Double:
                return fetcher.getDouble(colName, rs, ctx);
            case Date:
                return fetcher.getDate(colName, rs, ctx);
            case Time:
                LocalTime lt = fetcher.getTime(colName, rs, ctx);
                return lt == null ? null : lt.toString();
            case TimeZ:
                OffsetTime ot = fetcher.getTimeZ(colName, rs, ctx);
                return ot == null ? null : ot.toString();
            case DateTime:
                return fetcher.getDateTime(colName, rs, ctx);
            case DateTimeZ:
                return fetcher.getDateTimeZ(colName, rs, ctx);
            case Bytes:
                byte[] bytes = fetcher.getBytes(colName, rs, ctx);
                return bytes == null ? null : HexadecimalUtils.bytes2hex(bytes);
            default:
                return fetcher.getString(colName, rs, ctx);
        }
    }

    public static String toDisplayString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return HexadecimalUtils.bytes2hex(bytes);
        }
        if (value instanceof LocalDateTime ldt) {
            return ISO_DATE_TIME.format(ldt);
        }
        return String.valueOf(value);
    }

    public static void writeValue(DuckDBWritableVector vector, long rowIdx, Object value, DuckDBColumnType type) {
        if (value == null) {
            vector.setNull(rowIdx);
            return;
        }

        switch (type) {
            case BOOLEAN:
                if (value instanceof Boolean b) {
                    vector.setBoolean(rowIdx, b);
                } else {
                    vector.setBoolean(rowIdx, Boolean.parseBoolean(String.valueOf(value)));
                }
                return;
            case TINYINT:
                vector.setByte(rowIdx, toNumber(value).byteValue());
                return;
            case SMALLINT:
                vector.setShort(rowIdx, toNumber(value).shortValue());
                return;
            case INTEGER:
                vector.setInt(rowIdx, toNumber(value).intValue());
                return;
            case BIGINT:
                vector.setLong(rowIdx, toNumber(value).longValue());
                return;
            case HUGEINT:
                if (value instanceof BigInteger bi) {
                    vector.setHugeInt(rowIdx, bi);
                } else {
                    vector.setHugeInt(rowIdx, new BigInteger(String.valueOf(value)));
                }
                return;
            case FLOAT:
                vector.setFloat(rowIdx, toNumber(value).floatValue());
                return;
            case DOUBLE:
                vector.setDouble(rowIdx, toNumber(value).doubleValue());
                return;
            case DATE:
                if (value instanceof LocalDate ld) {
                    vector.setDate(rowIdx, ld);
                } else if (value instanceof java.sql.Date sd) {
                    vector.setDate(rowIdx, sd);
                } else if (value instanceof Date d) {
                    vector.setDate(rowIdx, d);
                } else {
                    vector.setDate(rowIdx, LocalDate.parse(String.valueOf(value)));
                }
                return;
            case TIMESTAMP:
                if (value instanceof LocalDateTime ldt) {
                    vector.setTimestamp(rowIdx, ldt);
                } else if (value instanceof java.sql.Timestamp ts) {
                    vector.setTimestamp(rowIdx, ts);
                } else if (value instanceof Date d) {
                    vector.setTimestamp(rowIdx, d);
                } else {
                    vector.setTimestamp(rowIdx, LocalDateTime.parse(String.valueOf(value)));
                }
                return;
            case TIMESTAMP_WITH_TIME_ZONE:
                if (value instanceof OffsetDateTime odt) {
                    vector.setOffsetDateTime(rowIdx, odt);
                } else if (value instanceof ZonedDateTime zdt) {
                    vector.setOffsetDateTime(rowIdx, zdt.toOffsetDateTime());
                } else if (value instanceof Instant instant) {
                    vector.setOffsetDateTime(rowIdx, instant.atOffset(ZoneOffset.UTC));
                } else {
                    vector.setOffsetDateTime(rowIdx, OffsetDateTime.parse(String.valueOf(value)));
                }
                return;
            default:
                vector.setString(rowIdx, toDisplayString(value));
        }
    }

    private static Number toNumber(Object value) {
        if (value instanceof Number n) {
            return n;
        }
        return new BigDecimal(String.valueOf(value).trim());
    }
}
