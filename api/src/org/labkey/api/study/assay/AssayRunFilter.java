package org.labkey.api.study.assay;

import org.labkey.api.exp.ExperimentRunFilter;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.data.Container;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ActionButton;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.DataView;
import org.labkey.api.security.ACL;

/**
 * User: brittp
* Date: Oct 10, 2007
* Time: 2:24:42 PM
*/
public class AssayRunFilter extends ExperimentRunFilter
{
    public static final String SCHEMA_NAME = "assay";
    private final ExpProtocol _protocol;
    private final Container _container;

    public AssayRunFilter(ExpProtocol protocol, Container c)
    {
        super(protocol.getName(), SCHEMA_NAME, getRunListTableName(protocol));
        _protocol = protocol;
        _container = c;
    }

    public static String getRunListTableName(ExpProtocol protocol)
    {
        AssayProvider provider = AssayService.get().getProvider(protocol);
        return provider.getRunListTableName(protocol);
    }

    public void populateButtonBar(ViewContext context, ButtonBar bar, DataView view)
    {
        AssayProvider provider = AssayService.get().getProvider(_protocol);
        if (provider.allowUpload(context.getUser(), context.getContainer(), _protocol) &&
                context.hasPermission(ACL.PERM_INSERT))
        {
            ActionButton uploadButton = new ActionButton("Upload Runs", AssayService.get().getUploadWizardURL(_container, _protocol));
            bar.add(uploadButton);
        }
    }

    public Priority getPriority(ExpProtocol protocol)
    {
        if (_protocol.getLSID().equals(protocol.getLSID()))
        {
            return Priority.HIGH;
        }
        return null;
    }
}
