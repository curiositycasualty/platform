/*
 * Copyright (c) 2010 LabKey Corporation
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

package org.labkey.api.data.dialect;

import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.VersionNumber;

public abstract class AbstractDialectRetrievalTestCase extends Assert
{
    @Test
    public abstract void testDialectRetrieval();

    protected void good(String databaseName, double beginVersion, double endVersion, String jdbcDriverVersion, Class<? extends SqlDialect> expectedDialectClass)
    {
        testRange(databaseName, beginVersion, endVersion, jdbcDriverVersion, expectedDialectClass, null);
    }

    protected void badProductName(String databaseName, double beginVersion, double endVersion, String jdbcDriverVersion)
    {
        testRange(databaseName, beginVersion, endVersion, jdbcDriverVersion, null, SqlDialectNotSupportedException.class);
    }

    protected void badVersion(String databaseName, double beginVersion, double endVersion, String jdbcDriverVersion)
    {
        testRange(databaseName, beginVersion, endVersion, jdbcDriverVersion, null, DatabaseNotSupportedException.class);
    }

    private void testRange(String databaseName, double beginVersion, double endVersion, String jdbcDriverVersion, @Nullable Class<? extends SqlDialect> expectedDialectClass, @Nullable Class<? extends ConfigurationException> expectedExceptionClass)
    {
        int begin = (int)Math.round(beginVersion * 10);
        int end = (int)Math.round(endVersion * 10);

        for (int i = begin; i < end; i++)
        {
            int majorVersion = i / 10;
            int minorVersion = i % 10;

            String description = databaseName + " version " + majorVersion + "." + minorVersion;

            try
            {
                // If either major or minor is negative then use int constructor... otherwise, test string parsing
                VersionNumber version = (majorVersion < 0 || minorVersion < 0 ? new VersionNumber(majorVersion, minorVersion) : new VersionNumber(majorVersion + "." + minorVersion));
                SqlDialect dialect = SqlDialectManager.getFromProductName(databaseName, version, jdbcDriverVersion, false);
                assertNotNull(description + " returned " + dialect.getClass().getSimpleName() + "; expected failure", expectedDialectClass);
                assertEquals(description + " returned " + dialect.getClass().getSimpleName() + "; expected " + expectedDialectClass.getSimpleName(), dialect.getClass(), expectedDialectClass);
            }
            catch (Exception e)
            {
                assertTrue(description + " failed; expected success", null == expectedDialectClass);
                assertEquals(description + " resulted in a " + e.getClass().getSimpleName() + "; expected " + expectedExceptionClass, e.getClass(), expectedExceptionClass);
            }
        }
    }
}
