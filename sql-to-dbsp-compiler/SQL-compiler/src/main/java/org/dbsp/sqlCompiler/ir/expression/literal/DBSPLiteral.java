/*
 * Copyright 2022 VMware, Inc.
 * SPDX-License-Identifier: MIT
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.dbsp.sqlCompiler.ir.expression.literal;

import org.dbsp.sqlCompiler.compiler.errors.InternalCompilerError;
import org.dbsp.sqlCompiler.compiler.frontend.CalciteObject;
import org.dbsp.sqlCompiler.ir.IDBSPNode;
import org.dbsp.sqlCompiler.compiler.visitors.inner.InnerVisitor;
import org.dbsp.sqlCompiler.ir.expression.DBSPExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPTupleExpression;
import org.dbsp.sqlCompiler.ir.type.DBSPType;
import org.dbsp.sqlCompiler.ir.type.DBSPTypeAny;
import org.dbsp.sqlCompiler.ir.type.DBSPTypeTuple;
import org.dbsp.sqlCompiler.ir.type.DBSPTypeVec;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeBool;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeDate;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeDecimal;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeDouble;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeFloat;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeGeoPoint;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeInteger;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeMillisInterval;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeMonthsInterval;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeNull;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeString;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeTime;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeTimestamp;
import org.dbsp.sqlCompiler.compiler.errors.UnimplementedException;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * This is not just a base class, it also can be used to represent NULL
 * literals of any type.  Maybe that's a bad idea.
 */
public abstract class DBSPLiteral extends DBSPExpression {
    public final boolean isNull;

    protected DBSPLiteral(CalciteObject node, DBSPType type, boolean isNull) {
        super(node, type);
        this.isNull = isNull;
        if (this.isNull && !type.mayBeNull && !type.is(DBSPTypeAny.class))
            throw new InternalCompilerError("Type " + type + " cannot represent null", this);
    }

    /**
     * Represents a "null" value of the specified type.
     */
    public static DBSPExpression none(DBSPType type) {
        if (type.is(DBSPTypeInteger.class)) {
            DBSPTypeInteger it = type.to(DBSPTypeInteger.class);
            if (!it.signed)
                throw new UnimplementedException("Null of type ", type);
            switch (it.getWidth()) {
                case 8:
                    return new DBSPI8Literal();
                case 16:
                    return new DBSPI16Literal();
                case 32:
                    return new DBSPI32Literal();
                case 64:
                    return new DBSPI64Literal();
            }
        } else if (type.is(DBSPTypeBool.class)) {
            return new DBSPBoolLiteral();
        } else if (type.is(DBSPTypeDate.class)) {
            return new DBSPDateLiteral();
        } else if (type.is(DBSPTypeDecimal.class)) {
            return new DBSPDecimalLiteral(type.getNode(), type, null);
        } else if (type.is(DBSPTypeDouble.class)) {
            return new DBSPDoubleLiteral();
        } else if (type.is(DBSPTypeFloat.class)) {
            return new DBSPFloatLiteral();
        } else if (type.is(DBSPTypeGeoPoint.class)) {
            return new DBSPGeoPointLiteral();
        } else if (type.is(DBSPTypeMillisInterval.class)) {
            return new DBSPIntervalMillisLiteral();
        } else if (type.is(DBSPTypeMonthsInterval.class)) {
            return new DBSPIntervalMonthsLiteral();
        } else if (type.is(DBSPTypeString.class)) {
            return new DBSPStringLiteral();
        } else if (type.is(DBSPTypeTime.class)) {
            return new DBSPTimeLiteral();
        } else if (type.is(DBSPTypeVec.class)) {
            return new DBSPVecLiteral(type, true);
        } else if (type.is(DBSPTypeTuple.class)) {
            return new DBSPTupleExpression(type.to(DBSPTypeTuple.class));
        } else if (type.is(DBSPTypeNull.class)) {
            return DBSPNullLiteral.INSTANCE;
        } else if (type.is(DBSPTypeTimestamp.class)) {
            return new DBSPTimestampLiteral();
        }
        throw new UnimplementedException(type);
    }

    public String wrapSome(String value) {
        if (this.getType().mayBeNull)
            return "Some(" + value + ")";
        return value;
    }

    /**
     * True if this and the other literal have the same type and value.
     */
    public abstract boolean sameValue(@Nullable DBSPLiteral other);

    public boolean mayBeNull() {
        return this.getType().mayBeNull;
    }

    @Override
    public void accept(InnerVisitor visitor) {
        if (visitor.preorder(this).stop()) return;
        visitor.push(this);
        visitor.pop(this);
        visitor.postorder(this);
    }

    boolean hasSameType(DBSPLiteral other) {
        return this.getType().sameType(other.getType());
    }

    /**
     * The same literal but with the specified nullability.
     */
    public abstract DBSPLiteral getWithNullable(boolean mayBeNull);

    @Nullable
    public <T> T checkIfNull(@Nullable T value, boolean mayBeNull) {
        if (!mayBeNull)
            return Objects.requireNonNull(value);
        return value;
    }

    @Override
    public boolean sameFields(IDBSPNode other) {
        DBSPLiteral o = other.as(DBSPLiteral.class);
        if (o == null)
            return false;
        return this.sameValue(o) && this.hasSameType(o);
    }
}
