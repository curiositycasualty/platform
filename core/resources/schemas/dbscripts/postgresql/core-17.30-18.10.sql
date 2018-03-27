/*
 * Copyright (c) 2017 LabKey Corporation
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

/* core-17.30-17.31.sql */

CREATE TABLE core.APIKeys
(
    CreatedBy USERID,
    Created TIMESTAMP,
    Crypt VARCHAR(100),
    Expiration TIMESTAMP NULL,

    CONSTRAINT PK_APIKeys PRIMARY KEY (Crypt)
);

/* core-17.31-17.32.sql */

ALTER TABLE core.APIKeys
    ADD COLUMN RowId SERIAL,
    DROP CONSTRAINT PK_APIKeys,
    ADD CONSTRAINT PK_APIKeys PRIMARY KEY (RowId),
    ADD CONSTRAINT UQ_CRYPT UNIQUE (Crypt);