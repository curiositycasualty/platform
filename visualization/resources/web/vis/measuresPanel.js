/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext.namespace("LABKEY.vis");

Ext.QuickTips.init();

LABKEY.vis.MeasuresPanel = Ext.extend(Ext.Panel, {

    constructor : function(config){

        // keep the list of toolbar actions and selections
        this.tbarActions = [];
        this.axisMap = {};              // map of name to axis info
        this.addEvents(
            'measureChanged'
        );
        Ext.apply(this, config, {storeUrl : LABKEY.ActionURL.buildURL('visualization', 'getMeasures'), isDateAxis : false});

        LABKEY.vis.MeasuresPanel.superclass.constructor.call(this, config);

    },

    initComponent : function() {
        this.layout = 'border';
        this.border = false;
        this.flex = 1;

        this.items = [
            this.createMeasuresFormPanel(),
            this.createMeasuresListPanel()
        ];

        var loaded = false;

        // Keep track of whether the store has been loaded so we know whether we need to
        // mask the popup:
        this.measuresStore.on('load', function(){
            loaded = true;
            //Prefilter list
            var datasetName = LABKEY.ActionURL.getParameter("queryName");
            if (datasetName)
            {
                this.searchBox.setValue(LABKEY.ActionURL.getParameter("queryName"));
                this.searchBox.focus(true, 100);
                this.filterMeasures(datasetName);
            }

        }, this);

        // load the store the first time after this component has rendered
        this.on('afterrender', function(cmp){
            var filter = [LABKEY.Visualization.Filter.create({schemaName: 'study'})];

            if (this.selectedMeasure)
            {
                filter = [LABKEY.Visualization.Filter.create({schemaName: this.selectedMeasure.schemaName,
                        queryName: this.selectedMeasure.queryName})];
            }
            this.measuresStore.load({params:{filters: filter, dateMeasures: this.isDateAxis}});
        }, this);

        // Show the mask after the component size has been determined, as long as the
        // data is still loading:
        this.on('afterlayout', function() {
            if (!loaded)
                this.getEl().mask("loading measures...", "x-mask-loading");
        });

        LABKEY.vis.MeasuresPanel.superclass.initComponent.call(this);
    },

    createMeasuresListPanel : function() {

        // define a store to wrap the data measures
        this.measuresStore = new Ext.data.Store({
            autoLoad: false,
            reader: new Ext.data.JsonReader({
                root:'measures', idProperty:'id'},
                [{name: 'id'}, {name:'name'},{name:'label'},{name:'description'}, {name:'isUserDefined'}, {name:'queryName'}, {name:'schemaName'}, {name:'type'}]
            ),
            proxy: new Ext.data.HttpProxy({
                method: 'GET',
                url : this.storeUrl}),
            remoteSort: false,
            sortInfo: {
                field: 'queryName',
                direction: 'ASC'
            }
        });
        this.measuresStore.on('load', function(){
            this.getEl().unmask();
        }, this);
        this.measuresStore.on('exception', function(proxy, type, action, options, resp){
            LABKEY.Utils.displayAjaxErrorResponse(resp, options);
            this.getEl().unmask();
        }, this);
        
        this.listView = new Ext.list.ListView({
            store: this.measuresStore,
            flex: 1,
            singleSelect: true,
            columns: [
                {header:'Dataset', dataIndex:'queryName', width: .4},
                {header:'Measure', dataIndex:'label', width:.25},
                {header:'Description', dataIndex:'description', cls : 'normal-wrap'}
            ]
        });

        // enable disable toolbar actions on selection change
        this.listView.on('selectionchange', this.onListViewSelectionChanged, this);

        this.searchBox = new Ext.form.TextField({
            fieldLabel: 'Search data types',
            width: 200,
            enableKeyEvents: true,
            emptyText : 'Search',
            listeners : {
                // filter the listview using both the label and category names
                keyup : function(cmp, e){ this.filterMeasures(cmp.getValue()); },
                scope : this
            }
        });

        var tbarItems = [{xtype:'tbspacer'}];

        tbarItems.push(this.searchBox);

        // create a toolbar button for each of the axis types
        if (this.hasBtnSelection) {

            for (var i=0; i < this.axis.length; i++)
            {
                var axis = this.axis[i];

                if (this.axisMap[axis.name]) {
                    var action = new Ext.Action({iconCls: 'iconUp', text: 'Add to ' + axis.name,
                        handler: this.onListViewBtnClicked,
                        scope: this, disabled: true,
                        tooltip: 'Adds the selected measurement into the axis field on the right',
                        labelId: this.axisMap[axis.name].labelId, axisId: axis.name});
                    this.tbarActions.push(action);
                    tbarItems.push(action);
                }
            }
        }

        var items = [];
        items.push(new Ext.Toolbar({
            style : 'padding: 5px 2px',
            items: tbarItems
        }));
        items.push(this.listView);

        var panel = new Ext.Panel({
            region: 'center',
            layout: 'vbox',
            layoutConfig: {
                align: 'stretch',
                pack: 'start'
            },
            items: items
        });

        return panel;
    },

    filterMeasures : function (txt) {
        if (txt) {
            this.measuresStore.filter([{
                fn: function(record){
                        var tester = new RegExp(Ext.escapeRe(txt), 'i');
                        return tester.test(record.data.label + record.data.queryName + record.data.description);
                }
            }]);
        }
        else
            this.measuresStore.clearFilter();
    },

    onListViewSelectionChanged : function(cmp, selections) {
        if (this.hasBtnSelection)
        {
            var disabled = selections.length == 0;

            for (var i=0; i < this.tbarActions.length; i++)
                this.tbarActions[i].setDisabled(disabled);
        }
        else
            this.setTextFieldSelection(this.axisId);
    },

    onListViewBtnClicked : function(btn, e) {

        if (this.listView)
            this.setTextFieldSelection(btn.initialConfig.axisId);
    },

    setTextFieldSelection : function(axisId) {

        var selection = this.listView.getSelectedRecords();
        if (selection.length == 1)
        {
            var rec = selection[0];
            var textField = Ext.getCmp(this.axisMap[axisId].labelId);
            if (textField) {
                textField.setValue(rec.data.queryName + ' : ' + rec.data.label);
                this.fireEvent('measureChanged', axisId, rec.data);
            }
        }
    },

    createMeasuresFormPanel : function() {

        var items = [];

        for (var i=0; i < this.axis.length; i++)
        {
            var axis = this.axis[i];

            if (this.isDateAxis && !axis.timeAxis)
            {
                // if we are picking dates, constrain the date columns to the selected y-axis query name
                if (this.measures)
                    this.selectedMeasure = this.measures[axis.name];

                continue;
            }

            if (!this.isDateAxis && axis.timeAxis)
                continue;

            var field = new Ext.form.DisplayField({
                width:500,
                fieldLabel: axis.label,
                labelStyle: {
                    style: 'italic'
                }
            });

            // stash the textfield id so we can update it later from the listview
            this.axisMap[axis.name] = {labelId: field.id};
            this.axisId = axis.name;

            items.push(field);
        }

        // if we have more than one axis, use a tbar button selection model
        this.hasBtnSelection = items.length > 1;

        var panel = new Ext.form.FormPanel({
            labelWidth: 175,
            bodyStyle:'padding:25px;',
            region: 'north',
            items: items
        });

        return panel;
    }
});

