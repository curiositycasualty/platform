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
ALTER TABLE study.SampleRequestSpecimen
ADD COLUMN SpecimenGlobalUniqueId VARCHAR(100);

UPDATE study.SampleRequestSpecimen SET SpecimenGlobalUniqueId =
    (SELECT GlobalUniqueId FROM study.Specimen WHERE RowId = SpecimenId);

ALTER TABLE study.Specimen
    DROP COLUMN SpecimenCondition,
    DROP COLUMN SampleNumber,
    DROP COLUMN XSampleOrigin,
    DROP COLUMN ExternalLocation,
    DROP COLUMN UpdateTimestamp,
    DROP COLUMN OtherSpecimenId,
    DROP COLUMN ExpectedTimeUnit,
    DROP COLUMN RecordSource,
    DROP COLUMN GroupProtocol,
    DROP COLUMN ExpectedTimeValue;

ALTER TABLE study.SpecimenEvent
    ADD COLUMN SpecimenCondition VARCHAR(3),
    ADD COLUMN SampleNumber INT,
    ADD COLUMN XSampleOrigin VARCHAR(20),
    ADD COLUMN ExternalLocation VARCHAR(20),
    ADD COLUMN UpdateTimestamp TIMESTAMP,
    ADD COLUMN OtherSpecimenId VARCHAR(20),
    ADD COLUMN ExpectedTimeUnit VARCHAR(15),
    ADD COLUMN ExpectedTimeValue FLOAT,
    ADD COLUMN GroupProtocol INT,
    ADD COLUMN RecordSource VARCHAR(10);

-- fix up study.SampleRequestSpecimen
ALTER TABLE study.SampleRequestSpecimen DROP CONSTRAINT fk_SampleRequestSpecimen_specimen;
ALTER TABLE study.SampleRequestSpecimen DROP COLUMN SpecimenId;

ALTER TABLE study.Specimen DROP CONSTRAINT FK_Specimens_Additives;
ALTER TABLE study.Specimen DROP CONSTRAINT FK_Specimens_Derivatives;
ALTER TABLE study.Specimen DROP CONSTRAINT FK_Specimens_PrimaryTypes;

ALTER TABLE study.SpecimenAdditive DROP CONSTRAINT UQ_Additives;
UPDATE study.SpecimenAdditive SET ScharpId = RowId;
CREATE INDEX IX_SpecimenAdditive_ScharpId ON study.SpecimenAdditive(ScharpId);
ALTER TABLE study.SpecimenAdditive ADD CONSTRAINT UQ_Additives UNIQUE (ScharpId, Container);

ALTER TABLE study.SpecimenDerivative DROP CONSTRAINT UQ_Derivatives;
UPDATE study.SpecimenDerivative SET ScharpId = RowId;
CREATE INDEX IX_SpecimenDerivative_ScharpId ON study.SpecimenDerivative(ScharpId);
ALTER TABLE study.SpecimenDerivative ADD CONSTRAINT UQ_Derivatives UNIQUE (ScharpId, Container);

ALTER TABLE study.SpecimenPrimaryType DROP CONSTRAINT UQ_PrimaryTypes;
UPDATE study.SpecimenPrimaryType SET ScharpId = RowId;
CREATE INDEX IX_SpecimenPrimaryType_ScharpId ON study.SpecimenPrimaryType(ScharpId);
ALTER TABLE study.SpecimenPrimaryType ADD CONSTRAINT UQ_PrimaryTypes UNIQUE (ScharpId, Container);

CREATE INDEX IX_SpecimenEvent_SpecimenId ON study.SpecimenEvent(SpecimenId);
CREATE INDEX IX_Specimen_PrimaryTypeId ON study.Specimen(PrimaryTypeId);
CREATE INDEX IX_Specimen_DerivativeTypeId ON study.Specimen(DerivativeTypeId);
CREATE INDEX IX_Specimen_AdditiveTypeId ON study.Specimen(AdditiveTypeId);

CREATE TABLE study.StudyDesign
        (
            -- standard fields
            _ts TIMESTAMP,
            StudyId SERIAL,
            CreatedBy USERID,
            Created TIMESTAMP,
            ModifiedBy USERID,
            Modified TIMESTAMP,
            Container ENTITYID NOT NULL,
            PublicRevision INT NULL,
            DraftRevision INT NULL,
            Label VARCHAR(200) NOT NULL,

            CONSTRAINT PK_StudyDesign PRIMARY KEY (StudyId),
            CONSTRAINT UQ_StudyDesign UNIQUE (Container,StudyId),
            CONSTRAINT UQ_StudyDesignLabel UNIQUE (Container, Label)
        );


        CREATE TABLE study.StudyDesignVersion
        (
            -- standard fields
            _ts TIMESTAMP,
            RowId SERIAL,
            StudyId INT NOT NULL,
            CreatedBy USERID,
            Created TIMESTAMP,
            Container ENTITYID NOT NULL,
            Revision INT NOT NULL,
            Draft Boolean NOT NULL DEFAULT '1',
            Label VARCHAR(200) NOT NULL,
            Description TEXT,
            XML TEXT,

            CONSTRAINT PK_StudyDesignVersion PRIMARY KEY (StudyId,Revision),
            CONSTRAINT FK_StudyDesignVersion_StudyDesign FOREIGN KEY (StudyId) REFERENCES study.StudyDesign(StudyId),
            CONSTRAINT UQ_StudyDesignVersion UNIQUE (Container,Label,Revision)
        );

