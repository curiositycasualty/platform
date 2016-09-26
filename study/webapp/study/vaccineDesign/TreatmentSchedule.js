Ext4.define('LABKEY.VaccineDesign.TreatmentSchedulePanel', {
    extend : 'Ext.panel.Panel',

    border : false,

    bodyStyle : 'background-color: transparent;',

    minWidth: 1400,

    disableEdit : true,

    returnURL : null,

    initComponent : function()
    {
        this.items = [
            this.getTreatmentsGrid()
        ];

        this.callParent();
    },

    getTreatmentsGrid : function()
    {
        if (!this.treatmentsGrid)
        {
            this.treatmentsGrid = Ext4.create('LABKEY.VaccineDesign.TreatmentsGrid', {
                disableEdit: this.disableEdit
            });

            this.treatmentsGrid.on('dirtychange', function() { this.getSaveButton().enable(); }, this);

            // Note: since we need the data from the treatment grid, don't add this.getTreatmentScheduleGrid() until the treatment grid store has loaded
            this.treatmentsGrid.on('loadcomplete', this.onTreatmentGridLoadComplete, this, {single: true});
        }

        return this.treatmentsGrid;
    },

    onTreatmentGridLoadComplete : function()
    {
        this.add(this.getTreatmentScheduleGrid());
        this.add(this.getButtonBar());

        // since a treatment label change needs to be reflected in the treatment schedule grid, force a refresh there
        this.getTreatmentsGrid().on('celledited', function(){
            this.getTreatmentScheduleGrid().refresh();
        }, this);

        // removing a treatment row needs to also remove any visit mappings for that treatment
        this.getTreatmentsGrid().on('beforerowdeleted', function(grid, record){
            this.getTreatmentScheduleGrid().removeTreatmentUsages(record.get('RowId'));
        }, this);
    },

    getTreatmentScheduleGrid : function()
    {
        if (!this.treatmentScheduleGrid)
        {
            this.treatmentScheduleGrid = Ext4.create('LABKEY.VaccineDesign.TreatmentScheduleGrid', {
                padding: '20px 0',
                disableEdit: this.disableEdit,
                subjectNoun: this.subjectNoun,
                visitNoun: this.visitNoun
            });

            this.treatmentScheduleGrid.on('dirtychange', function() { this.getSaveButton().enable(); }, this);
        }

        return this.treatmentScheduleGrid;
    },

    getButtonBar : function()
    {
        if (!this.buttonBar)
        {
            this.buttonBar = Ext4.create('Ext.toolbar.Toolbar', {
                dock: 'bottom',
                ui: 'footer',
                padding: 0,
                style : 'background-color: transparent;',
                defaults: {width: 75},
                items: [this.getSaveButton(), this.getCancelButton()]
            });
        }

        return this.buttonBar;
    },

    getSaveButton : function()
    {
        if (!this.saveButton)
        {
            this.saveButton = Ext4.create('Ext.button.Button', {
                text: 'Save',
                disabled: true,
                hidden: this.disableEdit,
                handler: this.saveTreatmentSchedule,
                scope: this
            });
        }

        return this.saveButton;
    },

    getCancelButton : function()
    {
        if (!this.cancelButton)
        {
            this.cancelButton = Ext4.create('Ext.button.Button', {
                margin: this.disableEdit ? 0 : '0 0 0 10px',
                text: this.disableEdit ? 'Done' : 'Cancel',
                handler: this.goToReturnURL,
                scope: this
            });
        }

        return this.cancelButton;
    },

    saveTreatmentSchedule : function()
    {
        this.getEl().mask('Saving...');

        var treatments = [], cohorts = [],
            index = 0, errorMsg = [];

        Ext4.each(this.getTreatmentsGrid().getStore().getRange(), function(record)
        {
            var recData = Ext4.clone(record.data);
            index++;

            // drop and empty immunogen or adjuvant rows that were just added
            recData['Products'] = [];
            Ext4.each(recData['Immunogen'], function(immunogen)
            {
                if (Ext4.isDefined(immunogen['RowId']) || LABKEY.VaccineDesign.Utils.objectHasData(immunogen))
                    recData['Products'].push(immunogen);
            }, this);
            Ext4.each(recData['Adjuvant'], function(adjuvant)
            {
                if (Ext4.isDefined(adjuvant['RowId']) || LABKEY.VaccineDesign.Utils.objectHasData(adjuvant))
                    recData['Products'].push(adjuvant);
            }, this);

            // drop any empty rows that were just added
            var hasData = recData['Label'] != '' || recData['Description'] != '' || recData['Products'].length > 0;
            if (Ext4.isDefined(recData['RowId']) || hasData)
            {
                var treatmentLabel = recData['Label'] != '' ? '\'' + recData['Label'] + '\'' : index;

                // validation: treatment must have at least one immunogen or adjuvant, no duplicate immunogens/adjuvants for a treatment
                var treatmentProductIds = Ext4.Array.clean(Ext4.Array.pluck(recData['Products'], 'ProductId'));
                if (recData['Products'].length == 0)
                    errorMsg.push('Treatment ' + treatmentLabel + ' must have at least one immunogen or adjuvant defined.');
                else if (treatmentProductIds.length != Ext4.Array.unique(treatmentProductIds).length)
                    errorMsg.push('Treatment ' + treatmentLabel + ' contains a duplicate immunogen or adjuvant.');
                else
                    treatments.push(recData);
            }
        }, this);

        if (errorMsg.length > 0)
        {
            this.onFailure(errorMsg.join('<br/>'));
        }
        else
        {
            Ext4.each(this.getTreatmentScheduleGrid().getStore().getRange(), function(record)
            {
                var recData = Ext4.clone(record.data);

                // drop any empty rows that were just added
                var hasData = recData['Label'] != '' || recData['SubjectCount'] != '' || recData['VisitMap'].length > 0;
                if (Ext4.isDefined(recData['RowId']) || hasData)
                {
                    cohorts.push(recData);
                }
            }, this);

            LABKEY.Ajax.request({
                url     : LABKEY.ActionURL.buildURL('study-design', 'updateTreatmentSchedule.api'),
                method  : 'POST',
                jsonData: {
                    treatments: treatments,
                    cohorts: cohorts
                },
                scope: this,
                success: function(response)
                {
                    var resp = Ext4.decode(response.responseText);
                    if (resp.success)
                        this.goToReturnURL();
                    else
                        this.onFailure();
                },
                failure: function(response)
                {
                    var resp = Ext4.decode(response.responseText);
                    this.onFailure(resp.exception);
                }
            });
        }
    },

    goToReturnURL : function()
    {
        window.location = this.returnURL;
    },

    onFailure : function(text)
    {
        Ext4.Msg.show({
            title: 'Error',
            msg: text || 'Unknown error occurred.',
            icon: Ext4.Msg.ERROR,
            buttons: Ext4.Msg.OK
        });

        this.getEl().unmask();
    }
});

