<%
/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
%>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.pipeline.PipelineController.StartFolderImportForm" %>
<%@ page import="org.labkey.pipeline.PipelineController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("Ext4"));
        return resources;
    }
%>
<%
    JspView<StartFolderImportForm> me = (JspView<StartFolderImportForm>) HttpView.currentView();
    StartFolderImportForm bean = me.getModelBean();

    Container c = getViewContext().getContainerNoTab();
    Container project = c.getProject();
    boolean canCreateSharedDatasets = false;

    if (!c.isProject() && null != project && project != c)
    {
        if (project.hasPermission(getViewContext().getUser(), AdminPermission.class))
        {
            Study studyProject = StudyService.get().getStudy(project);
            if (null != studyProject && studyProject.getShareDatasetDefinitions())
                canCreateSharedDatasets = true;
        }
    }
%>

<labkey:errors/>
<labkey:form id="pipelineImportForm" action="<%=h(buildURL(PipelineController.StartFolderImportAction.class))%>" method="post">
    <input type="hidden" name="fromZip" value=<%=bean.isFromZip()%>>
    <input type="hidden" name="asStudy" value=<%=bean.isAsStudy()%>>
    <input type="hidden" name="filePath" value=<%=q(bean.getFilePath())%>>
    <div id="startPipelineImportForm"></div>
    <div id="advancedImportOptionsForm"></div>
    <div class="main-form-spacer"></div>
    <%=button("Start Import").submit(true)%>
</labkey:form>

<style>
    .main-form-spacer {
        padding-bottom: 30px;
    }

    .main-form-cell {
        padding-top: 5px;
    }

    .import-option-panel {
        padding-top: 10px;
    }

    .import-option-panel .x4-panel-body {
        padding: 10px;
    }

    .import-option-header {
        padding-bottom: 5px;
    }

    .import-option-input {
        padding-top: 5px;
    }
</style>

<script type="text/javascript">
Ext4.onReady(function()
{
    LABKEY.Ajax.request({
        url: LABKEY.ActionURL.buildURL("core", "getRegisteredFolderImporters"),
        method: 'POST',
        jsonData: {
            sortAlpha: true
        },
        scope: this,
        success: function (response)
        {
            var responseText = Ext4.decode(response.responseText);

            Ext4.create('LABKEY.import.OptionsPanel', {
                renderTo: 'startPipelineImportForm',
                advancedImportOptionId: 'advancedImportOptionsForm',
                importers: responseText.importers
            });
        }
    });
});

Ext4.define('LABKEY.import.OptionsPanel', {
    extend: 'Ext.form.Panel',

    border: false,
    bodyStyle: 'background-color: transparent;',

    advancedImportOptionId: null,
    importers: [],

    initComponent: function()
    {
        this.items = [
            this.getMainFormView(),
            this.getAdvancedImportForm()
        ];

        this.callParent();
    },

    getMainFormView : function()
    {
        if (!this.mainFormView)
        {
            var data = [{
                header: 'Validate Queries',
                description: 'By default, queries will be validated upon import of a study/folder archive and any failure '
                + 'to validate will cause the import job to raise an error. To suppress this validation step, uncheck '
                + 'the option below before clicking \'Start Import\'.',
                name: 'validateQueries',
                initChecked: <%=q(bean.isValidateQueries() ? "checked": "")%>,
                isChecked: <%=bean.isValidateQueries()%>,
                label: 'Validate all queries after import',
                optionsForm: null
            },
            {
                header: 'Advanced Import Options',
                description: 'By default, all settings from the import archive will be used. If you would like to select a subset of '
                + 'those import options, you can use the advanced import options section to see the full list of folder '
                + 'archive settings to be imported.',
                name: 'advancedImportOptions',
                initChecked: <%=q(bean.isAdvancedImportOptions() ? "checked": "")%>,
                isChecked: <%=bean.isAdvancedImportOptions()%>,
                label: 'Use advanced import options',
                optionsForm: this.getAdvancedImportForm
            }];

<%
            if (canCreateSharedDatasets)
            {
%>
                data.splice(0, 0, {
                    header: 'Shared Datasets',
                    description: 'By default, datasets will be created in this container. For Dataspace projects, shared datasets are '
                        + 'created at the project level so that they can be used by each of the study folders in the project.',
                    name: 'createSharedDatasets',
                    initChecked: <%=q(bean.isCreateSharedDatasets() ? "checked": "")%>,
                    isChecked: <%=bean.isCreateSharedDatasets()%>,
                    label: 'Create shared datasets',
                    optionsForm: null
                });
<%
            }
%>

            var store = Ext4.create('Ext.data.Store', {
                fields: ['header', 'description', 'name', 'initChecked', 'isChecked', 'label', 'include', 'optionsForm'],
                data: data
            });

            this.mainFormView = Ext4.create('Ext.view.View', {
                store: store,
                itemSelector: 'input',
                tpl: new Ext4.XTemplate(
                    '<tpl for=".">',
                        '<table cellpadding=0>',
                        ' <tr><td class="labkey-announcement-title" align=left><span>{header}</span></td></tr>',
                        ' <tr><td class="labkey-title-area-line"></td></tr>',
                        ' <tr><td>{description}</td></tr>',
                        ' <tr><td class="main-form-cell">',
                        '  <label><input type="checkbox" class="main-input" id="{name}" name="{name}" value="true" {initChecked}>{label}</label>',
                        ' </td></tr>',
                        '</table>',
                    '</tpl>'
                )
            });

            this.mainFormView.on('itemclick', function(view, record)
            {
                if (record.get('optionsForm'))
                {
                    var optionsForm = record.get('optionsForm').call(this);
                    if (optionsForm)
                    {
                        var checked = Ext4.get(record.get('name')).dom.checked;
                        optionsForm.setVisible(checked);

                        // set all folder import type checkboxes to match this checked state
                        Ext4.each(document.getElementsByName('folderDataTypes'), function(input)
                        {
                            input.checked = checked;
                        });
                    }
                }
            }, this);
        }

        return this.mainFormView;
    },

    getAdvancedImportForm : function()
    {
        if (!this.advancedImportOptionsForm)
        {
            var advancedImportItems = [{
                xtype: 'box',
                cls: 'import-option-header',
                html: '<div class="import-option-header">Folder objects to import:</div>' +
                        '<div class="labkey-title-area-line"></div>'
            }];

            Ext4.each(this.importers, function(importer)
            {
                var selectionText = importer['selectionText'];

                advancedImportItems.push({
                    xtype: 'box',
                    cls: 'import-option-input',
                    html: '<label><input type="checkbox" name="folderDataTypes" '
                            + '<%=h(bean.isAdvancedImportOptions() ? "checked": "")%> value="'
                            + selectionText + '">' + selectionText + '</label>'
                });
            });

            this.advancedImportOptionsForm = Ext4.create('Ext.form.Panel', {
                renderTo: this.advancedImportOptionId,
                hidden: <%=!bean.isAdvancedImportOptions()%>,
                cls: 'import-option-panel',
                items: advancedImportItems
            });
        }

        return this.advancedImportOptionsForm;
    }
});

</script>
