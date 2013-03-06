/*
 * Copyright (c) 2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresExt4ClientAPI();
LABKEY.requiresScript("/extWidgets/Ext4Helper.js");
LABKEY.requiresScript("/survey/SurveyGridQuestion.js");
LABKEY.requiresScript("/survey/AttachmentField.js");
LABKEY.requiresScript("/survey/UsersCombo.js");
LABKEY.requiresScript("/biotrust/DiseaseTypeQuestion.js");
LABKEY.requiresScript("/biotrust/LinkToDiscarded.js");
LABKEY.requiresScript("/biotrust/ProtocolDocuments.js");
LABKEY.requiresScript("/biotrust/StudySampleRequestPanel.js");
LABKEY.requiresScript("/biotrust/TissueRecordPanel.js");
LABKEY.requiresCss("/survey/Survey.css");

Ext4.define('LABKEY.ext4.BaseSurveyPanel', {

    extend : 'Ext.form.Panel',

    constructor : function(config){
        this.callParent([config]);
    },

    initComponent : function() {
        this.callParent();
    },

    generateSurveySections : function(surveyConfig, panelType) {

        // default to a normal ext panel
        if (!panelType)
            panelType = 'Ext.panel.Panel';

        // add each of the sections as a panel to the sections array
        if (surveyConfig.survey)
        {
            Ext4.each(surveyConfig.survey.sections, function(section){
                var sectionPanel = Ext4.create(panelType, {
                    border: false,
                    flex: 1,
                    autoScroll: true,
                    cls : 'iScroll', // webkit custom scroll bars
                    header: this.surveyLayout == 'card' ? false : (section.header != undefined ? section.header : true),
                    sectionPanel: true, // marker for looking for a components parent section
                    completedQuestions: 0, // counter for the section header to show progress when a panel is collapsed
                    origTitle: section.title || '',
                    title: section.title || '',
                    copyFromPrevious: section.copyFromPrevious || false,
                    isDisabled: section.initDisabled || false,
                    defaults: {
                        labelWidth: section.defaultLabelWidth || 350,
                        labelSeparator: '',
                        padding: 10
                    },
                    items: []
                });

                // for card layout, add the section title as a displayfield instead of a panel header
                if (this.surveyLayout == 'card' && section.title)
                {
                    sectionPanel.add(this.getCardSectionHeader(section.title));
                }
                else
                {
                    sectionPanel.collapsed = section.collapsed != undefined ? section.collapsed : false;
                    sectionPanel.collapsible = section.collapsible != undefined ? section.collapsible : true;
                    sectionPanel.titleCollapse = true;
                }

                // there is a section description, add it as the first item for that section
                if (section.description)
                {
                    sectionPanel.add({
                        xtype: 'displayfield',
                        hideLabel: true,
                        value: section.description
                    })
                }

                // each section can have a set of questions to be added using the FormHelper
                if (section.questions)
                {
                    for (var i = 0; i < section.questions.length; i++)
                    {
                        var question = section.questions[i];

                        // not currently using helpPopups for the survey question field labels
                        question.helpPopup = [];

                        // we don't want to apply the hidden state until after the config is created
                        var hidden = question.hidden;
                        question.hidden = false;

                        var config;
                        if (question.extConfig)
                        {
                            // if the question is defined as an ext component, pass it through
                            config = question.extConfig;
                        }
                        else
                        {
                            // the labkey formhelper doesn't support file upload fields, so we'll step in here and
                            // add one manually
                            if (question.inputType == 'file')
                            {
                                var attachment = undefined;
                                var entry = this.rowMap[question.name];

                                if (entry && entry.value)
                                {
                                    attachment = {};
                                    attachment.icon = LABKEY.Utils.getFileIconUrl(entry.value);
                                    attachment.name = entry.value;
                                    attachment.downloadURL = entry.url;
                                }

                                config = {
                                    fieldLabel : question.caption,
                                    attachment : attachment,
                                    name : question.name,
                                    fieldWidth : 445,
                                    xtype : 'attachmentfield'
                                };
                            }
                            else
                            {
                                // if the question is not defined as ext, use the column metadata form helper
                                config = LABKEY.ext.Ext4Helper.getFormEditorConfig(question);
                            }
                        }

                        // survey specific question configurations (required field display, etc.)
                        config = this.customizeQuestionConfig(question, config, hidden);

                        // add a component id for retrieval
                        if (config.name && !config.itemId)
                            config.itemId = 'item-' + config.name;

                        // register any configured listeners
                        // Note: we allow for an array of listeners OR an array of question names to apply a single listener to
                        var listeners = question.listeners || {};
                        if (listeners.change)
                        {
                            if (!(listeners.change instanceof Array))
                                listeners.change = [listeners.change];

                            for (var listenerIndex = 0; listenerIndex < listeners.change.length; listenerIndex++)
                            {
                                var listenerFn = listeners.change[listenerIndex].fn;
                                var listenerNames;
                                if (listeners.change[listenerIndex].question instanceof Array)
                                    listenerNames = listeners.change[listenerIndex].question;
                                else
                                    listenerNames = [listeners.change[listenerIndex].question];

                                for (var qnameIndex = 0; qnameIndex < listenerNames.length; qnameIndex++)
                                {
                                    var handlers = this.changeHandlers[listenerNames[qnameIndex]] || [];
                                    var changeFn = new Function('', "return " + listenerFn);

                                    handlers.push({name : config.name, fn : changeFn});
                                    this.changeHandlers[listenerNames[qnameIndex]] = handlers;
                                }
                            }
                        }

                        sectionPanel.add(config);
                    }
                }
                // sections can be defined as an extAlias
                // TODO: is there a way to not have to import the JS for each defined ext alias?
                else if (section.extAlias)
                {
                    sectionPanel.add({
                        xtype: section.extAlias,
                        isSubmitted: this.isSubmitted,
                        canEdit: this.canEdit,
                        listeners: {
                            // allow custom section panels to fire save events back to the parent
                            saveSurvey: function(successUrl, idParamName) {
                                this.saveSurvey(null, null, false, successUrl, idParamName);
                            },
                            scope: this
                        }
                    });
                }

                this.sections.push(sectionPanel);
            }, this);
        }
    },

    customizeQuestionConfig : function(question, config, hidden) {
        // make the field label for required questions bold and end with an *
        if (question.required != undefined && question.required)
        {
            config.allowBlank = false;
            config.labelStyle = "font-weight: bold;";
            config.fieldLabel = config.fieldLabel + "*";
        }

        // customize the checkbox config to make sure unchecked values get submitted
        if (question.inputType != undefined && question.inputType == 'checkbox')
        {
            config.inputValue = 'true';
            config.uncheckedValue = 'false';
        }

        // set the date field format
        if (config.xtype == 'datefield')
        {
            config.format = question.format ? question.format : "Y-m-d";
        }

        // if the question has a description, append it to the field label
        if (question.description)
            config.fieldLabel += "<br/>" + question.description;

        // if hidden, apply that to the config
        if (hidden != undefined && hidden)
            config.hidden = true;

        // if the question has a short caption (used in the submit button disabled info), make sure it is applied
        if (question.shortCaption)
            config.shortCaption = question.shortCaption;

        // if the user can not edit this survey (i.e. submitted and non-admin), make the field readOnly
        if (!this.canEdit)
            config.readOnly = true;

        // make the name lowercase for consistency
        if (config.name)
            config.name = config.name.toLowerCase();

        // apply lookup filter (currently only supports "ISBLANK" filter type
        if (question.lookup && question.lookup.filterColumn)
            config.store.filterArray = [LABKEY.Filter.create(question.lookup.filterColumn, null, LABKEY.Filter.Types.ISBLANK)];

        return config;
    },

    getCardSectionHeader : function(title) {
        var txt = Ext.DomHelper.markup({tag:'div', cls:'labkey-nav-page-header', html: title}) +
                Ext.DomHelper.markup({tag:'div', html:'&nbsp;'});
        return {xtype:'displayfield', value: txt};
    },

    configureSurveyLayout : function(surveyConfig, initSectionTitle) {
        this.currentStep = this.getInitSection(initSectionTitle || LABKEY.ActionURL.getParameter("sectionTitle"));

        // in card layout, we add a side bar with the section titles and next/previous buttons
        var bbar = [];
        if (this.surveyLayout == 'card')
        {
            this.width += 250;

            // define function to be called on click of a sidebar section title
            window.surveySidebarSectionClick = function(step, itemId){
                // need to use component query to get back at the FormPanel object
                var panels = Ext4.ComponentQuery.query('#' + itemId);
                if (panels.length == 1)
                {
                    panels[0].updateStep(step);
                }
            };

            this.sideBar = Ext4.create('Ext.panel.Panel', {
                name: 'sidebar',
                width: 250,
                border: false,
                cls: 'extContainer',
                tpl: [
                    '<div class="labkey-ancillary-wizard-background">',
                    '<ol class="labkey-ancillary-wizard-steps">',
                    '<tpl for="steps">',
                        '<tpl if="values.currentStep == true">',
                            '<li class="labkey-ancillary-wizard-active-step">{value}</li>',
                        '<tpl elseif="values.isDisabled == false">',
                            '<li onclick="surveySidebarSectionClick({step}, \'{panelId}\');" class="labkey-side-bar-title">{value}</li>',
                        '<tpl else>',
                            '<li class="labkey-side-bar-title-disabled">{value}</li>',
                        '</tpl>',
                    '</tpl>',
                    '</ol>',
                    '</div>'
                ],
                data: {steps: this.getStepsDataArr()}
            });
            this.add(this.sideBar);

            this.prevBtn = Ext4.create('Ext.button.Button', {
                text: 'Previous',
                disabled: true,
                scope: this,
                handler: function(cmp){
                    this.updateStep(this.previousEnabledStepIndex());
            }});

            this.nextBtn = Ext4.create('Ext.button.Button', {
                text: 'Next',
                scope: this,
                handler: function(cmp){
                    this.updateStep(this.nextEnabledStepIndex());
            }});
            bbar = ['->', this.prevBtn, this.nextBtn];
        }

        this.centerPanel = Ext4.create('Ext.panel.Panel', {
            layout: this.surveyLayout,
            border: false,
            minHeight: this.surveyLayout == 'card' ? 500 : undefined,
            height: this.fixedHeight ? this.fixedHeight : undefined,
            bodyStyle : 'padding: 10px;',
            activeItem: this.currentStep,
            flex: 1,
            items: this.sections,
            bbar: bbar.length > 0 ? bbar : undefined
        });
        this.add(this.centerPanel);
    },

    getInitSection : function(goToSection) {
        if (goToSection)
        {
            for (var i = 0; i < this.sections.length; i++)
            {
                if (this.sections[i].title == goToSection)
                    return i;
            }
        }
        return 0;
    },

    previousEnabledStepIndex : function() {
        var step = this.currentStep;
        var steps = this.getStepsDataArr();
        for (var i = (step - 1); i > -1; i--)
        {
            if (!steps[i].isDisabled)
            {
                step = i;
                break;
            }
        }

        return step;
    },

    nextEnabledStepIndex : function() {
        var step = this.currentStep;
        var steps = this.getStepsDataArr();
        for (var i = (step + 1); i < steps.length; i++)
        {
            if (!steps[i].isDisabled)
            {
                step = i;
                break;
            }
        }

        return step;
    },

    getStepsDataArr : function() {
        var steps = [];
        for (var i = 0; i < this.sections.length; i++)
        {
            steps.push({
                value: this.sections[i].title,
                step: i,
                currentStep: i == this.currentStep,
                isDisabled: this.sections[i].isDisabled,
                panelId: this.itemId
            });
        }

        return steps;
    },

    updateStep : function(step) {

        this.currentStep = step;
        this.sideBar.update({steps: this.getStepsDataArr()});
        this.centerPanel.getLayout().setActiveItem(this.currentStep);

        this.prevBtn.setDisabled(this.currentStep == 0);
        this.nextBtn.setDisabled(this.currentStep == this.sections.length-1);
    },

    configureFieldListeners : function() {

        Ext4.each(this.getForm().getFields().items, function(field){

            // add a global change listener for all questions so we can map them to any change handlers specified in the survey
            field.addListener('change', this.questionChangeHandler, this);

            // if the user can not edit this survey (i.e. submitted and non-admin), make the field readOnly
            if (!this.canEdit)
            {
                field.setReadOnly(true);
            }
            else
            {
                // add a validity listener to each question
                if (field.submitValue)
                {
                    this.validStatus[field.getName()] = field.isValid();
                    field.clearInvalid();
                    field.addListener('validitychange', this.fieldValidityChanged, this);
                }
            }

        }, this);
    },

    fieldValidityChanged : function(cmp, isValid) {
        this.validStatus[cmp.getName()] = isValid;
    },

    questionChangeHandler : function(cmp, newValue, oldValue) {

        // if there is a handler mapped to the component
        var changeHandlers = this.changeHandlers[cmp.getName()];
        if (Ext4.isArray(changeHandlers))
        {
            var values = this.getForm().getValues();

            for (var i=0; i < changeHandlers.length; i++)
            {
                var info = changeHandlers[i];
                var me = this.down('#item-' + info.name);
                if (me)
                {
                    var changeFn = info.fn;

                    changeFn().call(this, me, cmp, newValue, oldValue, values);

                    this.clearHiddenFieldValues(me);
                }
            }
        }

        if (this.showCounts)
            this.udpateSectionCount(cmp, newValue, oldValue);
    },

    clearHiddenFieldValues : function(cmp) {
        if (cmp.isHidden())
        {
            // the component can either be a form field itself or a container that has multiple fields
            if (cmp.isFormField)
                this.clearFieldValue(cmp);
            else
                Ext4.each(cmp.query('field'), this.clearFieldValue, this);
        }
    },

    clearFieldValue : function(field) {
        // only "reset" form fields that are not displayfields
        if (field && field.isFormField && field.getXType() != 'displayfield')
        {
            if (field.clearValue != undefined)
                field.clearValue();
            else
                field.setValue(null);
        }
    },

    udpateSectionCount : function(cmp, newValue, oldValue) {

        var sectionPanel = cmp.up('.panel[sectionPanel=true]');
        if (sectionPanel)
        {
            var changed = false;

            // special case for checkbox fields since the value is never null (i.e. true or false)
            if (cmp.getXType() == "checkboxfield")
            {
                sectionPanel.completedQuestions = sectionPanel.completedQuestions + (newValue ? 1 : -1);
                changed = true;
            }
            // special case for any custom question types
            else if (cmp.getXType() == "surveygridquestion")
            {
                if (newValue > 0 && oldValue == 0)
                {
                    sectionPanel.completedQuestions++;
                    changed = true;
                }
                else if (newValue == 0 && oldValue > 0)
                {
                    sectionPanel.completedQuestions--;
                    changed = true;
                }
            }
            else if ((oldValue == null || oldValue.toString().length == 0) && (newValue != null && newValue.toString().length > 0))
            {
                sectionPanel.completedQuestions++;
                changed = true;
            }
            else if ((newValue == null || newValue.toString().length == 0) && (oldValue != null && oldValue.toString().length > 0))
            {
                sectionPanel.completedQuestions--;
                changed = true;
            }

            if (changed)
            {
                sectionPanel.setTitle(sectionPanel.origTitle + (sectionPanel.completedQuestions > 0 ? " (" + sectionPanel.completedQuestions + ")" : ""));

                // if we are in card layout, update the side bar titles
                if (this.sideBar)
                    this.sideBar.update({steps: this.getStepsDataArr()});
            }
        }
    },

    getFormDirtyValues : function() {
        return this.getDirtyValues(this.getForm());
    },

    getDirtyValues : function(form)
    {
        var values = {};
        Ext4.each(form.getFields().items, function(field){
            if (field.submitValue && field.isDirty() && field.isValid())
            {
                // special casing for radiogroups and radiofields, i.e. skip the group field and use the individual radiofeilds
                if (field.getXType() == "radiogroup")
                {} // skip the radiogroup itself in favor of the radiofields
                else if (field.getXType() == "radiofield")
                    values[field.getName()] = field.getGroupValue();
                else
                    values[field.getName()] = field.getSubmitValue();
            }
        }, this);

        return values;
    },

    saveSurvey : function(btn, evt, toSubmit, successUrl, idParamName) {
        // default, do nothing
    },

    onFailure : function(resp, message, hidePanel) {
        var error = {};
        if (resp && resp.responseText)
            error = Ext4.decode(resp.responseText);
        else if (resp)
            error = resp;

        if (error.exception)
            message = error.exception;
        else if (error.errorInfo)
            message = error.errorInfo;

        // explicitly check for equals true because hidePanel could be an object
        if (hidePanel == true)
            this.update("<span class='labkey-error'>" + message + "</span>");
        else
            Ext4.MessageBox.alert('Error', message != null ? message : 'An unknown error has ocurred.');

        this.clearLoadingMask();
    },

    clearLoadingMask: function() {
        this.setLoading(false);
    }
});