/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

-- Schema & tables used for Audit module
EXEC sp_addapprole 'audit', 'password'
GO

CREATE TABLE audit.AuditLog
(
    RowId INT IDENTITY(1,1) NOT NULL,
    Key1 NVARCHAR(64),
    Key2 NVARCHAR(64),
    IntKey1 INT NULL,
    IntKey2 INT NULL,
    Comment NVARCHAR(255),
    EventType NVARCHAR(64),
    CreatedBy USERID,
    Created DATETIME,
    ContainerId ENTITYID,
    EntityId ENTITYID NULL,
    ObjectXML TEXT,

    CONSTRAINT PK_AuditLog PRIMARY KEY (RowId)
);
GO

INSERT INTO audit.AuditLog (IntKey1, Created, Comment, EventType)
    (SELECT UserId, Date, Message, 'UserAuditEvent' FROM core.UserHistory);
GO

DROP TABLE core.UserHistory;
GO
