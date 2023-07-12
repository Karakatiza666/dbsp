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

import org.dbsp.sqlCompiler.compiler.frontend.CalciteObject;
import org.dbsp.sqlCompiler.ir.expression.DBSPExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPVariablePath;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPLiteral;
import org.dbsp.sqlCompiler.ir.type.DBSPType;
import org.dbsp.sqlCompiler.ir.type.DBSPTypeCode;
import org.dbsp.util.IIndentStream;

public abstract class DBSPTypeBaseType extends DBSPType {
    protected DBSPTypeBaseType(CalciteObject node, DBSPTypeCode code, boolean mayBeNull) {
        super(node, code, mayBeNull);
    }

    public String shortName() {
        return this.code.shortName;
    }

    public String getRustString() {
        return this.code.rustName;
    }

    @Override
    public DBSPExpression caster(DBSPType to) {
        DBSPVariablePath var = new DBSPVariablePath("x", this);
        return var.cast(to).closure(var.asParameter());
    }

    /**
     * Default value for this type.
     */
    public abstract DBSPLiteral defaultValue();

    /**
     * The null value with this type.
     */
    public DBSPExpression nullValue() {
        return DBSPLiteral.none(this);
    }

    @Override
    public IIndentStream toString(IIndentStream builder) {
        return builder.append(this.shortName())
                .append(this.mayBeNull ? "?" : "");
    }
}
