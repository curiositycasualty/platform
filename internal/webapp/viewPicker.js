/*
 * Copyright (c) 2010-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/*
    A pop-up, ext-style schema/query/view picker.  Just call chooseView(), specifying a dialog title, some help text,
    a separator character, and a function to call when the user clicks submit.  The function parameter is a String
    containing schema, query, and (optional) view separated by the separator character.

    Originally developed by britt.  Generalized into a reusable widget by adam.
*/
var dataFieldName = 'name';
var dataUrlFieldName = 'viewDataUrl';
                                    // schema, query, view, column, folder
var initialValues = new Array();        // TODO: Select these values in combos

function populateSchemas(schemaCombo, queryCombo, viewCombo, schemasInfo, includeSchema, columnCombo)
{
    var schemas;

    if (includeSchema)
    {
        schemas = new Array();
        var len = schemasInfo.schemas.length;

        for (var i = 0; i < len; i++)
            if (includeSchema(schemasInfo.schemas[i]))
                schemas.push(schemasInfo.schemas[i]);
    }
    else
    {
        schemas = schemasInfo.schemas;
    }

    schemaCombo.store.removeAll();
    schemaCombo.store.loadData(getArrayArray(schemas));
    schemaCombo.on("select", function(combo, record, index)
    {
        queryCombo.clearValue();
        viewCombo.clearValue();
        if (columnCombo)
            columnCombo.clearValue();
        LABKEY.Query.getQueries({
            schemaName: record.data[record.fields.first().name],
            successCallback: function(details) { populateQueries(queryCombo, viewCombo, details, columnCombo); }
        });
    });

    if (initialValues[0])
    {
        var index = schemaCombo.getStore().findExact('name', initialValues[0]);

        if (-1 != index)
        {
            var record = schemaCombo.getStore().getAt(index);
            schemaCombo.setValue(initialValues[0]);
            schemaCombo.fireEvent('select', schemaCombo, record, index);
        }

        initialValues[0] = null;
    }
}

function populateQueries(queryCombo, viewCombo, queriesInfo, columnCombo)
{
    var records = [];
    for (var i = 0; i < queriesInfo.queries.length; i++)
    {
        var queryInfo = queriesInfo.queries[i];
        records[i] = [queryInfo.name, queryInfo.viewDataUrl];
    }

    queryCombo.store.removeAll();
    queryCombo.store.loadData(records);
    queryCombo.on("select", function(combo, record, index)
    {
        viewCombo.clearValue();
        if (columnCombo)
            columnCombo.clearValue();
        LABKEY.Query.getQueryViews({
            schemaName: queriesInfo.schemaName,
            queryName: record.data[record.fields.first().name],
            successCallback: function(details) { populateViews(viewCombo, details, columnCombo); }
        })
    });

    if (initialValues[1])
    {
        var queryComboIndex = queryCombo.getStore().findExact('name', initialValues[1]);

        if (-1 != queryComboIndex)
        {
            var record = queryCombo.getStore().getAt(queryComboIndex);
            queryCombo.setValue(initialValues[1]);
            queryCombo.fireEvent('select', queryCombo, record, queryComboIndex);
        }

        initialValues[1] = null;
    }
}

var defaultViewLabel = "[default view]";

function populateViews(viewCombo, queryViews, columnCombo)
{
    var records = [[defaultViewLabel]];

    for (var i = 0; i < queryViews.views.length; i++)
    {
        var viewInfo = queryViews.views[i];
        if (viewInfo.name != null && viewInfo.name != "")
            records[records.length] = [viewInfo.name, viewInfo.viewDataUrl];
    }

    viewCombo.store.removeAll();
    viewCombo.store.loadData(records);

    if (columnCombo)
    {
        viewCombo.on("select", function(combo, record, index)
        {
            columnCombo.clearValue();
            LABKEY.Query.getQueryDetails({
                schemaName: queryViews.schemaName,
                queryName: queryViews.queryName,
                initializeMissingView: true,
                successCallback: function(details) { populateColumns(columnCombo, details); }
            })
        });
    }

    var initialView = defaultViewLabel;
    if (initialValues[2])
    {
        var viewComboIndex = viewCombo.getStore().findExact('name', initialValues[2]);

        if (-1 != viewComboIndex)
        {
            initialView = initialValues[2];
        }

        initialValues[2] = null;
    }

    viewCombo.setValue(initialView);
    if (columnCombo)
    {
        var record = viewCombo.getStore().getAt(viewComboIndex);
        viewCombo.fireEvent('select', viewCombo, record, viewComboIndex);
    }
}

