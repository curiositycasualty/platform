/*
 * Copyright (c) 2008-2010 LabKey Corporation
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
package org.labkey.study.query;

import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.sql.SQLException;
import java.util.*;

/*
* User: Dave
* Date: Jun 13, 2008
* Time: 4:15:51 PM
*/

/**
 * QueryUpdateService implementation for Study datasets.
 * <p>
 * Since datasets are of an unpredictable shape, this class just implements
 * the QueryUpdateService directly, working with <code>Map&lt;String,Object&gt;</code>
 * collections for the row data.
 */
public class DatasetUpdateService extends AbstractQueryUpdateService
{
    private final DataSetTable _table;

    public DatasetUpdateService(DataSetTable table)
    {
        super(table);
        _table = table;
    }

    public Map<String, Object> getRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        String lsid = keyFromMap(keys);
        return StudyService.get().getDatasetRow(user, container, _table.getDatasetDefinition().getDataSetId(), lsid);
    }

    public Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        List<String> errors = new ArrayList<String>();
        String newLsid = StudyService.get().insertDatasetRow(user, container, _table.getDatasetDefinition().getDataSetId(), row, errors);
        if(errors.size() > 0)
        {
            ValidationException e = new ValidationException();
            for(String err : errors)
                e.addError(new SimpleValidationError(err));
            throw e;
        }

        //update the lsid and return
        row.put("lsid", newLsid);
        return row;
    }

    @Override
    public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys)
            throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = super.updateRows(user, container, rows, oldKeys);

        Set<Object> changedLSIDs = new HashSet<Object>();
        // Keep track of whether LSIDs changed based on this update
        for (Map<String, Object> oldRowKeys : oldKeys)
        {
            changedLSIDs.add(oldRowKeys.get("lsid"));
        }
        for (Map<String, Object> updatedRows : result)
        {
            changedLSIDs.remove(updatedRows.get("lsid"));
        }

        resyncStudy(user, container, changedLSIDs.isEmpty());
        return result;
    }

    private void resyncStudy(User user, Container container, boolean lsidChanged)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        boolean recomputeCohorts = (!study.isManualCohortAssignment() &&
                PageFlowUtil.nullSafeEquals(_table.getDatasetDefinition().getDataSetId(), study.getParticipantCohortDataSetId()));

        // If this results in a change to cohort assignments, the participant ID, or the visit,
        // we need to recompute the participant-visit map:
        if (recomputeCohorts || lsidChanged)
        {
            DataSetDefinition dataset = _table.getDatasetDefinition();
            StudyManager.getInstance().recomputeStudyDataVisitDate(study, Collections.singletonList(dataset));
            StudyManager.getInstance().getVisitManager(study).updateParticipantVisits(user, Collections.singletonList(dataset));
        }
    }

    @Override
    public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = super.insertRows(user, container, rows);
        resyncStudy(user, container, true);
        return result;
    }

    public Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, Map<String, Object> oldKeys) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
    {
        List<String> errors = new ArrayList<String>();
        String lsid = null != oldKeys ? keyFromMap(oldKeys) : keyFromMap(row);
        String newLsid = StudyService.get().updateDatasetRow(user, container, _table.getDatasetDefinition().getDataSetId(), lsid, row, errors);
        if(errors.size() > 0)
        {
            ValidationException e = new ValidationException();
            for(String err : errors)
                e.addError(new SimpleValidationError(err));
            throw e;
        }

        //update the lsid and return
        row.put("lsid", newLsid);
        return row;
    }

    @Override
    public List<Map<String, Object>> deleteRows(User user, Container container, List<Map<String, Object>> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        List<Map<String, Object>> result = super.deleteRows(user, container, keys);
        resyncStudy(user, container, true);
        return result;
    }

    public Map<String, Object> deleteRow(User user, Container container, Map<String, Object> keys) throws InvalidKeyException, QueryUpdateServiceException, SQLException
    {
        StudyService.get().deleteDatasetRow(user, container, _table.getDatasetDefinition().getDataSetId(), keyFromMap(keys));
        return keys;
    }

    public String keyFromMap(Map<String, Object> map) throws InvalidKeyException
    {
        Object lsid = map.get("lsid");
        if(null == lsid && _table.getDatasetDefinition().getKeyManagementType() != DataSetDefinition.KeyManagementType.None)
        {
            Object id = map.get(_table.getDatasetDefinition().getKeyPropertyName());
            if (id == null)
            {
                id = map.get("Key");
            }
            if (id != null)
            {
                try
                {
                    String[] lsids = Table.executeArray(_table, _table.getColumn("LSID"), new SimpleFilter(_table.getDatasetDefinition().getKeyPropertyName(), id), null, String.class);
                    if (lsids.length == 1)
                    {
                        map.put("lsid", lsids[0]);
                        return lsids[0];
                    }
                    if (lsids.length > 1)
                    {
                        throw new IllegalStateException("More than one row matched for key '" + id + "' in column " +
                                _table.getDatasetDefinition().getKeyPropertyName() + " in dataset " +
                                _table.getDatasetDefinition().getName() + " in folder " +
                                _table.getDatasetDefinition().getContainer().getPath());
                    }
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }
            }
        }
        if (null == lsid)
            throw new InvalidKeyException("No value provided for 'lsid' key column!", map);
        return lsid.toString();
    }
}
