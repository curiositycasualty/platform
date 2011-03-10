/*
 * Copyright (c) 2010 LabKey Corporation
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

/* study-10.30-10.31.sql */

CREATE SCHEMA assayresult;

SELECT core.executeJavaUpgradeCode('materializeAssayResults');

/* study-10.31-10.32.sql */

SELECT core.executeJavaUpgradeCode('deleteDuplicateAssayDatasetFields');

/* study-10.32-10.33.sql */

ALTER TABLE study.SpecimenEvent ADD TotalCellCount INT;
ALTER TABLE study.Vial ADD TotalCellCount INT;