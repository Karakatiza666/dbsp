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

package org.dbsp.sqlCompiler.ir.type.primitive;

import org.dbsp.sqlCompiler.compiler.errors.InternalCompilerError;
import org.dbsp.sqlCompiler.compiler.frontend.CalciteObject;
import org.dbsp.sqlCompiler.compiler.visitors.inner.InnerVisitor;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPI32Literal;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPLiteral;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPI64Literal;
import org.dbsp.sqlCompiler.ir.type.DBSPType;
import org.dbsp.sqlCompiler.ir.type.DBSPTypeCode;
import org.dbsp.sqlCompiler.ir.type.IsNumericType;
import org.dbsp.sqlCompiler.compiler.errors.UnsupportedException;

import java.util.Objects;

import static org.dbsp.sqlCompiler.ir.type.DBSPTypeCode.*;

public class DBSPTypeInteger extends DBSPTypeBaseType
        implements IsNumericType {
    private final int width;
    public final boolean signed;

    public static final DBSPTypeInteger SIGNED_8 =
            new DBSPTypeInteger(CalciteObject.EMPTY, INT8,  8, true,false);
    public static final DBSPTypeInteger SIGNED_16 =
            new DBSPTypeInteger(CalciteObject.EMPTY, INT16, 16, true,false);
    public static final DBSPTypeInteger SIGNED_32 =
            new DBSPTypeInteger(CalciteObject.EMPTY, INT32, 32, true,false);
    public static final DBSPTypeInteger SIGNED_64 =
            new DBSPTypeInteger(CalciteObject.EMPTY, INT64,64, true,false);
    public static final DBSPTypeInteger UNSIGNED_32 =
            new DBSPTypeInteger(CalciteObject.EMPTY, UINT32,32, false,false);
    public static final DBSPTypeInteger UNSIGNED_64 =
            new DBSPTypeInteger(CalciteObject.EMPTY, UINT64,64, false,false);
    public static final DBSPTypeInteger NULLABLE_SIGNED_16 =
            new DBSPTypeInteger(CalciteObject.EMPTY, INT16,16, true,true);
    public static final DBSPTypeInteger NULLABLE_SIGNED_32 =
            new DBSPTypeInteger(CalciteObject.EMPTY, INT32,32, true,true);
    public static final DBSPTypeInteger NULLABLE_SIGNED_64 =
            new DBSPTypeInteger(CalciteObject.EMPTY, INT64,64, true,true);

    public DBSPTypeCode getCode(int width, boolean signed) {
        if (signed) {
            switch (width) {
                case 8: return INT8;
                case 16: return INT16;
                case 32: return INT32;
                case 64: return INT64;
            }
        } else {
            switch (width) {
                case 16: return UINT16;
                case 32: return UINT32;
                case 64: return UINT64;
            }
        }
        throw new InternalCompilerError("Unexpected width " + width);
    }

    public DBSPTypeInteger(CalciteObject node, DBSPTypeCode code, int width, boolean signed, boolean mayBeNull) {
        super(node, code, mayBeNull);
        this.width = width;
        this.signed = signed;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.width, this.signed);
    }

    @Override
    public DBSPLiteral getZero() {
        if (this.width <= 32) {
            return new DBSPI32Literal(0, this.mayBeNull);
        } else {
            return new DBSPI64Literal(0L, this.mayBeNull);
        }
    }

    @Override
    public DBSPLiteral getOne() {
        if (this.width <= 32) {
            return new DBSPI32Literal(1, this.mayBeNull);
        } else {
            return new DBSPI64Literal(1L, this.mayBeNull);
        }
    }

    @Override
    public DBSPLiteral getMaxValue() {
        switch (this.width) {
            case 16:
                return new DBSPI32Literal((int)Short.MAX_VALUE, this.mayBeNull);
            case 32:
                return new DBSPI32Literal(Integer.MAX_VALUE, this.mayBeNull);
            case 64:
                return new DBSPI64Literal(Long.MAX_VALUE, this.mayBeNull);
            default:
                throw new UnsupportedException(this.getNode());
        }
    }

    @Override
    public DBSPLiteral getMinValue() {
        switch (this.width) {
            case 16:
                return new DBSPI32Literal((int)Short.MIN_VALUE, this.mayBeNull);
            case 32:
                return new DBSPI32Literal(Integer.MIN_VALUE, this.mayBeNull);
            case 64:
                return new DBSPI64Literal(Long.MIN_VALUE, this.mayBeNull);
            default:
                throw new UnsupportedException(this.getNode());
        }
    }

    @Override
    public DBSPType setMayBeNull(boolean mayBeNull) {
        if (mayBeNull == this.mayBeNull)
            return this;
        return new DBSPTypeInteger(this.getNode(), this.code, this.width, this.signed, mayBeNull);
    }

    @Override
    public DBSPLiteral defaultValue() {
        return this.getZero();
    }

    public int getWidth() {
        return this.width;
    }

    @Override
    public boolean sameType(DBSPType type) {
        if (!super.sameNullability(type))
            return false;
        if (!type.is(DBSPTypeInteger.class))
            return false;
        DBSPTypeInteger other = type.to(DBSPTypeInteger.class);
        return this.width == other.width && this.signed == other.signed;
    }

    @Override
    public void accept(InnerVisitor visitor) {
        if (visitor.preorder(this).stop()) return;
        visitor.push(this);
        visitor.pop(this);
        visitor.postorder(this);
    }
}