function populateColumns(columnCombo, details)
{
    var records = [];

    var columns = details.columns;
    for (var i = 0; i < columns.length; i++)
    {
        var name = columns[i].name;
        records[records.length] = [name, columns[i].fieldKey];
    }

    columnCombo.store.removeAll();
    columnCombo.store.loadData(records);

    if (initialValues[3])
    {
        var queryColumnIndex = columnCombo.getStore().findExact('name', initialValues[3]);

        if (-1 != queryColumnIndex)
        {
            var record = columnCombo.getStore().getAt(queryColumnIndex);
            columnCombo.setValue(initialValues[3]);
            columnCombo.fireEvent('select', columnCombo, record, queryColumnIndex);
        }

        initialValues[3] = null;
    }
}

function getArrayArray(simpleArray)
{
    var arrayArray = [];
    for (var i = 0; i < simpleArray.length; i++)
    {
        arrayArray[i] = [];
        arrayArray[i][0] = simpleArray[i];
    }
    return arrayArray;
}

var s;

function createCombo(fieldLabel, name, id, allowBlank)
{
    var combo = new Ext.form.ComboBox({
        typeAhead: false,
        store: new Ext.data.ArrayStore({
            fields: [{
                name: dataFieldName,
                sortType: function(value) { return value.toLowerCase(); }
            }],
            sortInfo: { field: dataFieldName }
        }),
        valueField: dataFieldName,
        displayField: dataFieldName,
        fieldLabel: fieldLabel,
        name: name,
        id: id,
        allowBlank: allowBlank,
        readOnly:false,
        editable:false,
        mode:'local',
        triggerAction: 'all',
        lazyInit: false
    });
    return combo;
}

function createSchemaCombo()
{
    return createCombo("Schema", "schema", "userQuery_schema", false);
}

function createQueryCombo()
{
    return createCombo("Query", 'query', 'userQuery_query', false);
}

function createViewCombo()
{
    return createCombo("View", "view", "userQuery_view", true);
}

function createFolderCombo()
{
    return createCombo("Folder", "folders", "userQuery_folders", true);
}

function createColumnCombo()
{
    return createCombo("Title Column", "column", "userQuery_Column", true);
}

function createRootFolderCombo()
{
    return createCombo("Root Folder", "rootFolder", "userQuery_rootFolder", true);
}

function createFolderTypesCombo()
{
    return createCombo("Folder Types", "folderTypes", "userQuery_folderTypes", true);
}

// current value is an optional string parameter that provides string containing the current value.
// includeSchema is an optional function that determines if passed schema name should be included in the schema drop-down.
function chooseView(title, helpText, sep, submitFunction, currentValue, includeSchema)
{
    if (currentValue)
        initialValues = currentValue.split(sep);

    var schemaCombo = createSchemaCombo();
    s = schemaCombo;
    var queryCombo = createQueryCombo();
    var viewCombo = createViewCombo();

    LABKEY.Query.getSchemas({
        successCallback: function(schemasInfo) { populateSchemas(schemaCombo, queryCombo, viewCombo, schemasInfo, includeSchema, null); }
    });

    var labelStyle = 'border-bottom:1px solid #AAAAAA;margin:3px';

    var queryLabel = new Ext.form.Label({
        html: '<div style="' + labelStyle +'">' + helpText + '<\/div>'
    });

    var formPanel = new Ext.form.FormPanel({
        padding: 5,
        timeout: Ext.Ajax.timeout,
        items: [queryLabel, schemaCombo, queryCombo, viewCombo]});

    var win = new Ext.Window({
        title: title,
        layout:'fit',
        border: false,
        width: 475,
        height: 270,
        closeAction:'close',
        modal: true,
        items: formPanel,
        resizable: false,
        buttons: [{
            text: 'Submit',
            id: 'btn_submit',
            handler: function(){
                var form = formPanel.getForm();

                if (form && !form.isValid())
                {
                    Ext.Msg.alert(title, 'Please complete all required fields.');
                    return false;
                }

                var viewName = viewCombo.getValue();
                if (viewName == defaultViewLabel)
                    viewName = "";

                submitFunction(schemaCombo.getValue() + sep +
                               queryCombo.getValue() + sep +
                               viewName);
                win.close();
            }
        },{
            text: 'Cancel',
            id: 'btn_cancel',
            handler: function(){win.close();}
        }],
        bbar: [{ xtype: 'tbtext', text: '', id:'statusTxt'}]
    });
    win.show();
}

