package org.labkey.issue.experimental.actions;

import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.issue.IssuesController;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;
import org.springframework.validation.BindException;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by klum on 5/20/2016.
 */
@RequiresPermission(ReadPermission.class)
public class GetRelatedFolder extends ApiAction<IssuesController.IssuesForm>
{
    @Override
    public Object execute(IssuesController.IssuesForm form, BindException errors) throws Exception
    {
        IssueManager.getIssueListDefs(null);
        ApiSimpleResponse response = new ApiSimpleResponse();
        Collection<Map<String, String>> containers = new LinkedList<>();

        String issueDefName = form.getIssueDefName();
        if (issueDefName != null)
        {
            IssueListDef issueListDef = IssueManager.getIssueListDef(getContainer(), issueDefName);
            if (issueListDef != null)
            {
                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("name"), issueDefName);
                SimpleFilter.FilterClause filterClause = IssueListDef.createFilterClause(issueListDef, getUser());

                if (filterClause != null)
                    filter.addClause(filterClause);
                else
                    filter.addCondition(FieldKey.fromParts("container"), getContainer());

                List<IssueListDef> defs = new TableSelector(IssuesSchema.getInstance().getTableInfoIssueListDef(), filter, null).getArrayList(IssueListDef.class);
                for (IssueListDef def : defs)
                {
                    // exclude current container
                    if (!def.getContainerId().equals(getContainer().getId()))
                    {
                        Container c = ContainerManager.getForId(def.getContainerId());
                        if (c.hasPermission(getUser(), InsertPermission.class))
                        {
                            containers.add(PageFlowUtil.map(
                                    "containerId", c.getId(),
                                    "containerPath", c.getPath()));
                        }
                    }
                }
            }
        }
        response.put("containers", containers);

        return response;
    }
}
