package org.labkey.pipeline;

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.gwt.client.pipeline.GWTPipelineConfig;
import org.labkey.api.gwt.client.pipeline.GWTPipelineLocation;
import org.labkey.api.gwt.client.pipeline.GWTPipelineTask;
import org.labkey.api.gwt.client.pipeline.PipelineGWTService;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.pipeline.TaskFactory;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.pipeline.api.PipelineJobServiceImpl;
import org.labkey.pipeline.api.properties.GlobusClientPropertiesImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: jeckels
 * Date: Jan 20, 2012
 */
public class PipelineGWTServiceImpl extends BaseRemoteService implements PipelineGWTService
{
    public PipelineGWTServiceImpl(ViewContext viewContext)
    {
        super(viewContext);
    }

    @Override
    public GWTPipelineConfig getLocationOptions(String pipelineId)
    {
        try
        {
            TaskPipeline taskPipeline = PipelineJobServiceImpl.get().getTaskPipeline(new TaskId(pipelineId));
            if (taskPipeline == null)
            {
                throw new NotFoundException("Can't find pipelineId: " + pipelineId);
            }

            Map<String, GWTPipelineLocation> locations = new CaseInsensitiveHashMap<GWTPipelineLocation>();
            Map<String, GlobusClientPropertiesImpl> globusProperties = new CaseInsensitiveHashMap<GlobusClientPropertiesImpl>();
            for (GlobusClientPropertiesImpl globus : PipelineJobServiceImpl.get().getGlobusClientPropertiesList())
            {
                globusProperties.put(globus.getLocation(), globus);
                String name = globus.getLocation() == null ? "cluster" : globus.getLocation();
                locations.put(name, new GWTPipelineLocation(name, globus.getAvailableQueues()));
            }

            List<GWTPipelineTask> tasks = new ArrayList<GWTPipelineTask>();
            for (TaskId taskId : taskPipeline.getTaskProgression())
            {
                TaskFactory taskFactory = PipelineJobServiceImpl.get().getTaskFactory(taskId);
                GWTPipelineLocation location = locations.get(taskFactory.getExecutionLocation());
                if (location == null)
                {
                    location = new GWTPipelineLocation(taskFactory.getExecutionLocation(), null);
                    locations.put(location.getLocation(), location);
                }
                tasks.add(new GWTPipelineTask(taskId.toString(), taskFactory.getStatusName(), taskFactory.getGroupParameterName(), location.isCluster(), location));
            }

            return new GWTPipelineConfig(tasks, new ArrayList<GWTPipelineLocation>(locations.values()));
        }
        catch (ClassNotFoundException e)
        {
            throw new NotFoundException("Can't find pipelineId: " + pipelineId, e);
        }
    }
}
