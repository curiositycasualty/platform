/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
UPDATE study.Cohort
    SET LSID='UPGRADE_REQUIRED'
    WHERE LSID IS NULL;

ALTER TABLE study.Cohort
  ALTER COLUMN LSID SET NOT NULL;

ALTER TABLE study.Visit
    ADD COLUMN LSID VARCHAR(200);

UPDATE study.Visit
    SET LSID='UPGRADE_REQUIRED'
    WHERE LSID IS NULL;

ALTER TABLE study.Visit
  ALTER COLUMN LSID SET NOT NULL;

ALTER TABLE study.Study
    ADD COLUMN LSID VARCHAR(200);

UPDATE study.Study
    SET LSID='UPGRADE_REQUIRED'
    WHERE LSID IS NULL;

ALTER TABLE study.Study
  ALTER COLUMN LSID SET NOT NULL;