Ext4.define('LABKEY.VaccineDesign.TreatmentsGrid', {
    extend : 'LABKEY.VaccineDesign.BaseDataView',

    mainTitle : 'Treatments',

    width: 1120,

    studyDesignQueryNames : ['StudyDesignRoutes', 'Product', 'DoseAndRoute'],

    //Override
    getStore : function()
    {
        if (!this.store)
        {
            this.store = Ext4.create('Ext.data.Store', {
                storeId : 'TreatmentsGridStore',
                model : 'LABKEY.VaccineDesign.Treatment',
                proxy: {
                    type: 'ajax',
                    url : LABKEY.ActionURL.buildURL("study-design", "getStudyTreatments", null, {splitByRole: true}),
                    reader: {
                        type: 'json',
                        root: 'treatments'
                    }
                },
                sorters: [{ property: 'RowId', direction: 'ASC' }],
                autoLoad: true
            });
        }

        return this.store;
    },

    //Override
    getColumnConfigs : function()
    {
        if (!this.columnConfigs)
        {
            this.columnConfigs = [{
                label: 'Label',
                width: 200,
                dataIndex: 'Label',
                required: true,
                editorType: 'Ext.form.field.Text',
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignTextConfig('Label', 190)
            },{
                label: 'Description',
                width: 200,
                dataIndex: 'Description',
                editorType: 'Ext.form.field.TextArea',
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignTextConfig('Description', 190, '95%')
            }, {
                label: 'Immunogens',
                width: 360,
                dataIndex: 'Immunogen',
                subgridConfig: {
                    columns: [{
                        label: 'Immunogen',
                        width: 200,
                        dataIndex: 'ProductId',
                        required: true,
                        queryName: 'Product',
                        editorType: 'LABKEY.ext4.ComboBox',
                        editorConfig : this.getProductEditor('Immunogen')
                    },{
                        label: 'Dose and Route',
                        width: 140,
                        dataIndex: 'DoseAndRoute',
                        queryName: 'DoseAndRoute',
                        editorType: 'LABKEY.ext4.ComboBox',
                        editorConfig: this.getDoseAndRouteEditor()
                    }]
                }
            }, {
                label: 'Adjuvants',
                width: 360,
                dataIndex: 'Adjuvant',
                subgridConfig: {
                    columns: [{
                        label: 'Adjuvant',
                        width: 200,
                        dataIndex: 'ProductId',
                        required: true,
                        queryName: 'Product',
                        editorType: 'LABKEY.ext4.ComboBox',
                        editorConfig: this.getProductEditor('Adjuvant')
                    },{
                        label: 'Dose and Route',
                        width: 140,
                        dataIndex: 'DoseAndRoute',
                        queryName: 'DoseAndRoute',
                        editorType: 'LABKEY.ext4.ComboBox',
                        editorConfig: this.getDoseAndRouteEditor()
                    }]
                }
            }];
        }

        return this.columnConfigs;
    },

    getProductEditor : function(roleName){

        var cfg = LABKEY.VaccineDesign.Utils.getStudyDesignComboConfig('ProductId', 190, 'Product',
                LABKEY.Filter.create('Role', roleName), 'Label', 'RowId');

        cfg.listeners = {
            scope: this,
            change : function(cmp, value) {
                // clear out (if any) value for the dose and route field
                if (this.cellEditField){

                    var index = this.cellEditField.storeIndex,
                        record = this.getStore().getAt(index),
                        outerDataIndex = this.cellEditField.outerDataIndex,
                        subgridIndex = Number(this.cellEditField.subgridIndex);

                    this.updateSubgridRecordValue(record, outerDataIndex, subgridIndex, 'DoseAndRoute', '');
                }
            }
        };
        return cfg;
    },

    getDoseAndRouteEditor : function(){

        var cfg = LABKEY.VaccineDesign.Utils.getStudyDesignComboConfig('DoseAndRoute', 130, 'DoseAndRoute',
                undefined, 'label', 'label');
        cfg.listeners = {
            scope: this,
            render : function(cmp) {
                var store = Ext4.getStore('DoseAndRoute');
                if (store) {
                    var index = cmp.storeIndex,
                        record = this.getStore().getAt(index),
                        outerDataIndex = cmp.outerDataIndex,
                        subgridIndex = Number(cmp.subgridIndex);

                    // get the product id to filter on
                    var productId = record.get(outerDataIndex)[subgridIndex]['ProductId'];
                    if (productId){
                        store.filterArray = [LABKEY.Filter.create('ProductId', productId)];
                        store.load();
                    }
                }
            }
        };
        return cfg;
    },

    //Override
    getNewModelInstance : function()
    {
        return LABKEY.VaccineDesign.Treatment.create({
            RowId: Ext4.id() // need to generate an id so that the treatment schedule grid can use it
        });
    },

    //Override
    getDeleteConfirmationMsg : function()
    {
        return 'Are you sure you want to delete the selected treatment?<br/><br/>'
            + 'Note: this will also delete any usages of this treatment record in the Treatment Schedule grid below.';
    },

    //Override
    updateSubgridRecordValue : function(record, outerDataIndex, subgridIndex, fieldName, newValue)
    {
        var preProductIds = Ext4.Array.pluck(record.get('Immunogen'), 'ProductId').concat(Ext4.Array.pluck(record.get('Adjuvant'), 'ProductId'));

        this.callParent([record, outerDataIndex, subgridIndex, fieldName, newValue]);

        // auto populate the treatment label if the user has not already entered a value
        if (fieldName == 'ProductId')
            this.populateTreatmentLabel(record, preProductIds);
    },

    //Override
    removeSubgridRecord : function(target, record)
    {
        var preProductIds = Ext4.Array.pluck(record.get('Immunogen'), 'ProductId').concat(Ext4.Array.pluck(record.get('Adjuvant'), 'ProductId'));
        this.callParent([target, record]);
        this.populateTreatmentLabel(record, preProductIds);
    },

    populateTreatmentLabel : function(record, preProductIds)
    {
        var currentLabel = record.get('Label');
        if (currentLabel == '' || currentLabel == this.getLabelFromProductIds(preProductIds))
        {
            var postProductIds = Ext4.Array.pluck(record.get('Immunogen'), 'ProductId').concat(Ext4.Array.pluck(record.get('Adjuvant'), 'ProductId'));
            record.set('Label', this.getLabelFromProductIds(postProductIds));
            this.fireEvent('celledited');
        }
    },

    getLabelFromProductIds : function(productIdsArr)
    {
        var labelArr = [];

        if (Ext4.isArray(productIdsArr))
        {
            Ext4.each(productIdsArr, function(productId){
                if (productId != undefined || productId != null)
                    labelArr.push(LABKEY.VaccineDesign.Utils.getLabelFromStore('Product', productId));
            });
        }

        return labelArr.join(' | ');
    }
});

