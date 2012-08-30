/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.api.data;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * User: adam
 * Date: 8/7/12
 * Time: 9:37 PM
 */
public class ParameterMarkerInClauseGenerator implements InClauseGenerator
{
    @Override
    public SQLFragment appendInClauseSql(SQLFragment sql, @NotNull Object[] params)
    {
        sql.append("IN (");
        String questionMarks = StringUtils.repeat("?, ", params.length);
        sql.append(questionMarks.substring(0, questionMarks.length() - 2));
        sql.append(")");

        sql.addAll(params);

        return sql;
    }
}
