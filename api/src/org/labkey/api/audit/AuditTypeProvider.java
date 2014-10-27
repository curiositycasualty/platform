/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.api.audit;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

import java.util.Map;

/**
 * User: klum
 * Date: 7/8/13
 */
public interface AuditTypeProvider
{
    /**
     * The audit event name associated with this audit provider. Must be
     * unique within the system.
     */
    public String getEventName();
    public String getLabel();
    public String getDescription();

    /**
     * Perform any initialization of the provider at registration time such as
     * domain creation.
     * @param user User useed when saving the backing Domain.
     */
    public void initializeProvider(User user);

    public Domain getDomain();
    public TableInfo createTableInfo(UserSchema schema);

    /**
     * Conversion from legacy untyped event fields to new provider specific
     * fields
     */
    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event);

    public <K extends AuditTypeEvent> K convertEvent(AuditLogEvent event, @Nullable Map<String, Object> dataMap);

    public <K extends AuditTypeEvent> Class<K> getEventClass();

    /**
     * Mapping from old audit table names ("intKey1", "key1", and "Property/Foo" to the new column names.)
     */
    Map<FieldKey, String> legacyNameMap();

    public ActionURL getAuditUrl();

}