ALTER TABLE study.Report DROP CONSTRAINT PK_Report;

ALTER TABLE study.Report ADD CONSTRAINT PK_Report PRIMARY KEY (ContainerId, ReportId);

UPDATE exp.ObjectProperty SET
    StringValue = CAST(exp.ObjectProperty.floatValue AS INTEGER),
    TypeTag = 's',
    floatValue = NULL
WHERE
    (SELECT exp.PropertyDescriptor.PropertyURI FROM exp.PropertyDescriptor
        WHERE exp.PropertyDescriptor.PropertyId =
        exp.ObjectProperty.PropertyId) LIKE '%StudyDataset.%NAB#FileId' AND
    (SELECT exp.PropertyDescriptor.RangeURI FROM exp.PropertyDescriptor
        WHERE exp.PropertyDescriptor.PropertyId = exp.ObjectProperty.PropertyId) =
        'http://www.w3.org/2001/XMLSchema#int';

UPDATE exp.PropertyDescriptor SET
    RangeURI = 'http://www.w3.org/2001/XMLSchema#string'
WHERE
    exp.PropertyDescriptor.RangeURI = 'http://www.w3.org/2001/XMLSchema#int' AND
    exp.PropertyDescriptor.PropertyURI LIKE '%StudyDataset.%NAB#FileId';

CREATE INDEX IX_AssayRun_Container ON study.AssayRun(Container);

CREATE INDEX IX_Plate_Container ON study.Plate(Container);

CREATE INDEX IX_SampleRequest_Container ON study.SampleRequest(Container);

CREATE INDEX IX_SampleRequest_StatusId ON study.SampleRequest(StatusId);

CREATE INDEX IX_SampleRequest_DestinationSiteId ON study.SampleRequest(DestinationSiteId);

CREATE INDEX IX_SampleRequestActor_Container ON study.SampleRequestActor(Container);

CREATE INDEX IX_SampleRequestEvent_Container ON study.SampleRequestEvent(Container);

CREATE INDEX IX_SampleRequestEvent_RequestId ON study.SampleRequestEvent(RequestId);

CREATE INDEX IX_SampleRequestRequirement_Container ON study.SampleRequestRequirement(Container);

CREATE INDEX IX_SampleRequestRequirement_RequestId ON study.SampleRequestRequirement(RequestId);

CREATE INDEX IX_SampleRequestRequirement_ActorId ON study.SampleRequestRequirement(ActorId);

CREATE INDEX IX_SampleRequestRequirement_SiteId ON study.SampleRequestRequirement(SiteId);

CREATE INDEX IX_SampleRequestSpecimen_Container ON study.SampleRequestSpecimen(Container);

CREATE INDEX IX_SampleRequestSpecimen_SampleRequestId ON study.SampleRequestSpecimen(SampleRequestId);

CREATE INDEX IX_SampleRequestSpecimen_SpecimenGlobalUniqueId ON study.SampleRequestSpecimen(SpecimenGlobalUniqueId);

CREATE INDEX IX_SampleRequestStatus_Container ON study.SampleRequestStatus(Container);

CREATE INDEX IX_Specimen_Container ON study.Specimen(Container);

CREATE INDEX IX_Specimen_GlobalUniqueId ON study.Specimen(GlobalUniqueId);

CREATE INDEX IX_SpecimenAdditive_Container ON study.SpecimenAdditive(Container);

CREATE INDEX IX_SpecimenDerivative_Container ON study.SpecimenDerivative(Container);

CREATE INDEX IX_SpecimenPrimaryType_Container ON study.SpecimenPrimaryType(Container);

CREATE INDEX IX_SpecimenEvent_Container ON study.SpecimenEvent(Container);

CREATE INDEX IX_SpecimenEvent_LabId ON study.SpecimenEvent(LabId);

CREATE INDEX IX_Well_PlateId ON study.Well(PlateId);

CREATE INDEX IX_Well_Container ON study.Well(Container);

CREATE INDEX IX_WellGroup_PlateId ON study.WellGroup(PlateId);

CREATE INDEX IX_WellGroup_Container ON study.WellGroup(Container);

/*
 * Fix for https://cpas.fhcrc.org/Issues/home/issues/details.view?issueId=3111
 */