Ext4.define('LABKEY.VaccineDesign.TreatmentScheduleGrid', {
    extend : 'LABKEY.VaccineDesign.BaseDataView',

    mainTitle : 'Treatment Schedule',

    width: 330,

    //studyDesignQueryNames : ['Visit'],

    subjectNoun : 'Subject',

    visitNoun : 'Visit',

    getStore : function()
    {
        if (!this.store)
        {
            this.store = Ext4.create('Ext.data.Store', {
                model : 'LABKEY.VaccineDesign.Cohort',
                sorters: [{ property: 'RowId', direction: 'ASC' }]
            });

            this.queryStudyTreatmentSchedule();
        }

        return this.store;
    },

    queryStudyTreatmentSchedule : function()
    {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('study-design', 'getStudyTreatmentSchedule', null, {splitByRole: true}),
            method: 'GET',
            scope: this,
            success: function (response)
            {
                var o = Ext4.decode(response.responseText);
                if (o.success)
                {
                    this.getVisitStore(o['visits']);
                    this.getStore().loadData(o['cohorts']);
                    this.getStore().fireEvent('load', this.getStore());
                }
            }
        });
    },

    getVisitStore : function(data)
    {
        if (!this.visitStore)
        {
            this.visitStore = Ext4.create('Ext.data.Store', {
                model : 'LABKEY.VaccineDesign.Visit',
                data : data
            });
        }

        return this.visitStore;
    },

    getTreatmentsStore : function()
    {
        if (!this.treatmentsStore)
        {
            this.treatmentsStore = Ext4.getStore('TreatmentsGridStore');
        }

        return this.treatmentsStore;
    },

    getVisitColumnConfigs : function()
    {
        var visitConfigs = [];

        Ext4.each(this.getVisitStore().getRange(), function(visit)
        {
            if (visit.get('Included'))
            {
                visitConfigs.push({
                    label: visit.get('Label') || (this.visitNoun + visit.get('RowId')),
                    width: 150,
                    dataIndex: 'VisitMap',
                    dataIndexArrFilterProp: 'VisitId',
                    dataIndexArrFilterValue: visit.get('RowId'),
                    dataIndexArrValue: 'TreatmentId',
                    lookupStoreId: 'TreatmentsGridStore',
                    editorType: 'LABKEY.ext4.ComboBox',
                    editorConfig: this.getTreatmentComboBoxConfig
                });
            }
        }, this);

        return visitConfigs;
    },

    getTreatmentComboBoxConfig : function()
    {
        return {
            hideFieldLabel: true,
            name: 'VisitMap',
            width: 140,
            forceSelection : false, // allow usage of inactive types
            editable : false,
            queryMode : 'local',
            displayField : 'Label',
            valueField : 'RowId',
            store : this.getNewTreatmentComboStore()
        };
    },

    getNewTreatmentComboStore : function()
    {
        // need to create a new store each time since we need to add a [none] option and include any new treatment records
        var data = [{RowId: null, Label: '[none'}];
        Ext4.each(this.getTreatmentsStore().getRange(), function(record)
        {
            data.push(Ext4.clone(record.data));
        }, this);

        return Ext4.create('Ext.data.Store', {
            fields: ['RowId', 'Label'],
            data: data
        });
    },

    removeTreatmentUsages : function(treatmentId)
    {
        this.getStore().suspendEvents();
        Ext4.each(this.getStore().getRange(), function(record)
        {
            var newVisitMapArr = Ext4.Array.filter(record.get('VisitMap'), function(item){ return item.TreatmentId != treatmentId; });
            record.set('VisitMap', newVisitMapArr);
        }, this);
        this.getStore().resumeEvents();

        this.refresh(true);
    },

    //Override
    getColumnConfigs : function()
    {
        if (!this.columnConfigs)
        {
            var columnConfigs = [{
                label: 'Group / Cohort',
                width: 200,
                dataIndex: 'Label',
                required: true,
                editorType: 'Ext.form.field.Text',
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignTextConfig('Label', 190)
            },{
                label: this.subjectNoun + ' Count',
                width: 130,
                dataIndex: 'SubjectCount',
                editorType: 'Ext.form.field.Number',
                editorConfig: LABKEY.VaccineDesign.Utils.getStudyDesignNumberConfig('SubjectCount', 120)
            }];

            var visitConfigs = this.getVisitColumnConfigs();

            // update the width based on the number of visit columns
            this.setWidth((visitConfigs.length * 150) + 330);

            this.columnConfigs = columnConfigs.concat(visitConfigs);
        }

        return this.columnConfigs;
    },

    //Override
    getNewModelInstance : function()
    {
        return LABKEY.VaccineDesign.Cohort.create();
    },

    //Override
    getDeleteConfirmationMsg : function()
    {
        return 'Are you sure you want to delete the selected group / cohort and its associated treatment / visit mapping records?';
    },

    //Override
    getCurrentCellValue : function(column, record, dataIndex, outerDataIndex, subgridIndex)
    {
        var value = this.callParent([column, record, dataIndex, outerDataIndex, subgridIndex]);

        if (Ext4.isArray(value))
        {
            var matchingIndex = LABKEY.VaccineDesign.Utils.getMatchingRowIndexFromArray(value, column.dataIndexArrFilterProp, column.dataIndexArrFilterValue);
            if (matchingIndex > -1)
                return value[matchingIndex][column.dataIndexArrValue];
            else
                return null;
        }

        return value;
    },

    //Override
    updateStoreRecordValue : function(record, column, newValue)
    {
        // special case for editing the value of one of the pivot visit columns
        if (column.dataIndex == 'VisitMap')
        {
            var visitMapArr = record.get(column.dataIndex),
                matchingIndex = LABKEY.VaccineDesign.Utils.getMatchingRowIndexFromArray(visitMapArr, column.dataIndexArrFilterProp, column.dataIndexArrFilterValue);

            if (matchingIndex > -1)
            {
                if (newValue != null)
                    visitMapArr[matchingIndex][column.dataIndexArrValue] = newValue;
                else
                    Ext4.Array.splice(visitMapArr, matchingIndex, 1);
            }
            else if (newValue != null)
            {
                visitMapArr.push({
                    CohortId: record.get('RowId'),
                    VisitId: column.dataIndexArrFilterValue,
                    TreatmentId: newValue
                });
            }
        }
        else
        {
            this.callParent([record, column, newValue]);
        }
    },

    //Override
    getAddNewRowTpl : function(columns, dataIndex)
    {
        var tplArr = [];

        if (!this.disableEdit)
        {
            tplArr.push('<tr>');
            tplArr.push('<td class="cell-display">&nbsp;</td>');
            tplArr.push('<td class="cell-display action" colspan="' + columns.length + '">');
            tplArr.push('<i class="' + this.ADD_ICON_CLS + ' add-new-row"> Add new row</i>&nbsp;&nbsp;&nbsp;');
            tplArr.push('<i class="' + this.ADD_ICON_CLS + ' add-visit-column"> Add new ' + this.visitNoun.toLowerCase() + '</i>');
            tplArr.push('</td>');
            tplArr.push('</tr>');
        }

        return tplArr;
    },

    //Override
    attachAddRowListeners : function(view)
    {
        this.callParent([view]);

        var addIconEls = Ext4.DomQuery.select('i.add-visit-column', view.getEl().dom);

        Ext4.each(addIconEls, function(addIconEl)
        {
            Ext4.get(addIconEl).on('click', this.addNewVisitColumn, this);
        }, this);
    },

    addNewVisitColumn : function()
    {
        var win = Ext4.create('LABKEY.VaccineDesign.VisitWindow', {
            title: 'Add ' + this.visitNoun,
            visitNoun: this.visitNoun,
            visitStore: this.getVisitStore(),
            listeners: {
                scope: this,
                closewindow: function(){
                    win.close();
                },
                selectexistingvisit: function(w, visitId){
                    win.close();

                    // set the selected visit to be included
                    this.getVisitStore().findRecord('RowId', visitId).set('Included', true);

                    this.updateDataViewTemplate();
                },
                newvisitcreated: function(w, newVisitData){
                    win.close();

                    // add the new visit to the store
                    this.getVisitStore().add(LABKEY.VaccineDesign.Visit.create(newVisitData));

                    this.updateDataViewTemplate();
                }
            }
        });

        win.show();
    },

    updateDataViewTemplate : function()
    {
        // explicitly clear the column configs so the new visit column will be added
        this.columnConfigs = null;
        this.getDataView().setTemplate(this.getDataViewTpl());
    }
});