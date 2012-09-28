/*
 * Copyright (c) 2008-2012 LabKey Corporation
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
package org.labkey.api.query;

import org.labkey.api.etl.DataIterator;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.security.User;
import org.labkey.api.data.Container;

import java.util.List;
import java.util.Map;
import java.sql.SQLException;
/*
 * User: Dave
 * Date: Jun 9, 2008
 * Time: 4:36:22 PM
 */

/**
 * This interface should be implemented by modules that expose queries
 * that can be updated by the HTTP-based APIs, or any other code that
 * uses maps to work with things that act like rows.
 * <p>
 * This interface has the basic CRUD operations, each taking a reference
 * to the current user, current container, and a
 * <code>Map&lt;String,Object&gt;</code> containing either the primary
 * key value(s) or all column values.
 * </p><p>
 * Implementations may wish to extend {@link AbstractBeanQueryUpdateService},
 * which uses BeanUtils to convert maps into hard-typed beans, and vice-versa.
 * This allows the implementation to work primarily with beans instead of maps.
 * </p>
 */
public interface QueryUpdateService
{
    /**
     * Returns the rows identified by the keys, existing in the container, as maps.
     * If the row is not found, it is omitted from the returned list.
     * @param user The current user.
     * @param container The container in which the data should exist.
     * @param keys A map of primary key values.
     * @return The row data as maps.
     * @throws InvalidKeyException Thrown if the key value(s) is(are) not valid.
     * @throws SQLException Thrown if there was an error communicating with the database.
     * @throws QueryUpdateServiceException Thrown for implementation-specific exceptions.
     */
    public List<Map<String,Object>> getRows(User user, Container container, List<Map<String,Object>> keys)
            throws InvalidKeyException, QueryUpdateServiceException, SQLException;

    /**
     * Inserts the given values as new rows into the source table of this query.
     * @param user The current user.
     * @param container The container in which the data should exist.
     * @param rows The row values as maps.
     * @return The row values after insert. If the rows have an automatically-assigned
     * primary key value(s), those should be added to the returned map. However, the
     * implementation should not completely refetch the row data. The caller will use
     * <code>getRows()</code> to refetch if that behavior is necessary.
     * @throws SQLException Thrown if there was an error communicating with the database.
     * @throws ValidationException Thrown if the data failed one of the validation checks
     * @throws QueryUpdateServiceException Thrown for implementation-specific exceptions.
     * @throws DuplicateKeyException Thrown if primary key values were supplied in the map
     */
    public List<Map<String,Object>> insertRows(User user, Container container, List<Map<String, Object>> rows,
            BatchValidationException errors, Map<String, Object> extraScriptContext)
        throws DuplicateKeyException, BatchValidationException, QueryUpdateServiceException, SQLException;

    /**
     * Inserts the given values as new rows into the source table of this query.  Same as insertRows() except for the use
     * of DataIterator.  importRows() may be implmented using insertRows() or vice versa.
     *
     * @param user The current user.
     * @param container The container in which the data should exist.
     * @param rows The row values provided using a DataIterator.
     * @return The row values after insert. If the rows have an automatically-assigned
     * primary key value(s), those should be added to the returned map. However, the
     * implementation should not completely refetch the row data. The caller will use
     * <code>getRows()</code> to refetch if that behavior is necessary.
     * @throws SQLException Thrown if there was an error communicating with the database.
     * @throws ValidationException Thrown if the data failed one of the validation checks
     * @throws QueryUpdateServiceException Thrown for implementation-specific exceptions.
     * @throws DuplicateKeyException Thrown if primary key values were supplied in the map
     */
    public int importRows(User user, Container container, DataIterator rows,
           BatchValidationException errors, Map<String, Object> extraScriptContext)
        throws SQLException;

    /**
     * Updates a set of rows in the source table for this query.
     * @param user The current user.
     * @param container The container in which the row should exist.
     * @param rows The new row values as maps.
     * @param oldKeys (Optional) A map containing old key values. If the keys are
     * not automatically assigned by the database, and if the row map contains
     * modified key values, the client may pass the old key values in this parameter.
     * If the key values cannot or did not change, pass null.
     * @return The row values after update. However, the implementation should not
     * completely refetch the row data for this returned map. The caller will use
     * the <code>getRow()</code> method to refetch if that behavior is necessary.
     * @throws InvalidKeyException Thrown if the keys (or old keys) are not valid.
     * @throws ValidationException Thrown if the data fails one of the validation checks
     * @throws QueryUpdateServiceException Thrown for implementation-specific exceptions.
     * @throws SQLException Thrown if there was an error communicating with the database.
     */
    public List<Map<String,Object>> updateRows(User user, Container container, List<Map<String, Object>> rows,
                                               List<Map<String, Object>> oldKeys, Map<String, Object> extraScriptContext)
            throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException;

    /**
     * Deletes a set of rows from the source table for this query.
     * @param user The current user.
     * @param container The container in which the row should exist.
     * @param keys The list of primary key values as maps.
     * @return The primary key values passed as the keys parameter.
     * @throws InvalidKeyException Thrown if the primary key values are not valid.
     * @throws ValidationException Thrown if the data fails one of the validation checks
     * @throws QueryUpdateServiceException Thrown for implementation-specific exceptions.
     * @throws SQLException Thrown if there was an error communicating with the database.
     */
    public List<Map<String,Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys, Map<String, Object> extraScriptContext)
            throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException;

    /**
     * If true, disables expensive optional activity for this updater.
     * @param bulkLoad whether to write audit log, and do per insert housekeeping
     */
    public void setBulkLoad(boolean bulkLoad);

    /**
     * implementations should use this to decide whether to do optional expensive operations upon updates.
     * @return
     */
    public boolean isBulkLoad();
}
