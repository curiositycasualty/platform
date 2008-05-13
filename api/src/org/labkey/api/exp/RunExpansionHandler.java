/*
 * Copyright (c) 2005-2008 LabKey Corporation
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

package org.labkey.api.exp;

import org.fhcrc.cpas.exp.xml.ProtocolApplicationBaseType;
import org.labkey.api.exp.api.ExpRun;

/**
 * User: jeckels
 * Date: Dec 7, 2005
 */
public interface RunExpansionHandler extends Handler<String>
{
    public void protocolApplicationExpanded(ProtocolApplicationBaseType xbProtApp, ExpRun run);
}
