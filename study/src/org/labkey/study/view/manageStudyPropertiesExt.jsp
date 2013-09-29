<%
/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.api.wiki.WikiRendererType" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%!
  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
      //Need to include the Util for a use of the form panel configuration.
      resources.add(ClientDependency.fromFilePath("clientapi/ext4"));
      return resources;
  }
%>
<%
    ViewContext context = getViewContext();
    boolean canEdit = context.getContainer().hasPermission(context.getUser(), AdminPermission.class);
    boolean emptyStudy = getStudy().isEmptyStudy();
    String timepointType = getStudy().getTimepointType().toString();
    String cancelLink = context.getActionURL().getParameter("returnURL");
    if (cancelLink == null || cancelLink.length() == 0)
        cancelLink = new ActionURL(StudyController.ManageStudyAction.class, context.getContainer()).toString();
%>

<%!

    public String shortenFileName(String fileName)
    {
        if (fileName.length() > 55)
        {
            fileName = fileName.substring(0, 54) + "...";
        }

        return fileName;
    }
%>

<div class="extContainer" id="manageStudyPropertiesDiv"></div>

<script type="text/javascript">

Ext4.QuickTips.init();

var canEdit = <%=q(canEdit?"true":"false")%>;
var editableFormPanel = canEdit;
var studyPropertiesFormPanel = null;
var timepointType = "<%=h(timepointType)%>";

function removeProtocolDocument(name, xid)
{
    Ext4.Msg.show({
        title : 'Remove Attachment',
        msg : 'Please confirm you would like to remove this study protocol document. This cannot be undone.',
        buttons: Ext4.Msg.OKCANCEL,
        icon: Ext4.MessageBox.QUESTION,
        fn  : function(b) {
            if (b == 'ok') {
                Ext4.Ajax.request({
                    url    : LABKEY.ActionURL.buildURL('study', 'removeProtocolDocument'),
                    method : 'POST',
                    success: function() {
                        var el = document.getElementById(xid);
                        if (el) {
                            el.parentNode.removeChild(el);
                        }
                    },
                    failure: function() {
                        alert('Failed to remove study protocol document.');
                    },
                    params : { name: name}
                });
            }
        }
    });
}

function addExtFilePickerHandler()
{
    var fibasic = Ext4.create('Ext.form.field.File', {
        width  : 300,
        height : 30,
        style  : 'float: left;'
    });

    var removeBtn = Ext4.create('Ext.Button', {
        text: "remove",
        name: "remove",
        uploadId: fibasic.id,
        style : 'margin: 4px;',
        handler: removeNewAttachment
    });

    var uploadField = Ext4.create('Ext.Panel', {
        border : false, frame : false,
        height : 35,
        items:[fibasic, removeBtn]
    });

    var protocolPanel = studyPropertiesFormPanel.getComponent('protocolPanel');
    if (protocolPanel) {
        protocolPanel.add(uploadField);
        protocolPanel.enlarge();
        protocolPanel.doLayout();
    }
    studyPropertiesFormPanel.doLayout();
}

function removeNewAttachment(btn)
{
    // In order to 'remove' an attachment before it is submitted we must hide and disable it. We CANNOT destroy the
    // elements related to the upload field, if we do the form will not validate. This is a known Issue with Ext 3.4.
    // http://www.sencha.com/forum/showthread.php?25479-2.0.1-2.1-Field.destroy()-on-Fields-rendered-by-FormLayout-does-not-clean-up.
    Ext4.getCmp(btn.uploadId).disable();
    studyPropertiesFormPanel.getComponent('protocolPanel').shrink();
    studyPropertiesFormPanel.doLayout();
    btn.ownerCt.hide();
}

function mask()
{
    Ext4.getBody().mask();
}

function unmask()
{
    Ext4.getBody().unmask();
}

function showSuccessMessage(message, after)
{
    Ext4.get("formError").update("");
    var el = Ext4.get("formSuccess");
    el.update(message);
    el.pause(3).fadeOut({callback:function(){el.update("");}});
}


function onSaveSuccess_updateRows()
{
    // if you want to stay on page, you need to refresh anyway to udpate attachments
    var msgbox = Ext4.Msg.show({
        title:'Status',
        msg: '<span class="labkey-message">Changes saved</span>',
        buttons: false
    });
    var el = msgbox.getDialog().el;
    el.pause(1).fadeOut({callback:cancelButtonHandler});
}

function onSaveSuccess_formSubmit()
{

    // if you want to stay on page, you need to refresh anyway to udpate attachments
    LABKEY.submit=true;
    var msgbox = Ext4.Msg.show({
        title:'Status',
        msg: '<span class="labkey-message">Changes saved</span>',
        buttons: false
    });
    window.location = <%=q(cancelLink)%>;
    var el = msgbox.getEl();
    el.pause(1).fadeOut({callback:cancelButtonHandler});
}


