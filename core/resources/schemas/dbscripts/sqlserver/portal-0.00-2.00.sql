/*
 * Copyright (c) 2009 LabKey Corporation
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

/* portal-0.00-1.00.sql */

EXEC sp_addapprole 'portal', 'password'
GO

CREATE TABLE portal.PortalWebParts
	(
	PageId ENTITYID NOT NULL,
	[Index] INT NOT NULL,
	Name VARCHAR(64),
	Location VARCHAR(16),	-- 'body', 'left', 'right'

	Properties VARCHAR(4000),	-- url encoded properties

	CONSTRAINT PK_PortalWebParts PRIMARY KEY (PageId, [Index])
	)
GO

/* portal-1.50-1.60.sql */

ALTER TABLE portal.portalwebparts ADD Permanent BIT NOT NULL DEFAULT 0
GO

DELETE FROM portal.PortalWebParts
	WHERE NOT EXISTS (SELECT EntityId FROM core.Containers C WHERE C.EntityId = PageId)
GO

/* portal-1.60-1.70.sql */

UPDATE Portal.PortalWebParts
    SET Name = 'Messages'
    WHERE Name = 'Announcements'
GO

UPDATE Portal.PortalWebParts
SET Properties = 'webPartContainer=' + UPPER(PageId) + '&name=default'
WHERE (Name = 'Wiki' OR Name = 'Narrow Wiki') AND (Properties IS NULL OR Properties = '')
GO