function customizeMenu(submitFunction, cancelFunction, renderToDiv, currentValue, includeSchema)
{
    var schemaCombo = createSchemaCombo();
    s = schemaCombo;
    var queryCombo = createQueryCombo();
    var viewCombo = createViewCombo();
    var columnCombo = createColumnCombo();
    var folderCombo = createFolderCombo();

    var title = "";
    var schemaName = "";
    var queryName = "";
    var viewName = "";
    var columnName = "";
    var folderName = "";
    var urlTop = "";
    var urlBottom = "";
    var isChoiceListQuery = true;

    if (currentValue)
    {
        title = currentValue.title;
        schemaName = currentValue.schemaName;
        queryName = currentValue.queryName;
        viewName = currentValue.viewName;
        columnName = currentValue.columnName;
        folderName = currentValue.folderName;
        urlTop = currentValue.urlTop;
        urlBottom = currentValue.urlBottom;
        isChoiceListQuery = currentValue.choiceListQuery;
        initialValues = [schemaName, queryName, viewName, columnName];

    }

    LABKEY.Query.getSchemas({
        successCallback: function(schemasInfo) { populateSchemas(schemaCombo, queryCombo, viewCombo, schemasInfo, includeSchema, columnCombo); }
    });

    var labelStyle = 'border-bottom:1px solid #AAAAAA;margin:3px';

    var urlBottomField = new Ext.form.TextField({
        name: 'url',
        fieldLabel: 'URL',
        labelSeparator: '',
        value: urlBottom,
        width : 200,
        labelWidth: 50
    });

    var formSQV = new Ext.form.FormPanel({
        padding: 5,
        layout: 'form',

        items: [folderCombo, schemaCombo, queryCombo, viewCombo, columnCombo, urlBottomField]});

    var titleField = new Ext.form.TextField({
        name: 'title',
        fieldLabel: 'Title',
        labelSeparator: '',
        value: title,
        width : 400,
        labelWidth: 50
    });

    var urlField = new Ext.form.TextField({
        name: 'url',
        fieldLabel: 'URL',
        labelSeparator: '',
        value: urlTop,
        width : 400,
        labelWidth: 50
    });

    var rootFolderCombo = createRootFolderCombo();
    var folderTypesCombo = createFolderTypesCombo();

    var formFolders = new Ext.form.FormPanel({
        padding: 5,
        timeout: Ext.Ajax.timeout,
        hidden: false,
        items: [rootFolderCombo, folderTypesCombo]
    });

    var formMenuSelectPanel = new Ext.Panel({
        padding: 5,
        layout: 'hbox',
        items: [
            {
            xtype: 'label',
            fieldLabel: 'Menu Items' ,
            flex: 1
            },
            {
                xtype: 'radio',
                boxLabel: 'Create from List or Query',
                name: 'menuSelect',
                inputValue: 'list',
                checked: isChoiceListQuery,
                flex: 2,
                check: function(checkbox, checked)
                {
                    if (checked)
                    {
                        formSQV.hidden = false;
                        formWinPanel.doLayout();
                    }
                },
                id: 'radio2'
            },{
                xtype: 'radio',
                boxLabel: 'Folders',
                name: 'menuSelect',
                inputValue: 'folders',
                checked: !isChoiceListQuery,
                flex: 3,
                check: function(checkbox, checked)
                {
                    if (checked)
                    {
                        formFolders.hidden = false;
                        formWinPanel.doLayout();
                    }
                },
                id: 'radio3'
            }
        ]
    });

    var formWinPanel = new Ext.form.FormPanel({
        padding: 5,
        timeout: Ext.Ajax.timeout,
        items: [titleField, urlField, formMenuSelectPanel, formSQV, formFolders]});

    var win = new Ext.form.FormPanel({
//        title: 'Customize Menu',
        renderTo: renderToDiv,
//        layout:'fit',
        border: false,
        width: 1000,
//        height: 600,
//        closeAction:'close',
//        modal: true,
        items: formWinPanel,
        resizable: true,
        buttons: [{
            text: 'Submit',
            id: 'btn_submit',
            handler: function(){
                var form = formSQV.getForm();

                if (form && !form.isValid())
                {
                    Ext.Msg.alert(title, 'Please complete all required fields.');
                    return false;
                }

                var viewName = "";
                var viewRecord = viewCombo.getValue();
                if (viewRecord != defaultViewLabel)
                    viewName = viewRecord;

                var isChoiceListQuery = formMenuSelectPanel.items.items[1].checked;
                submitFunction({
                    schemaName : schemaCombo.getValue(),
                    queryName : queryCombo.getValue(),
                    viewName: viewName,
                    folderName: folderCombo.getValue(),
                    columnName: columnCombo.getValue(),
                    title: titleField.getValue(),
                    urlTop: urlField.getValue(),
                    urlBottom: urlBottomField.getValue(),
                    choiceListQuery: isChoiceListQuery,
                    rootFolder: rootFolderCombo.getValue(),
                    folderTypes: folderTypesCombo.getValue(),
                    includeAllDescendants: true                                // TODO
                });
            }
        },{
            text: 'Cancel',
            id: 'btn_cancel',
            handler: function(){cancelFunction(); formWinPanel.doLayout();}
        }]
//        bbar: [{ xtype: 'tbtext', text: '', id:'statusTxt'}]
    });

    return win;
}