function onSaveFailure_updateRows(error)
{
    unmask();
    Ext4.get("formSuccess").update("");
    Ext4.get("formError").update(error.exception);
}


function onSaveFailure_formSubmit(form, action)
{
    unmask();
    switch (action.failureType)
    {
        case Ext4.form.Action.CLIENT_INVALID:
            Ext4.Msg.alert('Failure', 'Form fields may not be submitted with invalid values');
            break;
        case Ext4.form.Action.CONNECT_FAILURE:
            Ext4.Msg.alert('Failure', 'Ajax communication failed');
            break;
        case Ext4.form.Action.SERVER_INVALID:
            Ext4.Msg.alert('Failure', action.result.msg);
    }
}

function submitButtonHandler()
{
    var form = studyPropertiesFormPanel.getForm();
    if (form.isValid())
    {
        /* This works except for the file attachment
         var rows = studyPropertiesFormPanel.getFormValues();
         LABKEY.Query.updateRows(
         {
         schemaName:'study',
         queryName:'StudyProperties',
         rows:rows,
         success : onSaveSuccess_updateRows,
         failure : onSaveFailure_updateRows
         });
         */

        form.fileUpload = true;
        form.submit(
                {
                    url     : LABKEY.ActionURL.buildURL('study', 'manageStudyProperties.view'),
                    success : onSaveSuccess_formSubmit,
                    failure : onSaveFailure_formSubmit
                });
        mask();
    }
    else
    {
        Ext4.MessageBox.alert("Error Saving", "There are errors in the form.");
    }
}


function editButtonHandler()
{
    editableFormPanel = true;
    destroyFormPanel();
    createPage();
}


function cancelButtonHandler()
{
    LABKEY.setSubmit(true);
    window.location = <%=q(cancelLink)%>;
}


function doneButtonHandler()
{
    LABKEY.setSubmit(true);
    window.location = <%=q(cancelLink)%>;
}


function destroyFormPanel()
{
    if (studyPropertiesFormPanel)
    {
        studyPropertiesFormPanel.destroy();
        studyPropertiesFormPanel = null;
    }
}


var renderTypes = {<%
String comma = "";
for (WikiRendererType type : getRendererTypes())
{
    %><%=comma%><%=q(type.name())%>:<%=q(type.getDisplayName())%><%
    comma = ",";
}%>};


