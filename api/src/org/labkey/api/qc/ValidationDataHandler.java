/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.qc;

import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.study.assay.ParticipantVisitResolver;

import java.util.Map;
import java.util.List;
import java.io.File;
import java.io.IOException;

/*
* User: Karl Lum
* Date: Dec 22, 2008
* Time: 12:11:55 PM
*/
public interface ValidationDataHandler
{
    public List<Map<String, Object>> loadFileData(Domain dataDomain, File dataFile)  throws IOException, ExperimentException;
}