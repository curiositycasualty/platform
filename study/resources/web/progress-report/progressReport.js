/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * Main panel for the NAb QC interface.
 */
Ext4.QuickTips.init();

Ext4.define('LABKEY.ext4.AssayStatusPanel', {

    extend: 'Ext.panel.Panel',

    border: false,

    header: false,

    alias: 'widget.labkey-assay-status',

    padding: 10,

    constructor: function (config) {
        this.callParent([config]);
    },

    initComponent: function () {
        this.items = [];
        this.items.push(this.getStatusPanel());

        this.callParent(arguments);
    },

    getStatusPanel : function(){

        if (!this.statusPanel){

            this.statusPanel = Ext4.create('Ext.panel.Panel', {
                border  : false,
                frame   : false,
                flex    : 1.2,
                tpl     : this.getTemplate(),
                data    : {
                    name    : this.name,
                    visits  : this.visits,
                    participants : this.participants
                }
            });

            this.visitLabelMap = {};
            var i=0;
            Ext4.each(this.visits, function(visit){

                this.visitLabelMap[visit] = this.visitLables[i++];
            }, this);
        }
        return this.statusPanel;
    },

    getTemplate : function () {

        var tpl = new Ext4.XTemplate(
                '<div><h3>{name:htmlEncode}</div>',
                '<div class="table-responsive"><table class="table progress-report" id="ProgressReport"><tr>',
                '<th>Participant</th>',
                '<tpl for="visits">',
                    '{[this.getVisitLabel(this, values)]}',
                '</tpl>',
                '</tr>',
                '<tpl for="participants">',
                    '<tr class="{[xindex % 2 === 1 ? "labkey-alternate-row" : "labkey-row"]}">',
                    '<td>{.}</td>',
                    '<tpl for="parent.visits">',
                        '{[this.renderCellValue(this, parent, values)]}',
                    '</tpl>',
                    '</tr>',
                '</tpl>',
                '</table></div>',
                {
                    getVisitLabel : function(cmp, visit){
                        var me = cmp.initialConfig.me;
                        return '<th>' + me.visitLabelMap[visit] + '</th>';
                    },
                    renderCellValue : function(cmp, ptid, visit)
                    {
                        var me = cmp.initialConfig.me;
                        var className = 'fa fa-circle-o';
                        var key = ptid + '|' + visit;

                        var mapCell = me.heatMap[key];
                        var tooltip = '';
                        if (mapCell){
                            className = mapCell['iconcls'];
                            tooltip = mapCell['tooltip'];
                        }
                        return '<td><span height="16px" data-qtip="' + tooltip + '" class="' + className + '"></span></td>';
                    },
                    me : this
                }
        );
        return tpl;
    }
});

Ext4.define('LABKEY.ext4.AssayProgressReport', {

    extend: 'Ext.panel.Panel',

    border: false,

    header : false,

    alias: 'widget.labkey-progress-report',

    padding: 10,

    constructor: function (config)
    {
        this.reports = [];
        this.callParent([config]);
    },

    componentIdFromAssay : function(assayname){
        return assayname + '-report';
    },

    assayFromComponentId : function(component){
        return component.substring(0, component.length - '-report'.length);
    },

    initComponent: function ()
    {
        this.items = [];
        var storeData = [{name : 'All', componentId : 'all'}];

        Ext4.each(this.assays, function(assay){

            var id = this.componentIdFromAssay(assay);
            this.reports.push(id);
            storeData.push({name : Ext4.String.htmlEncode(assay), componentId : id})
        }, this);

        var assayStore = Ext4.create('Ext.data.Store', {
            fields  : ['name', 'componentId'],
            data : storeData
        });

        this.items.push({
            xtype   : 'button',
            text    : 'Export to Excel',
            itemId  : 'export-btn',
            disabled : true,
            handler : this.exportAssays,
            margin  : '0 0, 10, 0',
            scope   : this
        });

        // legend and assay selector
        this.items.push({
            xtype   : 'panel',
            border  : false,
            frame   : false,
            items   : [{
                xtype   : 'combo',
                labelWidth   : 200,
                fieldLabel  : 'Assay Progress Reports',
                queryMode   : 'local',
                editable    : false,
                value       : 'all',
                displayField    : 'name',
                valueField      : 'componentId',
                forceSelction   : false,
                store   : assayStore,
                listeners : {
                    scope   : this,
                    change  : function(cmp, newValue) {
                        var exportBtn = this.getComponent('export-btn');
                        if (exportBtn)
                            exportBtn.setDisabled(newValue === 'all');
                        this.selectedAssay = newValue;
                        Ext4.each(this.reports, function(report){

                            var panel = this.getComponent(report);
                            if (panel){
                                panel.setVisible(newValue === 'all' || newValue === report);
                            }
                        }, this);
                    }
                }
            }]
        });

        this.items.push(this.getLegendPanel());

        // add the assays
        this.on('render', this.createAssayReports, this);
        this.callParent(arguments);
    },

    createAssayReports : function(){

        this.getEl().mask("Generating Assay Reports...");
        LABKEY.Ajax.request({
            url    : LABKEY.ActionURL.buildURL('study-reports', 'getAssayReportData.api'),
            method  : 'POST',
            jsonData  : {
                reportId : this.reportId
            },
            success : function(response){
                var o = Ext4.decode(response.responseText);
                this.getEl().unmask();

                if (o.success) {
                    var items = [];
                    this.assayData = o.assayData;
                    Ext4.each(this.assays, function(assay){

                        var data = this.assayData[assay];

                        if (data){
                            var id = this.componentIdFromAssay(assay);
                            this.reports.push(id);
                            items.push({
                                xtype   : 'labkey-assay-status',
                                itemId  : id,
                                name    : assay,
                                visits  : data['visits'],
                                visitLables : data['visitLabels'],
                                participants : data['participants'],
                                heatMap : data['heatMap']
                            });
                        }
                    }, this);

                    if (items.length > 0)
                        this.add(items);
                }
            },
            failure : function(response){
                this.getEl().unmask();
                Ext4.Msg.alert('Failure', Ext4.decode(response.responseText).exception);
            },
            scope : this
        });
    },

    getLegendPanel : function(){

        if (!this.legendPanel){

            var tpl = new Ext4.XTemplate(
                    '<div><h4>Legend</h4></div>',
                    '<table class="legend">',
                    '<tpl for=".">',
                        '<tpl if="xindex % 2 === 1"><tr></tpl>',
                        '<td><span height="16px" class="{icon-class}"></span></td><td>&nbsp;{label}</td>',
                        '<tpl if="xindex % 2 != 1"></tr></tpl>',
                    '</tpl>',
                    '</table>'
            );

            this.legendPanel = Ext4.create('Ext.panel.Panel', {
                border  : false,
                frame   : false,
                flex    : 1.2,
                tpl     : tpl,
                data    : this.legend
            });
        }
        return this.legendPanel;
    },

    exportAssays : function(){
        window.location = LABKEY.ActionURL.buildURL('study-reports', 'exportAssayProgressReport.view', null, {reportId: this.reportId, assayName : this.assayFromComponentId(this.selectedAssay)});
    }
});

