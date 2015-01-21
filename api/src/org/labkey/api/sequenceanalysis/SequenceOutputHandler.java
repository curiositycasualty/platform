/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.api.sequenceanalysis;

import com.drew.lang.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.ldk.table.ButtonConfigFactory;
import org.labkey.api.module.Module;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.template.ClientDependency;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * This interface describes an action that acts upon sequence data.  It is designed to let modules easily register actions that
 * process or visualize data, including those that require background pipeline processing.  If registed, this will appear as an
 * option in the outputfiles dataregion.  When the button is clicked, it will check whether the selected files are available to this
 * handler by calling canProcess().  If the files pass, the handler will do one of two things.  If this handler returns
 * a non-null string for getSuccessUrl(), the window will navigate to this URL.  This would allow the handler to have a secondary page
 * to capture additional user input or show a report.  Alternately,
 *
 * Created by bimber on 8/25/2014.
 */
public interface SequenceOutputHandler
{
    public String getName();

    public String getDescription();

    public boolean canProcess(SequenceOutputFile o);

    /**
     * This should be a JS function that will be called after we have verified that the output files selected
     * can be processed by this handler.  The handler should provide either a JS handler or a successURL.  If both are provided,
     * the URL will be used prferentially.  The JS handler function will be called with the following arguments:
     * dataRegionName: the name of the DataRegion
     * outputFileIds: the RowIDs of the output files selected
     */
    public @Nullable String getButtonJSHandler();

    /**
     * When the user chooses this option,
     */
    public @Nullable ActionURL getButtonSuccessUrl(Container c, User u, List<Integer> outputFileIds);

    /**
     * The module that provides this handler.  If the module is not active in the current container, this handler will not be shown.
     */
    public Module getOwningModule();

    /**
     * An ordered list of ClientDependencies, which allows this handler to declare any client-side resources it depends upon.
     */
    public LinkedHashSet<ClientDependency> getClientDependencies();

    /**
     * Allows handlers to perform processing on the input SequenceOutputFiles.  This will be run in the background as a pipeline job.
     * The intention is to allow handlers to only implement the actual processing code they need, without a separate server-side action, pipeline job, etc.
     * Certain handlers will not use this method, and it is recommended that they throw an IllegalArgumentException
     * @param job The pipeline job running this task
     * @param inputFiles The list of input files to process
     * @param params
     * @param actions
     * @param outputsToCreate
     * @return A list of new SequenceOutputFile records to create
     */
    public void processFiles(PipelineJob job, List<SequenceOutputFile> inputFiles, JSONObject params, List<RecordedAction> actions, List<SequenceOutputFile> outputsToCreate) throws UnsupportedOperationException, PipelineJobException;
}