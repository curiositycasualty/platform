/*
 * Copyright (c) 2006-2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.query.sql;

import org.labkey.api.data.JdbcType;

import java.sql.Types;

final public class QOperator extends QExpr
{
    Operator _op;

    public void appendSql(SqlBuilder builder)
    {
        _op.appendSql(builder, children());
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.pushPrefix(_op.getPrefix());
        for (QNode n : children())
        {
			QExpr child = (QExpr)n;
            boolean fParen = needsParentheses(child);
            if (fParen)
            {
                builder.pushPrefix("(");
            }
            child.appendSource(builder);
            if (fParen)
            {
                builder.popPrefix(")");
            }
            builder.nextPrefix(_op.getOperator());
        }
        builder.popPrefix();
    }

    public QOperator(Operator op)
    {
        _op = op;
    }

    public Operator getOperator()
    {
        return _op;
    }

    private boolean needsParentheses(QExpr child)
    {
        return _op.needsParentheses(child, child == getFirstChild());
    }

    public String getValueString()
    {
        StringBuilder ret = new StringBuilder(_op.getPrefix());
        boolean first = false;
        for (QNode n : children())
        {
			QExpr child = (QExpr)n;
            String strChild = child.getValueString();
            if (strChild == null)
            {
                return null;
            }
            if (!first)
            {
                ret.append(_op.getOperator());
            }
            first = false;
            boolean fParen = needsParentheses(child);
            if (fParen)
            {
                ret.append("(");
            }
            ret.append(strChild);
            if (fParen)
            {
                ret.append(")");
            }
        }
        return ret.toString();
    }

    @Override
    public JdbcType getSqlType()
    {
        if (_op.getResultType() == Operator.ResultType.bool)
        {
            return JdbcType.BOOLEAN;
        }
        if (_op.getResultType() == Operator.ResultType.string)
        {
            return JdbcType.VARCHAR;
        }
        if (_op.getResultType() == Operator.ResultType.arg)
        {
            return getChildrenSqlType();
        }

        return super.getSqlType();
    }
}
