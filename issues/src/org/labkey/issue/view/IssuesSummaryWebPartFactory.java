package org.labkey.issue.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.issue.IssuesController;
import org.labkey.issue.IssuesModule;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;

import java.util.Map;

/**
 * Created by klum on 5/27/2016.
 */
public class IssuesSummaryWebPartFactory extends BaseWebPartFactory
{
    public static final String NAME = "Issues Summary";

    public IssuesSummaryWebPartFactory()
    {
        super(NAME, true, true);
        addLegacyNames(IssuesModule.NAME);
    }

    public WebPartView getWebPartView(@NotNull ViewContext context, @NotNull Portal.WebPart webPart)
    {
        Map<String, String> propertyMap = webPart.getPropertyMap();
        String issueDefName = propertyMap.get(IssuesListView.ISSUE_LIST_DEF_NAME);
        if (issueDefName == null)
            issueDefName = IssueManager.getDefaultIssueListDefName(context.getContainer());

        WebPartView view;
        IssueListDef issueListDef = IssueManager.getIssueListDef(context.getContainer(), issueDefName);
        if (issueListDef != null)
            view = new SummaryWebPart(issueDefName, propertyMap);
        else
        {
            view = new HtmlView(IssuesController.getUndefinedIssueListMessage(context));
            view.setFrame(WebPartView.FrameType.PORTAL);
            String title = IssueManager.getEntryTypeNames(context.getContainer()).pluralName + " Summary";
            view.setTitle(title);
        }
        return view;
    }

    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
    {
        return new IssuesListView.IssuesListConfig(webPart);
    }
}
