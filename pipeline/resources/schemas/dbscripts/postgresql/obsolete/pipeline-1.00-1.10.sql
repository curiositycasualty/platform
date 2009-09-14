/*
 * Copyright (c) 2005-2009 LabKey Corporation
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


ALTER TABLE pipeline.StatusFiles
    ADD Description VARCHAR(255),
    ADD DataUrl VARCHAR(255),
    ADD Job VARCHAR(36),
    ADD Provider VARCHAR(255);

UPDATE pipeline.StatusFiles SET Provider = 'X!Tandem (Cluster)'
WHERE FilePath LIKE '%/xtan/%';

UPDATE pipeline.StatusFiles SET Provider = 'Comet (Cluster)'
WHERE FilePath LIKE '%/cmt/%';

UPDATE pipeline.StatusFiles SET Provider = 'msInspect (Cluster)'
WHERE FilePath LIKE '%/inspect/%';