function renderFormPanel(data, editable){
    var protocolDocs = [];
<%
    int x = 0;
    for (Attachment att : getStudy().getProtocolDocuments())
    {
%>
    protocolDocs.push({
        logo : '<%= request.getContextPath() + att.getFileIcon() %>',
        text : '<%= h(shortenFileName(att.getName()))%>',
        removeURL : <%=PageFlowUtil.jsString(att.getName())%>,
        atId : <%=x%>
    });
<%
        x++;
    }
%>
    var initHeight = <%=x%>;
    Ext4.define('ProtocolDoc', {
        extend : 'Ext.data.Model',
        fields : [
            {name : 'logo', type : 'string' },
            {name : 'text', type : 'string'},
            {name : 'removeURL', type : 'string'},
            {name : 'atId', type : 'int'}
        ]
    });

    var protoTemplate = new Ext4.XTemplate(
        '<tpl for=".">',
            '<div class="protoDoc" id="attach-{atId}">',
                '<td>&nbsp;<img src="{logo}" talt="logo"/></td>',
                '<td>&nbsp; {text} </td>',
                '<td>&nbsp; [<a class="removelink" onclick="removeProtocolDocument(\'{removeURL}\', \'attach-{atId}\');">remove </a>]</td>',
            '</div>',
        '</tpl>',
        '<div>',
            '&nbsp;<a onclick="addExtFilePickerHandler(); return false;" href="#addFile"><img src="<%=request.getContextPath()%>/_images/paperclip.gif">&nbsp;&nbsp;Attach a file</a>',
        '</div>'
    );

    var buttons = [];

    if (editable)
    {
        buttons.push({
            text:"Submit",
            handler: submitButtonHandler,
            scope: this
        });
        buttons.push({
            text: "Cancel",
            handler: cancelButtonHandler
        });
    }
    else if (canEdit)
    {
        buttons.push({text:"Edit", handler: editButtonHandler});
        buttons.push({text:"Done", handler: doneButtonHandler});
    }

    var getConfig = function(searchString){
        var fields = data.metaData.fields;
        for(var i = 0; i < fields.length; i++){
            if(fields[i].caption == searchString && !fields[i].lookup)
                return LABKEY.ext4.Util.getFormEditorConfig(data.metaData.fields[i]);
        }
        return undefined;
    };
    var items = [
        getConfig('Label'),
        getConfig('Investigator'),
        getConfig('Grant'),
        getConfig('Description')
    ];

    var handledFields = {
        Label: true,
        Investigator: true,
        Grant: true,
        Description: true,

        // Don't show these fields, they have a special UI for editing them
        ParticipantAliasDatasetId: true,
        ParticipantAliasSourceProperty: true,
        ParticipantAliasSourceProperty: true,

        DescriptionRendererType: true,

        TimepointType: true,
        Container: true
    };

    if (editableFormPanel) {
        items.push({
            fieldLabel : 'Render Type',
            labelWidth : 150,
            padding : 5,
            hiddenName : 'DescriptionRendererType',
            name : 'DescriptionRendererType',
            triggerAction : 'all',
            queryMode: 'local',
            valueField: 'renderType',
            displayField: 'displayText',
            width : 500,
            editable : false,
            value : data.rows[0]['DescriptionRendererType'].value,
            store : Ext4.create('Ext.data.ArrayStore', {
                id : 0,
                fields : ['renderType', 'displayText'],
                data : [
<%
                    comma = "";
                    for (WikiRendererType type : getRendererTypes())
                    {
                        %><%=comma%>[<%=q(type.name())%>,<%=q(type.getDisplayName())%>]<%
                        comma = ",";
                    }
%>
                ]
            })
        });
    }

    items.push({
        xtype: 'label',
        text : 'Protocol Documents:',
        style : 'float: left;',
        width : 150
    });
    items.push({
        xtype: 'panel',
        itemId : 'protocolPanel',
        cls    : 'protocolPanel',
        height : (initHeight * 25) + 30,
        border : false, frame: false,
        enlarge : function(){
            this.height += 35;
        },
        shrink : function(){
            this.height -= 35;
        },
        items : [{
            xtype : 'dataview',
            store : {
                xtype: 'store',
                model : 'ProtocolDoc',
                data : protocolDocs
            },
            tpl : protoTemplate,
            itemSelector :  'div.protoDoc',
            emptyText : 'No current docs'
        }]
    });

    // the original form didn't include these, but we can decide later
    items.push({
        xtype : 'radiogroup',
        fieldLabel : "Timepoint Type",
        style : 'margin: 0px 5px;',
        labelWidth : 150,
        width : 500,
        columns : 2,
        vertical : true,
        items : [{
            xtype: 'radio',
            id : 'visitRadio',
            inputId : 'visit',
            disabled: <%=!emptyStudy%>,
            boxLabel: 'VISIT',
            inputValue: 'VISIT',
            value: 'VISIT',
            name: 'TimepointType',
            checked: timepointType == 'VISIT'
        },{
            xtype: 'radio',
            id : 'dateRadio',
            inputId : 'date',
            disabled: <%=!emptyStudy%>,
            boxLabel: 'DATE',
            inputValue: 'DATE',
            name: 'TimepointType',
            checked: timepointType == 'DATE'
        }]
    });

    var info = data.rows[0];

    items[0].value = info.Label.value;
    items[1].value = info.Investigator.value;
    items[2].value = info.Grant.value;
    items[3].value = info.Description.value;

    var cm = data.columnModel,
        col,
        hold;

    for(var i = 0; i < cm.length; i++){
        col = cm[i];
        if(col.hidden) continue;
        if(handledFields[col.dataIndex]) continue;
        hold = getConfig(col.header);
        if(hold != undefined){
            items.push(hold);
        }
    }

    for(i = 8; i < items.length; i++){

        // initialize the form elements
        var value = data.rows[0][items[i].name].value;

        // stupid date conversion...
        if (items[i].xtype == 'datefield')
        {
            items[i].value = (value != null && value != '') ? new Date(value) : '';
            items[i].listeners = {
                render : {
                    fn : function(cmp){
                        // hack to fix Ext bug with initializing date values, erroneously setting the
                        // field dirty state
                        if (cmp.isDirty())
                            cmp.originalValue = cmp.getValue();
                    }
                }
            }
        }
        else
            items[i].value = value;
    }

    studyPropertiesFormPanel = Ext4.create('Ext.form.Panel', {
        padding : 10,
        border : false,
        bodyStyle : 'background-color: transparent;',
        defaults : {labelWidth: 150, width: 500, height : 30, padding : '5px', disabled : !editableFormPanel},
        items: items,
        buttons : buttons,
        buttonAlign : 'left',
        renderTo : 'testZone'
    });
}


function onQuerySuccess(data) // e.g. callback from Query.selectRows
{
    renderFormPanel(data, editableFormPanel);
}


function onQueryFailure(a)
{
    alert(a);
}


function createPage()
{
    LABKEY.Query.selectRows({
        requiredVersion : '9.1',
        schemaName: 'study',
        queryName: 'StudyProperties',
        columns: '*',
        success: onQuerySuccess,
        failure: onQueryFailure
    });
}

function isFormDirty()
{
    return studyPropertiesFormPanel && studyPropertiesFormPanel.getForm().isDirty();
}

window.onbeforeunload = LABKEY.beforeunload(isFormDirty);

Ext4.onReady(createPage);

</script>

<span id=formSuccess class=labkey-message-strong></span><span id=formError class=labkey-error></span>&nbsp;</br>
<div id='formDiv'></div>
<div id='testZone'></div>
