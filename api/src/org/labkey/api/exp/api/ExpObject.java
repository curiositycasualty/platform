/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

package org.labkey.api.exp.api;

import org.labkey.api.data.Container;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;

import java.util.Date;
import java.util.Map;

public interface ExpObject extends Identifiable, Comparable<ExpObject>
{
    int getRowId();
    void setLSID(String lsid);
    void setLSID(Lsid lsid);
    String getLSIDNamespacePrefix();
    void setName(String name);
    URLHelper detailsURL();
    Container getContainer();
    void setContainer(Container container);

    void setProperty(User user, PropertyDescriptor pd, Object value) throws ValidationException;
    Object getProperty(PropertyDescriptor pd);
    Object getProperty(DomainProperty prop);

    /** Stored in ontology manager */
    String getComment();
    /** Stored in ontology manager */
    void setComment(User user, String comment) throws ValidationException;
    
    String urlFlag(boolean flagged);

    User getCreatedBy();
    Date getCreated();
    User getModifiedBy();
    Date getModified();

    void save(User user);
    void delete(User user);

    /**
     * @return Map from PropertyURI to ObjectProperty
     */
    Map<String, ObjectProperty> getObjectProperties();
}
