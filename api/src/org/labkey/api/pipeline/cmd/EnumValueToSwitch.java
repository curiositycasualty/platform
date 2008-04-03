/*
 * Copyright (c) 2008 LabKey Software Foundation
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
package org.labkey.api.pipeline.cmd;

import java.util.Map;

/**
 * <code>EnumValueToSwitch</code>
*/
public class EnumValueToSwitch extends ValueToCommandArgs
{
    private String _default;
    private Map<String, String> _switches;

    public String getDefault()
    {
        return _default;
    }

    public void setDefault(String def)
    {
        _default = def;
    }

    public String getSwitchName(String value)
    {
        return _switches.get(value);
    }

    public Map<String, String> getSwitches()
    {
        return _switches;
    }

    public void setSwitches(Map<String, String> switches)
    {
        _switches = switches;
    }

    public String[] toArgs(String value)
    {
        if (value == null)
            value = getDefault();

        if (value != null)
            return getSwitchFormat().format(getSwitchName(value));

        return new String[0];
    }
}
