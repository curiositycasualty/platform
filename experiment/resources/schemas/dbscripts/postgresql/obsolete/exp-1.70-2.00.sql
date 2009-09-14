/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
SELECT core.fn_dropifexists ('Experiment', 'exp', 'INDEX', 'IX_Experiment_Container')
;
SELECT core.fn_dropifexists ('ExperimentRun', 'exp', 'INDEX', 'IX_CL_ExperimentRun_Container')
;
SELECT core.fn_dropifexists ('ExperimentRun', 'exp', 'INDEX', 'IX_ExperimentRun_ProtocolLSID')
;
SELECT core.fn_dropifexists ('RunList', 'exp', 'INDEX', 'IX_RunList_ExperimentRunId')
;
SELECT core.fn_dropifexists ('ProtocolApplicationParameter', 'exp', 'INDEX', 'IX_ProtocolApplicationParameter_AppId')
;
SELECT core.fn_dropifexists ('ProtocolActionPredecessor', 'exp', 'INDEX', 'IX_ProtocolActionPredecessor_PredecessorId')
;
SELECT core.fn_dropifexists ('ProtocolAction', 'exp', 'INDEX', 'IX_ProtocolAction_ChildProtocolId')
;
SELECT core.fn_dropifexists ('ProtocolParameter', 'exp', 'INDEX', 'IX_ProtocolParameter_ProtocolId')
;
SELECT core.fn_dropifexists ('Data', 'exp', 'INDEX', 'IX_Data_Container')
;
SELECT core.fn_dropifexists ('Data', 'exp', 'INDEX', 'IX_Data_SourceApplicationId')
;
SELECT core.fn_dropifexists ('Data', 'exp', 'INDEX', 'IX_Data_DataFileUrl')
;
SELECT core.fn_dropifexists ('DataInput', 'exp', 'INDEX', 'IX_DataInput_PropertyId')
;
SELECT core.fn_dropifexists ('Material', 'exp', 'INDEX', 'IX_Material_Container')
;
SELECT core.fn_dropifexists ('Material', 'exp', 'INDEX', 'IX_Material_SourceApplicationId')
;
SELECT core.fn_dropifexists ('Material', 'exp', 'INDEX', 'IX_Material_CpasType')
;
SELECT core.fn_dropifexists ('MaterialInput', 'exp', 'INDEX', 'IX_MaterialInput_PropertyId')
;
SELECT core.fn_dropifexists ('MaterialSource', 'exp', 'INDEX', 'IX_MaterialSource_Container')
;
SELECT core.fn_dropifexists ('ActiveMaterialSource', 'exp', 'INDEX', 'IX_ActiveMaterialSource_MaterialSourceLSID')
;
SELECT core.fn_dropifexists ('ObjectProperty', 'exp', 'INDEX', 'IX_ObjectProperty_PropertyObject')
;
SELECT core.fn_dropifexists ('PropertyDomain', 'exp', 'INDEX', 'IX_PropertyDomain_DomainId')
;
SELECT core.fn_dropifexists ('PropertyDescriptor', 'exp', 'INDEX', 'IX_PropertyDescriptor_Container')
;
SELECT core.fn_dropifexists ('DomainDescriptor', 'exp', 'INDEX', 'IX_DomainDescriptor_Container')
;
SELECT core.fn_dropifexists ('Object', 'exp', 'INDEX', 'IX_Object_OwnerObjectId')
;
-- redundant to unique constraint
SELECT core.fn_dropifexists ('Object', 'exp', 'INDEX', 'IX_Object_Uri')
;



CREATE INDEX IX_Experiment_Container ON exp.Experiment(Container)
;
CREATE INDEX IX_CL_ExperimentRun_Container ON exp.ExperimentRun(Container)
;
CREATE INDEX IX_ExperimentRun_ProtocolLSID ON exp.ExperimentRun(ProtocolLSID)
;
CREATE INDEX IX_RunList_ExperimentRunId ON exp.RunList(ExperimentRunId)
;
CREATE INDEX IX_ProtocolApplicationParameter_AppId ON exp.ProtocolApplicationParameter(ProtocolApplicationId)
;
CREATE INDEX IX_ProtocolActionPredecessor_PredecessorId ON exp.ProtocolActionPredecessor(PredecessorId)
;
CREATE INDEX IX_ProtocolAction_ChildProtocolId ON exp.ProtocolAction(ChildProtocolId)
;
CREATE INDEX IX_ProtocolParameter_ProtocolId ON exp.ProtocolParameter(ProtocolId)
;

CREATE INDEX IX_Data_Container ON exp.Data(Container)
;
CREATE INDEX IX_Data_SourceApplicationId ON exp.Data(SourceApplicationId)
;
CREATE INDEX IX_DataInput_PropertyId ON exp.DataInput(PropertyId)
;
CREATE INDEX IX_Data_DataFileUrl ON exp.Data(DataFileUrl)
;

CREATE INDEX IX_Material_Container ON exp.Material(Container)
;
CREATE INDEX IX_Material_SourceApplicationId ON exp.Material(SourceApplicationId)
;
CREATE INDEX IX_MaterialInput_PropertyId ON exp.MaterialInput(PropertyId)
;
CREATE INDEX IX_Material_CpasType ON exp.Material(CpasType)
;


CREATE INDEX IX_MaterialSource_Container ON exp.MaterialSource(Container)
;
CREATE INDEX IX_ActiveMaterialSource_MaterialSourceLSID ON exp.ActiveMaterialSource(MaterialSourceLSID)
;


CREATE  INDEX IX_ObjectProperty_PropertyObject ON exp.ObjectProperty(PropertyId, ObjectId)
;
CREATE INDEX IX_PropertyDomain_DomainId ON exp.PropertyDomain(DomainID)
;
CREATE INDEX IX_PropertyDescriptor_Container ON exp.PropertyDescriptor(Container)
;
CREATE INDEX IX_DomainDescriptor_Container ON exp.DomainDescriptor(Container)
;
CREATE INDEX IX_Object_OwnerObjectId ON exp.Object(OwnerObjectId)
;