LABKEY.vis.ZeroDatePanel = Ext.extend(LABKEY.vis.MeasuresPanel, {

    constructor : function(config) {

        Ext.apply(config, {storeUrl: LABKEY.ActionURL.buildURL("visualization", "getZeroDate")});
        LABKEY.vis.ZeroDatePanel.superclass.constructor.call(this, config);

        this.addEvents(
            'timeOptionChanged'
        );
    },

    createMeasuresFormPanel : function() {

        var items = [];

        var field = new Ext.form.TextField({disabled: true,
            emptyText:'select from the list view',
            width:500,
            fieldLabel: 'Measure time from'
        });

        // stash the textfield id so we can update it later from the listview
        this.axisMap['zeroDate'] = {labelId: field.id};
        this.axisId = 'zeroDate';

        items.push(field);

        var dateIntervals = new Ext.data.ArrayStore({
            fields: ['id','value'],
            data: [
                ['days', 'Days'],
                ['weeks', 'Weeks'],
                ['months', 'Months'],
                ['years', 'Years']]
        });

        var dateCombo = new Ext.form.ComboBox({
            fieldLabel: 'Display time progression as:',
            labelSeparator: '',
            store: dateIntervals,
            forceSelection:true,
            triggerAction:'all',
            value: 'days',
            allowBlank: false,
            valueField:'id',
            displayField:'value',
            mode:'local',
            axisId: 'zeroDate',
            emptyText:'Choose a interval value...'
        });
        dateCombo.on('change', function(cmp, newVal, oldVal){
            this.fireEvent('timeOptionChanged', cmp.initialConfig.axisId, newVal);
        }, this);

        items.push(dateCombo);

        var panel = new Ext.form.FormPanel({
            labelWidth: 200,
            bodyStyle:'padding:25px;',
            region: 'north',
            items: items
        });

        return panel;
    }
});

