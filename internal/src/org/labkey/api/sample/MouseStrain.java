/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.sample;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.util.ResultSetUtil;

/**
 * Bean Class for for MouseStrain.
 *
 * @author <a href="http://boss.bekk.no/boss/middlegen/"/>Middlegen</a>
 */
public class MouseStrain
{

    private int _mouseStrainId = 0;
    private java.lang.String _mouseStrain = null;
    private java.lang.String _characteristics = null;

    public MouseStrain()
    {
    }

    public int getMouseStrainId()
    {
        return _mouseStrainId;
    }

    public void setMouseStrainId(int mouseStrainId)
    {
        _mouseStrainId = mouseStrainId;
    }

    public java.lang.String getMouseStrain()
    {
        return _mouseStrain;
    }

    public void setMouseStrain(java.lang.String mouseStrain)
    {
        _mouseStrain = mouseStrain;
    }

    public java.lang.String getCharacteristics()
    {
        return _characteristics;
    }

    public void setCharacteristics(java.lang.String characteristics)
    {
        _characteristics = characteristics;
    }


}
