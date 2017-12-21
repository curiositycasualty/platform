/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

package org.labkey.study.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.DataLoaderService;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.Filter;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.StudySchema;
import org.labkey.study.importer.StudyImportContext;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.QCState;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class DatasetImportRunnable implements Runnable
{
    private static final Logger LOG = PipelineJob.getJobLogger(DatasetImportRunnable.class);

    protected AbstractDatasetImportTask.Action _action = null;
    protected boolean _deleteAfterImport = false;
    protected Date _replaceCutoff = null;
    protected String visitDatePropertyURI = null;
    protected String visitDatePropertyName = null;

    protected final DatasetDefinition _datasetDefinition;
    protected final PipelineJob _job;
    protected final StudyImpl _study;
    @Nullable protected final StudyImportContext _studyImportContext;
    protected final Map<String, String> _columnMap = new DatasetFileReader.OneToOneStringMap();

    protected VirtualFile _root;
    protected String _tsvName;

    DatasetImportRunnable(PipelineJob job, StudyImpl study, DatasetDefinition ds, VirtualFile root, String tsv,
                          AbstractDatasetImportTask.Action action, boolean deleteAfterImport, Date defaultReplaceCutoff,
                          Map<String, String> columnMap, @Nullable StudyImportContext studyImportContext)
    {
        _job = job;
        _study = study;
        _datasetDefinition = ds;
        _action = action;
        _deleteAfterImport = deleteAfterImport;
        _replaceCutoff = defaultReplaceCutoff;
        _columnMap.putAll(columnMap);

        _root = root;
        _tsvName = tsv;
        _studyImportContext = studyImportContext;
    }

    public String validate()
    {
        List<String> errors = new ArrayList<>(5);
        validate(errors);
        return errors.isEmpty() ? null : errors.get(0);
    }

    public void validate(List<String> errors)
    {
        if (_action == null)
            errors.add("No action specified");

        if (_datasetDefinition == null)
            errors.add("Dataset not defined");
        else if (_datasetDefinition.getTypeURI() == null)
            errors.add("Dataset " + (null != _datasetDefinition.getName() ? _datasetDefinition.getName() + ": " : "") + "type is not defined");
        else if (null == _datasetDefinition.getStorageTableInfo())
            errors.add("No database table found for dataset " + _datasetDefinition.getName());

        if (_action == AbstractDatasetImportTask.Action.DELETE)
            return;

        if (null == _tsvName)
            errors.add("No file specified");
        else if (!_root.list().contains(_tsvName))
            errors.add("File does not exist: " + _tsvName);
    }


    public void run()
    {
        String name = getDatasetDefinition().getName();
        CPUTimer cpuDelete = new CPUTimer(name + ": delete");
        CPUTimer cpuImport = new CPUTimer(name + ": import");
        CPUTimer cpuCommit = new CPUTimer(name + ": commit");

        DbSchema schema  = StudySchema.getInstance().getSchema();
        DbScope scope = schema.getScope();
        QCState defaultQCState = _study.getDefaultPipelineQCState() != null ?
                StudyManager.getInstance().getQCStateForRowId(_job.getContainer(), _study.getDefaultPipelineQCState().intValue()) : null;

        List<String> errors = new ArrayList<>();
        validate(errors);

        if (!errors.isEmpty())
        {
            for (String e : errors)
                _job.error(_tsvName + " -- " + e);
            return;
        }

        DataLoader loader = null;

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            final String visitDatePropertyURI = getVisitDateURI(_job.getUser());
            boolean useCutoff =
                    _action == AbstractDatasetImportTask.Action.REPLACE &&
                    visitDatePropertyURI != null &&
                    _replaceCutoff != null;

            if (_action == AbstractDatasetImportTask.Action.REPLACE || _action == AbstractDatasetImportTask.Action.DELETE)
            {
                assert cpuDelete.start();
                _job.info(_datasetDefinition.getLabel() + ": Starting delete" + (useCutoff ? " of rows newer than " + _replaceCutoff : ""));
                int rows = StudyManager.getInstance().purgeDataset(_datasetDefinition, useCutoff ? _replaceCutoff : null, _job.getUser());
                _job.info(_datasetDefinition.getLabel() + ": Deleted " + rows + " rows");
                assert cpuDelete.stop();
            }

            if (_action == AbstractDatasetImportTask.Action.APPEND || _action == AbstractDatasetImportTask.Action.REPLACE)
            {
                final Integer[] skippedRowCount = new Integer[] { 0 };

                try (InputStream is = _root.getInputStream(_tsvName))
                {
                    loader = DataLoaderService.get().createLoader(_tsvName, null, is, true, _job.getContainer(), TabLoader.TSV_FILE_TYPE);
                    if (useCutoff && loader instanceof TabLoader)
                    {
                        // UNDONE: shouldn't be tied to TabLoader
                        ((TabLoader) loader).setMapFilter(new Filter<Map<String, Object>>()
                        {
                            public boolean accept(Map<String, Object> row)
                            {
                                Object o = row.get(visitDatePropertyURI);

                                // Allow rows with no Date or those that have failed conversion (e.g., value is a StudyManager.CONVERSION_ERROR)
                                if (!(o instanceof Date))
                                    return true;

                                // Allow rows after the cutoff date.
                                if (((Date) o).compareTo(_replaceCutoff) > 0)
                                    return true;

                                skippedRowCount[0]++;
                                return false;
                            }
                        });
                    }

                    assert cpuImport.start();
                    _job.info(_datasetDefinition.getLabel() + ": Starting import from " + _tsvName);
                    List<String> imported = StudyManager.getInstance().importDatasetData(
                            _job.getUser(),
                            _datasetDefinition,
                            loader,
                            _columnMap,
                            errors,
                            DatasetDefinition.CheckForDuplicates.sourceOnly,
                            //Set to TRUE if MERGEing
                            defaultQCState,
                            _studyImportContext,
                            _job.getLogger()
                    );
                    if (errors.size() == 0)
                    {
                        // optional check if new visits exist before committing, visit based timepoint studies only
                        boolean shouldCommit = true;
                        if (_studyImportContext.isFailForUndefinedVisits() && _study.getTimepointType() == TimepointType.VISIT)
                        {
                            List<Double> undefinedSequenceNums = StudyManager.getInstance().getUndefinedSequenceNumsForDataset(_datasetDefinition.getContainer(), _datasetDefinition.getDatasetId());
                            if (!undefinedSequenceNums.isEmpty())
                            {
                                Collections.sort(undefinedSequenceNums);
                                _job.error("The following undefined visits exist in the dataset data: " + StringUtils.join(undefinedSequenceNums, ", "));
                                shouldCommit = false;
                            }
                        }

                        if (shouldCommit)
                        {
                            assert cpuCommit.start();
                            transaction.commit();
                            String msg = _datasetDefinition.getLabel() + ": Successfully imported " + imported.size() + " rows from " + _tsvName;
                            if (useCutoff && skippedRowCount[0] > 0)
                                msg += " (skipped " + skippedRowCount[0] + " rows older than cutoff)";
                            _job.info(msg);
                            assert cpuCommit.stop();
                        }
                    }
                    assert cpuImport.stop();
                }
            }
        }
        catch (Exception x)
        {
            _job.error("Exception while importing dataset " + _datasetDefinition.getName() + " from " + _tsvName, x);
        }
        finally
        {
            for (String err : errors)
                _job.error(_tsvName + " -- " + err);

            if (_deleteAfterImport)
            {
                boolean success = _root.delete(_tsvName);
                if (success)
                    _job.info("Deleted file " + _tsvName);
                else
                    _job.error("Could not delete file " + _tsvName);
            }

            if (loader != null)
                loader.close();

            boolean debug = false;
            assert debug = true;
            if (debug)
            {
                LOG.debug(cpuDelete);
                LOG.debug(cpuImport);
                LOG.debug(cpuCommit);
            }
        }
    }

    public AbstractDatasetImportTask.Action getAction()
    {
        return _action;
    }

    public String getFileName()
    {
        return _tsvName;
    }

    public DatasetDefinition getDatasetDefinition()
    {
        return _datasetDefinition;
    }

    public String getVisitDatePropertyName()
    {
        if (visitDatePropertyName == null && getDatasetDefinition() != null)
            return getDatasetDefinition().getVisitDateColumnName();
        return visitDatePropertyName;
    }

    public void setVisitDatePropertyName(String visitDatePropertyName)
    {
        this.visitDatePropertyName = visitDatePropertyName;
    }

    public String getVisitDateURI(User user)
    {
        if (visitDatePropertyURI == null)
        {
            TableInfo ti = _datasetDefinition.getTableInfo(user, false);
            if (null != ti)
            for (ColumnInfo col : ti.getColumns())
            {
                if (col.getName().equalsIgnoreCase(getVisitDatePropertyName()))
                    visitDatePropertyURI = col.getPropertyURI();
            }
            if (visitDatePropertyURI == null)
                visitDatePropertyURI = DatasetDefinition.getVisitDateURI();
        }
        return visitDatePropertyURI;
    }
}
