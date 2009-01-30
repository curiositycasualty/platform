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
package org.labkey.api.view.template;

import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.settings.LookAndFeelProperties;
import org.apache.commons.collections.MultiMap;

import java.util.List;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/*
* User: markigra
* Date: Jan 4, 2009
* Time: 9:44:28 AM
*/
public class MenuBarView extends JspView<List<Portal.WebPart>>
{
    public MenuBarView(List<Portal.WebPart> menus)
    {
        super(MenuBarView.class,  "menuBar.jsp", menus);
    }

    public MenuBarView(Container container)
    {
        super(MenuBarView.class,  "menuBar.jsp", null);
        Container project = container.getProject();
        //Probably not right for site-level admin pages...
        if (null == project)
            project = ContainerManager.getHomeContainer();

        LookAndFeelProperties laf = LookAndFeelProperties.getInstance(project);
        if (laf.isMenuUIEnabled())
        {
        Portal.WebPart[] allParts = Portal.getParts(project.getId());
        MultiMap locationMap = Portal.getPartsByLocation(allParts);
        List<Portal.WebPart> menuParts = (List<Portal.WebPart>) locationMap.get("menubar");
        if (null == menuParts)
            menuParts = Collections.EMPTY_LIST;

        setModelBean(menuParts);
        }
        else
            setModelBean(Collections.EMPTY_LIST);
    }
}
