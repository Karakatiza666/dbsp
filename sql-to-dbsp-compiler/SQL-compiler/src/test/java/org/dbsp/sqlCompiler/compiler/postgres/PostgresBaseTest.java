package org.dbsp.sqlCompiler.compiler.postgres;

import org.apache.calcite.config.Lex;
import org.apache.calcite.util.TimeString;
import org.dbsp.sqlCompiler.circuit.DBSPCircuit;
import org.dbsp.sqlCompiler.compiler.BaseSQLTests;
import org.dbsp.sqlCompiler.compiler.CompilerOptions;
import org.dbsp.sqlCompiler.compiler.InputOutputPair;
import org.dbsp.sqlCompiler.compiler.backend.DBSPCompiler;
import org.dbsp.sqlCompiler.compiler.errors.UnimplementedException;
import org.dbsp.sqlCompiler.compiler.frontend.CalciteObject;
import org.dbsp.sqlCompiler.ir.expression.DBSPExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPTupleExpression;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPBoolLiteral;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPDateLiteral;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPDecimalLiteral;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPDoubleLiteral;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPFloatLiteral;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPI16Literal;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPI32Literal;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPI64Literal;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPI8Literal;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPIntervalMillisLiteral;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPIntervalMonthsLiteral;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPLiteral;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPStringLiteral;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPTimeLiteral;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPTimestampLiteral;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPVecLiteral;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPZSetLiteral;
import org.dbsp.sqlCompiler.ir.type.DBSPType;
import org.dbsp.sqlCompiler.ir.type.DBSPTypeTuple;
import org.dbsp.sqlCompiler.ir.type.DBSPTypeTupleBase;
import org.dbsp.sqlCompiler.ir.type.DBSPTypeVec;
import org.dbsp.sqlCompiler.ir.type.DBSPTypeZSet;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeBaseType;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeBool;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeDate;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeDecimal;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeDouble;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeFloat;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeInteger;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeMillisInterval;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeMonthsInterval;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeString;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeTime;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeTimestamp;
import org.dbsp.util.Linq;
import org.dbsp.util.Utilities;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class PostgresBaseTest extends BaseSQLTests {
    /**
     * Override this method to prepare the tables on
     * which the tests are built.
     */
    public void prepareData(DBSPCompiler compiler) {}

    public CompilerOptions getOptions(boolean optimize) {
        CompilerOptions options = new CompilerOptions();
        options.ioOptions.lexicalRules = Lex.ORACLE;
        options.optimizerOptions.throwOnError = true;
        options.optimizerOptions.optimizationLevel = optimize ? 2 : 0;
        options.optimizerOptions.generateInputForEveryTable = true;
        options.optimizerOptions.incrementalize = false;
        return options;
    }

    public DBSPCompiler testCompiler(boolean optimize) {
        CompilerOptions options = this.getOptions(optimize);
        return new DBSPCompiler(options);
    }

    public DBSPCompiler compileQuery(String query, boolean optimize) {
        DBSPCompiler compiler = this.testCompiler(optimize);
        this.prepareData(compiler);
        compiler.compileStatement(query);
        if (!compiler.options.optimizerOptions.throwOnError) {
            compiler.throwIfErrorsOccurred();
        }
        return compiler;
    }

    // Calcite is not very flexible regarding timestamp formats
    static final SimpleDateFormat[] TIMESTAMP_INPUT_FORMAT = {
            new SimpleDateFormat("EEE MMM d HH:mm:ss.SSS yyyy"),
            new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy G"),
            new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy")
    };
    static final SimpleDateFormat TIMESTAMP_OUTPUT_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");

    /**
     * Convert a timestamp from a format like Sat Feb 16 17:32:01 1996 to
     * a format like 1996-02-16 17:32:01
     */
    public static DBSPExpression convertTimestamp(@Nullable String timestamp, boolean mayBeNull) {
        if (timestamp == null)
            return DBSPLiteral.none(DBSPTypeTimestamp.NULLABLE_INSTANCE);
        for (SimpleDateFormat input: TIMESTAMP_INPUT_FORMAT) {
            String out;
            try {
                // Calcite problems: does not support negative years, or fractional seconds ending in 0
                Date zero = new SimpleDateFormat("yyyy-MM-dd").parse("0000-01-01");
                Date converted = input.parse(timestamp);
                out = TIMESTAMP_OUTPUT_FORMAT.format(converted);
                if (converted.before(zero))
                    out = "-" + out;
            } catch (ParseException ignored) {
                continue;
            }
            return new DBSPTimestampLiteral(out, mayBeNull);
        }
        throw new RuntimeException("Could not parse " + timestamp);
    }

    static final SimpleDateFormat DATE_INPUT_FORMAT = new SimpleDateFormat("MM-dd-yyyy");
    static final SimpleDateFormat DATE_OUTPUT_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    /**
     * Convert a date from the MM-DD-YYYY format (which is used in the Postgres output)
     * to a DBSPLiteral.
     */
    static DBSPExpression parseDate(@Nullable String date) {
        if (date == null || date.isEmpty() || date.equalsIgnoreCase("null"))
            return DBSPLiteral.none(DBSPTypeDate.NULLABLE_INSTANCE);
        try {
            Date converted = DATE_INPUT_FORMAT.parse(date);
            String out = DATE_OUTPUT_FORMAT.format(converted);
            return new DBSPDateLiteral(out, true);
        } catch (ParseException ex) {
            throw new RuntimeException("Could not parse " + date);
        }
    }

    static DBSPExpression parseTime(@Nullable String time) {
        if (time == null || time.isEmpty() || time.equalsIgnoreCase("null"))
            return DBSPLiteral.none(DBSPTypeTime.NULLABLE_INSTANCE);
        return new DBSPTimeLiteral(CalciteObject.EMPTY, DBSPTypeTime.NULLABLE_INSTANCE, new TimeString(time));
    }

    static final Pattern YEAR = Pattern.compile("^(\\d+) years?(.*)");
    static final Pattern MONTHS = Pattern.compile("^\\s*(\\d+) months?(.*)");

    static int longIntervalToMonths(String interval) {
        String orig = interval;

        int result = 0;
        if (interval.equals("0")) {
            interval = "";
        } else {
            Matcher m = YEAR.matcher(interval);
            if (m.matches()) {
                int months = Integer.parseInt(m.group(1));
                result += months * 12;
                interval = m.group(2);
            }

            m = MONTHS.matcher(interval);
            if (m.matches()) {
                int days = Integer.parseInt(m.group(1));
                result += days;
                interval = m.group(2);
            }

            m = AGO.matcher(interval);
            if (m.matches()) {
                interval = m.group(1);
                result = -result;
            }
        }
        //System.out.println(orig + "->" + result + ": " + interval);
        if (!interval.isEmpty())
            throw new RuntimeException("Could not parse interval " + orig);
        return result;
    }

    static final Pattern MINUS = Pattern.compile("^-(.*)");
    static final Pattern DAYS = Pattern.compile("^(\\d+) days?(.*)");
    static final Pattern HOURS = Pattern.compile("^\\s*(\\d+) hours?(.*)");
    static final Pattern MINUTES = Pattern.compile("\\s*(\\d+) mins?(.*)");
    static final Pattern SECONDS = Pattern.compile("\\s*(\\d+)([.](\\d+))? secs?(.*)");
    static final Pattern HMS = Pattern.compile("\\s*([0-9][0-9]:[0-9][0-9]:[0-9][0-9])(\\.[0-9]*)?(.*)");
    static final Pattern AGO = Pattern.compile("\\s*ago(.*)");

    static long shortIntervalToMilliseconds(String interval) {
        String orig = interval;
        boolean negate = false;

        long result = 0;
        if (interval.equals("0")) {
            interval = "";
        } else {
            Matcher m = MINUS.matcher(interval);
            if (m.matches()) {
                negate = true;
                interval = m.group(1);
            }

            m = DAYS.matcher(interval);
            if (m.matches()) {
                int d = Integer.parseInt(m.group(1));
                result += (long) d * 86_400_000;
                interval = m.group(2);
            }

            m = HMS.matcher(interval);
            if (m.matches()) {
                String timeString = m.group(1);
                if (m.group(2) != null)
                    timeString += m.group(2);
                TimeString time = new TimeString(timeString);
                result += time.getMillisOfDay();
                interval = m.group(3);
            } else {
                m = HOURS.matcher(interval);
                if (m.matches()) {
                    long h = Integer.parseInt(m.group(1));
                    result += h * 3600_000;
                    interval = m.group(2);
                }

                m = MINUTES.matcher(interval);
                if (m.matches()) {
                    long mm = Integer.parseInt(m.group(1));
                    result += mm * 60_000;
                    interval = m.group(2);
                }

                m = SECONDS.matcher(interval);
                if (m.matches()) {
                    long s = Integer.parseInt(m.group(1));
                    result += s * 1000;
                    interval = m.group(4);
                }
            }

            m = AGO.matcher(interval);
            if (m.matches()) {
                interval = m.group(1);
                negate = !negate;
            }

            if (negate)
                result = -result;
        }
        //System.out.println(orig + "->" + result + ": " + interval);
        if (!interval.isEmpty())
            throw new RuntimeException("Could not parse interval " + orig);
        return result;
    }

    DBSPExpression parseValue(DBSPType fieldType, String data) {
        String trimmed = data.trim();
        DBSPExpression result;
        if (!fieldType.is(DBSPTypeString.class) &&
                (trimmed.isEmpty() ||
                        trimmed.equalsIgnoreCase("null"))) {
            if (!fieldType.mayBeNull)
                throw new RuntimeException("Null value in non-nullable column " + fieldType);
            result = fieldType.to(DBSPTypeBaseType.class).nullValue();
        } else if (fieldType.is(DBSPTypeDouble.class)) {
            double value = Double.parseDouble(trimmed);
            result = new DBSPDoubleLiteral(value, fieldType.mayBeNull);
        } else if (fieldType.is(DBSPTypeFloat.class)) {
            float value = Float.parseFloat(trimmed);
            result = new DBSPFloatLiteral(value, fieldType.mayBeNull);
        } else if (fieldType.is(DBSPTypeDecimal.class)) {
            BigDecimal value = new BigDecimal(trimmed);
            result = new DBSPDecimalLiteral(fieldType, value);
        } else if (fieldType.is(DBSPTypeTimestamp.class)) {
            result = convertTimestamp(trimmed, fieldType.mayBeNull);
        } else if (fieldType.is(DBSPTypeDate.class)) {
            result = parseDate(trimmed);
        } else if (fieldType.is(DBSPTypeTime.class)) {
            result = parseTime(trimmed);
        } else if (fieldType.is(DBSPTypeInteger.class)) {
            DBSPTypeInteger intType = fieldType.to(DBSPTypeInteger.class);
            switch (intType.getWidth()) {
                case 8:
                    result = new DBSPI8Literal(Byte.parseByte(trimmed), fieldType.mayBeNull);
                    break;
                case 16:
                    result = new DBSPI16Literal(Short.parseShort(trimmed), fieldType.mayBeNull);
                    break;
                case 32:
                    result = new DBSPI32Literal(Integer.parseInt(trimmed), fieldType.mayBeNull);
                    break;
                case 64:
                    result = new DBSPI64Literal(Long.parseLong(trimmed), fieldType.mayBeNull);
                    break;
                default:
                    throw new UnimplementedException(intType);
            }
        } else if (fieldType.is(DBSPTypeMillisInterval.class)) {
            long value = shortIntervalToMilliseconds(trimmed);
            result = new DBSPIntervalMillisLiteral(value, fieldType.mayBeNull);
        } else if (fieldType.is(DBSPTypeMonthsInterval.class)) {
            int months = longIntervalToMonths(trimmed);
            result = new DBSPIntervalMonthsLiteral(months);
        } else if (fieldType.is(DBSPTypeString.class)) {
            // If there is no space in front of the string, we expect a NULL.
            // This is how we distinguish empty strings from nulls.
            if (!data.startsWith(" ")) {
                if (data.equals("NULL"))
                    result = DBSPLiteral.none(fieldType);
                else
                    throw new RuntimeException("Expected NULL or a space: " +
                            Utilities.singleQuote(data));
            } else {
                data = data.substring(1);
                result = new DBSPStringLiteral(CalciteObject.EMPTY, fieldType, data, StandardCharsets.UTF_8);
            }
        } else if (fieldType.is(DBSPTypeBool.class)) {
            boolean value = trimmed.equalsIgnoreCase("t") || trimmed.equalsIgnoreCase("true");
            result = new DBSPBoolLiteral(CalciteObject.EMPTY, fieldType, value);
        } else if (fieldType.is(DBSPTypeVec.class)) {
            DBSPTypeVec vec = fieldType.to(DBSPTypeVec.class);
            // TODO: this does not handle nested arrays
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}"))
                throw new UnimplementedException("Expected array constant to be bracketed: " + trimmed);
            trimmed = trimmed.substring(1, trimmed.length() - 1);
            String[] parts = trimmed.split(",");
            DBSPExpression[] fields = Linq.map(
                    parts, p -> this.parseValue(vec.getElementType(), p), DBSPExpression.class);
            result = new DBSPVecLiteral(fields);
        } else {
            throw new UnimplementedException(fieldType);
        }
        return result;
    }

    public DBSPTupleExpression parseRow(String line, DBSPTypeTupleBase rowType) {
        String[] columns;
        if (rowType.size() > 1) {
            columns = line.split("[|]");
        } else {
            // Do not split; allows handling 1-column outputs that contains |
            columns = new String[1];
            columns[0] = line;
        }
        if (columns.length != rowType.size())
            throw new RuntimeException("Row has " + columns.length +
                    " columns, but expected " + rowType.size() + ": " +
                    Utilities.singleQuote(line));
        DBSPExpression[] values = new DBSPExpression[columns.length];
        for (int i = 0; i < columns.length; i++) {
            DBSPType fieldType = rowType.getFieldType(i);
            values[i] = this.parseValue(fieldType, columns[i]);
        }
        return new DBSPTupleExpression(values);
    }

    public DBSPZSetLiteral.Contents parseTable(String table, DBSPType outputType) {
        DBSPTypeZSet zset = outputType.to(DBSPTypeZSet.class);
        DBSPZSetLiteral.Contents result = DBSPZSetLiteral.Contents.emptyWithElementType(zset.elementType);
        DBSPTypeTuple tuple = zset.elementType.to(DBSPTypeTuple.class);

        // We parse tables in two formats:
        // Postgres
        // t | t | f
        //---+---+---
        // t | t | f
        // and MySQL
        // +-----------+----------+----------+----------+
        // | JOB       | 10_COUNT | 50_COUNT | 20_COUNT |
        // +-----------+----------+----------+----------+
        // | ANALYST   |        0 |        0 |        2 |
        // | CLERK     |        1 |        0 |        2 |
        // | MANAGER   |        1 |        0 |        1 |
        // | PRESIDENT |        1 |        0 |        0 |
        // | SALESMAN  |        0 |        0 |        0 |
        // +-----------+----------+----------+----------+
        boolean mysqlStyle = false;

        String[] lines = table.split("\n", -1);
        boolean inHeader = true;
        int horizontalLines = 0;
        for (String line: lines) {
            if (inHeader) {
                if (line.isEmpty())
                    continue;
                if (line.startsWith("+---"))
                    mysqlStyle = true;
            }
            if (line.contains("---")) {
                horizontalLines++;
                if (mysqlStyle) {
                    if (horizontalLines == 2)
                        inHeader = false;
                } else {
                    inHeader = false;
                }
                continue;
            }
            if (horizontalLines == 3)
                // After table.
                continue;
            if (inHeader)
                continue;
            if (mysqlStyle && line.startsWith("|") && line.endsWith("|"))
                line = line.substring(1, line.length() - 2);
            DBSPExpression row = this.parseRow(line, tuple);
            result.add(row);
        }
        if (inHeader)
            throw new RuntimeException("Could not find end of header for table");
        return result;
    }

    DBSPZSetLiteral.Contents[] getPreparedInputs(DBSPCompiler compiler) {
        DBSPZSetLiteral.Contents[] inputs = new DBSPZSetLiteral.Contents[
                compiler.getTableContents().tablesCreated.size()];
        int index = 0;
        for (String table: compiler.getTableContents().tablesCreated) {
            DBSPZSetLiteral.Contents data = compiler.getTableContents().getTableContents(table.toUpperCase());
            inputs[index++] = data;
        }
        return inputs;
    }

    void compare(String query, DBSPZSetLiteral.Contents expected, boolean optimize) {
        DBSPCompiler compiler = this.testCompiler(optimize);
        this.prepareData(compiler);
        compiler.compileStatement("CREATE VIEW VV AS " + query);
        if (!compiler.options.optimizerOptions.throwOnError)
            compiler.throwIfErrorsOccurred();
        compiler.optimize();
        DBSPCircuit circuit = getCircuit(compiler);
        InputOutputPair streams = new InputOutputPair(
                this.getPreparedInputs(compiler),
                new DBSPZSetLiteral.Contents[] { expected }
        );
        this.addRustTestCase(query, compiler, circuit, streams);
    }


    void compare(String query, String expected, boolean optimize) {
        DBSPCompiler compiler = this.testCompiler(optimize);
        this.prepareData(compiler);
        compiler.compileStatement("CREATE VIEW VV AS " + query);
        if (!compiler.options.optimizerOptions.throwOnError)
            compiler.throwIfErrorsOccurred();
        compiler.optimize();
        DBSPCircuit circuit = getCircuit(compiler);
        DBSPType outputType = circuit.getOutputType(0);
        DBSPZSetLiteral.Contents result = this.parseTable(expected, outputType);
        InputOutputPair streams = new InputOutputPair(
                this.getPreparedInputs(compiler),
                new DBSPZSetLiteral.Contents[] { result }
        );
        this.addRustTestCase(query, compiler, circuit, streams);
    }

    /**
     * Test a query followed by the expected output.
     * The query ends at the semicolon.
     * Runs two test cases, one with optimizations and one without.
     * This makes sure that constant queries still exercise the runtime.
     */
    public void q(String queryAndOutput) {
        int semicolon = queryAndOutput.indexOf(';');
        if (semicolon < 0)
            throw new RuntimeException("Could not parse query and output");
        String query = queryAndOutput.substring(0, semicolon);
        String expected = queryAndOutput.substring(semicolon + 1);
        this.compare(query, expected, true);
        this.compare(query, expected, false);
    }

    /**
     * Test a sequence of queries, each followed by its expected output.
     * Two queries are separated by a whitespace line.
     * Here is an example legal input:
     * SELECT f.* FROM FLOAT4_TBL f WHERE f.f1 = '1004.3';
     *    f1
     * --------
     *  1004.3
     * (1 row)
     *
     * SELECT f.* FROM FLOAT4_TBL f WHERE '1004.3' > f.f1;
     *       f1
     * ---------------
     *              0
     *         -34.84
     *  1.2345679e-20
     * (3 rows)
     */
    public void qs(String queriesWithOutputs) {
        String[] parts = queriesWithOutputs.split("\n\n");
        // From each part drop the last line (N rows) *and* its last newline.
        Pattern regex = Pattern.compile("^(.*)\\n\\(\\d+ rows?\\)$", Pattern.DOTALL);
        for (String part: parts) {
            Matcher regexMatcher = regex.matcher(part);
            if (regexMatcher.find()) {
                String result = regexMatcher.group(1);
                this.q(result);
            } else {
                throw new RuntimeException("Could not understand test: " + part);
            }
        }
    }
}
