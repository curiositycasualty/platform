<%
    /*
     * Copyright (c) 2009-2015 LabKey Corporation
     *
     * Licensed under the Apache License, Version 2.0 (the "License");
     * you may not use this file except in compliance with the License.
     * You may obtain a copy of the License at
     *
     *     http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software>
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     */
%>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("clientapi/ext4"));
//        resources.add(ClientDependency.fromPath("extWidgets/Ext4FormPanel.js"));
        return resources;
    }
%>

<labkey:errors/>
<div id="form"></div>

<script type="text/javascript">

    Ext4.onReady(function() {
        Ext4.define('Schema', {
            extend: 'Ext.data.Model',
            fields: [
                {name: 'dataSourceDisplayName', type: 'string'},
                {name: 'schemaName', type: 'auto'}
            ]
        });

        Ext4.define('DataSource', {
            extend: 'Ext.data.Model',
            fields: [
                {name: 'dataSourceDisplayName', type: 'string'}
            ]
        });

        var schemaProxyConfig = {
            type: 'memory',
            reader: {
                type: 'json',
                root: 'schemas'
            }
        };

        var sourceSchemaStore = Ext4.create('Ext.data.Store', {
            model: 'Schema',
            proxy: schemaProxyConfig
        });

        var targetSchemaStore = Ext4.create('Ext.data.Store', {
            model: 'Schema',
            proxy: schemaProxyConfig
        });

        var dataSourceProxyConfig = {
            type: 'memory',
            reader: {
                type: 'json',
                root: 'schemas'
            }
        };

        var sourceDataSourceStore = Ext4.create('Ext.data.Store', {
            model: 'DataSource',
            proxy: dataSourceProxyConfig
        });

        var targetDataSourceStore = Ext4.create('Ext.data.Store', {
            model: 'DataSource',
            proxy: dataSourceProxyConfig
        });

        var createDataSourceFilter = function(value) {
            return Ext4.create('Ext.util.Filter', {
                filterFn: function(item) {
                    return item.dataSourceDisplayName = value;
                }
            });
        };

        var sourceDataSourceCombo = new Ext4.form.ComboBox({
            name: 'sourceDataSource',
            editable: false,
            fieldLabel: 'Source Data Source',
            store: sourceDataSourceStore,
            queryMode: 'local',
            displayField: 'dataSourceDisplayName',
            value: 'labkey',
            listeners: {
                scope: this,
                change: function(combo, newValue) {
                    sourceDataSourceStore.clearFilter(true);
                    sourceDataSourceStore.addFilter(createDataSourceFilter(newValue));
                }
            }
        });

        var sourceSchemaCombo = new Ext4.form.ComboBox({
            name: 'sourceSchema',
            editable: false,
            fieldLabel: 'Source Schema',
            store: sourceSchemaStore,
            queryMode: 'local',
            displayField: 'schemaName'
            // Store kind of pain here -> mainly wired up values
        });

        var targetDataSourceCombo = new Ext4.form.ComboBox({
            name: 'targetDataSource',
            editable: false,
            fieldLabel: 'Target Data Source',
            store: targetDataSourceStore,
            queryMode: 'local',
            displayField: 'dataSourceDisplayName',
            value: 'labkey',
            listeners: {
                scope: this,
                change: function(combo, newValue) {
                    sourceDataSourceStore.clearFilter(true);
                    sourceDataSourceStore.addFilter(createDataSourceFilter(newValue));
                }
            }
        });

        var targetSchemaCombo = new Ext4.form.ComboBox({
            name: 'targetSchema',
            editable: false,
            fieldLabel: 'Target Schema',
            store: targetSchemaStore,
            queryMode: 'local',
            displayField: 'schemaName'
            // Store kind of pain here -> mainly wired up values
        });

        var pathTextField = new Ext4.form.Text({
            name: 'path',
            fieldLabel: 'Path'
        });

        // load up all stores with a single request
        Ext4.Ajax.request({
            url: LABKEY.ActionURL.buildURL('query', 'getSchemasWithDataSources.api'),
            success: function(response) {
                var data = Ext4.decode(response.responseText);

                // load up stores with schema model
                sourceSchemaStore.loadRawData(data);
                targetSchemaStore.loadRawData(data);

                // get unique datasource names
                var s = {};
                Ext4.each(data["schemas"], function(map) {
                    s[map["dataSourceDisplayName"]] = true;
                });

                // generate those in model data format (could probably skip this)
                var dataSourceData = [];
                Ext4.iterate(s, function(key) {
                    dataSourceData.push({dataSourceDisplayName:key});
                });

                // load up stores with dataSource model
                sourceDataSourceStore.loadRawData(dataSourceData);
                targetDataSourceStore.loadRawData(dataSourceData);

                // handle firing some events
                sourceSchemaStore.addFilter(createDataSourceFilter('labkey'));
                targetSchemaStore.addFilter(createDataSourceFilter('labkey'));

                // select first item in schema dropdowns
                sourceSchemaCombo.select(sourceSchemaStore.getAt(0));
                targetSchemaCombo.select(targetSchemaStore.getAt(0));
            }
        });

        // NOTE: tried to use LABKEY.ext4.FormPanel but do not think store implementation here fits our use case
        var f = new Ext4.FormPanel({
            renderTo: 'form',
            width: 500,
            border: false,
            standardSubmit: true,
            fieldDefaults: {
                width: 350,
                labelWidth: 150
            },
            bodyStyle : 'background-color: transparent;',
            defaults: {
                border: false, // clears border on docked items
                labelCls : 'labkey-form-label'
            },
            items: [
                sourceDataSourceCombo,
                sourceSchemaCombo,
                targetDataSourceCombo,
                targetSchemaCombo,
                pathTextField,
                {
                    xtype: 'hidden',
                    name: 'X-LABKEY-CSRF',
                    value: LABKEY.CSRF
                }
            ],
            dockedItems: [{
                xtype: 'toolbar',
                dock: 'bottom',
                ui: 'footer',
                style: 'background-color: transparent;',
                items: [
                    {
                        text: 'Export',
                        handler: function() {
                            var form = this.up('form').getForm();
                            // TODO: handle client side validation and dirty
                            form.submit();
                        }
                    },
                    {text: 'Cancel'} // TODO: Where am I coming from?
                ]

            }]
        });
    });


</script>