/*
 * Copyright (c) 2010-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.requiresExt4ClientAPI();
LABKEY.requiresCss('/extWidgets/Ext4DetailsPanel.css');
Ext4.namespace('LABKEY.ext');

/*
config:

titleField
sectionTitle
multiToGrid
queryConfig
qwpConfig

 */

Ext4.define('LABKEY.ext.DetailsPanel', {
    extend: 'Ext.panel.Panel',
    alias: 'widget.labkey-detailspanel',
    initComponent: function(){
        var params = {schemaName: this.schemaName, 'query.queryName': this.queryName};
        params['query.' + this.keyFieldName + '~eq'] = this.keyValue;
        var gridURL = LABKEY.ActionURL.buildURL('query', 'executeQuery', null, params);

        Ext4.apply(this, {
            items: [{html: 'Loading...'}],
            bodyStyle: 'padding:5px',
            autoHeight: true,
            bodyBorder: false,
            border: false,
            frame: false,
            defaults: {
                border: false
            },
            buttonAlign: 'left',
            buttons: [{
                text: 'Show Grid',
                href: gridURL
            }]
        });

        this.callParent(arguments);

        var required = ['schemaName', 'queryName'];
        for (var i=0;i<required.length;i++){
            if (!this[required[i]]){
                alert('Required: '+required[i]);
                return;
            }
        }

        this.queryConfig = this.queryConfig || {};

        Ext4.applyIf(this.queryConfig, {
            containerPath: this.containerPath,
            containerFilter: this.containerFilter,
            queryName: this.queryName,
            schemaName: this.schemaName,
            viewName: this.viewName,
            columns: this.columns,
            metadataDefaults: this.metadataDefaults,
            metadata: this.metadata,
            filterArray: this.filterArray,
            listeners: {
                load: this.loadQuery,
                scope: this
            },
            failure: LABKEY.Utils.onError,
            scope: this,
            maxRows: 100,
            autoLoad: true
        });

        this.store = Ext4.create('LABKEY.ext4.Store', this.queryConfig);
    },

    loadQuery: function(store){
        if(!this.rendered){
            this.on('render', this.loadQuery, this, {single: true});
            return;
        }

        this.removeAll();

        if (!this.store.getCount()){
            this.add({html: 'No records found'});
            this.doLayout();
            return;
        }

        if (this.store.getCount() > 1 && this.multiToGrid){
            //NOTE: would be cleaner just to drop an Ext grid in here
            Ext.applyIf(this.queryConfig, {
                allowChooseQuery: false,
                allowChooseView: true,
                showInsertNewButton: false,
                showDeleteButton: false,
                showDetailsColumn: true,
                showUpdateColumn: false,
                showRecordSelectors: true,
                buttonBarPosition: 'top',
                title: this.sectionTitle,
                timeout: 0
            });

            if (this.viewName){
                this.queryConfig.viewName = this.viewName;
            }

            if(this.qwpConfig){
                Ext.apply(this.queryConfig, this.qwpConfig);
            }

            delete this.queryConfig.listeners;
            var target = this.add({tag: 'span'});
            this.doLayout();
            new LABKEY.QueryWebPart(this.queryConfig).render(target.id);
            return;
        }

        this.store.each(function(rec, idx){
            var fields = this.store.getFields();
            var panel = {
                xtype: 'form',
                title: Ext4.isDefined(this.sectionTitle) ?  this.sectionTitle : 'Details',
                items: [],
                style: 'margin-bottom:10px',
                border: true,
                bodyStyle: 'padding:5px',
                autoHeight: true,
                fieldDefaults: {
                    labelWidth: 150
                }
            };
            fields.each(function(field){
                if (LABKEY.ext.MetaHelper.shouldShowInDetailsView(field)){
                    var value;

                    if(rec.raw && rec.raw[field.name]){
                        value = rec.raw[field.name].displayValue || rec.get(field.name);
                        if(value && field.jsonType == 'date'){
                            var format = 'Y-m-d h:m A'; //NOTE: java date formats do not necessarily match Ext
                            value = value.format(format);
                        }

                        if(rec.raw[field.name].url)
                            value = '<a href="'+rec.raw[field.name].url+'" target="new">'+value+'</a>';
                    }
                    else
                        value = rec.get(field.name);

                    panel.items.push({
                        fieldLabel: field.label || field.caption || field.name,
                        xtype: 'displayfield',
                        fieldCls: 'labkey-display-field',
                        width: 600,
                        value: value
                    });

                    //NOTE: because this panel will render multiple rows as multiple forms, we provide a mechanism to append an identifier field
                    if (this.titleField == field.name){
                        panel.title += ': '+value;
                    }
                }
            }, this);

            this.add(panel)
        }, this);

        this.doLayout();
    }
});
