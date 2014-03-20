/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.study.importer;

import org.labkey.api.admin.ImportException;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.Visit;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.xml.ExportDirType;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by klum on 1/24/14.
 */
public class AssayScheduleImporter extends DefaultStudyDesignImporter implements InternalStudyImporter
{
    // shared transform data structures
    Map<Integer, Integer> _assaySpecimenIdMap = new HashMap<>();
    Map<Double, Visit> _visitMap = new HashMap<>();

    private AssaySpecimenTransform _assaySpecimenTransform = new AssaySpecimenTransform();
    private AssaySpecimenVisitMapTransform _assaySpecimenVisitMapTransform = new AssaySpecimenVisitMapTransform();

    @Override
    public String getDescription()
    {
        return "assay schedule data";
    }

    @Override
    public void process(StudyImportContext ctx, VirtualFile root, BindException errors) throws Exception
    {
        ExportDirType dirType = ctx.getXml().getAssaySchedule();

        if (dirType != null)
        {
            VirtualFile vf = root.getDir(dirType.getDir());
            if (vf != null)
            {
                // import project-level tables first, since study-level may reference them
                StudyQuerySchema schema = StudyQuerySchema.createSchema(StudyManager.getInstance().getStudy(ctx.getContainer()), ctx.getUser(), true);

                // study design tables
                ctx.getLogger().info("Importing study design data tables");
                List<String> studyDesignTableNames = new ArrayList<>();
                studyDesignTableNames.add(StudyQuerySchema.STUDY_DESIGN_ASSAYS_TABLE_NAME);
                studyDesignTableNames.add(StudyQuerySchema.STUDY_DESIGN_LABS_TABLE_NAME);
                studyDesignTableNames.add(StudyQuerySchema.STUDY_DESIGN_SAMPLE_TYPES_TABLE_NAME);

                StudyQuerySchema projectSchema = ctx.isDataspaceProject() ? new StudyQuerySchema(StudyManager.getInstance().getStudy(ctx.getProject()), ctx.getUser(), true) : schema;
                for (String studyDesignTableName : studyDesignTableNames)
                {
                    StudyQuerySchema.TablePackage tablePackage = schema.getTablePackage(ctx, projectSchema, studyDesignTableName);
                    importTableData(ctx, vf, tablePackage, null, new PreserveExistingProjectData(ctx.getUser(), tablePackage.getTableInfo(), "Name"));
                }

                // assay specimen table
                ctx.getLogger().info("Importing assay schedule tables");
                StudyQuerySchema.TablePackage assaySpecimenTablePackage = schema.getTablePackage(ctx, projectSchema, StudyQuerySchema.ASSAY_SPECIMEN_TABLE_NAME);
                importTableData(ctx, vf, assaySpecimenTablePackage, _assaySpecimenTransform, null);

                // assay specimen visit table
                StudyQuerySchema.TablePackage assaySpecimenVisitTablePackage = schema.getTablePackage(ctx, projectSchema, StudyQuerySchema.ASSAY_SPECIMEN_VISIT_TABLE_NAME);
                importTableData(ctx, vf, assaySpecimenVisitTablePackage, null, _assaySpecimenVisitMapTransform);
            }
            else
                throw new ImportException("Unable to open the folder at : " + dirType.getDir());
        }
    }

    /**
     * Transform which manages foreign keys to the Treatment table
     */
    private class AssaySpecimenTransform implements TransformBuilder
    {
        @Override
        public void createTransformInfo(StudyImportContext ctx, List<Map<String, Object>> origRows, List<Map<String, Object>> insertedRows)
        {
            for (int i=0; i < origRows.size(); i++)
            {
                Map<String, Object> orig = origRows.get(i);
                Map<String, Object> inserted = insertedRows.get(i);

                if (orig.containsKey("RowId") && inserted.containsKey("RowId"))
                {
                    _assaySpecimenIdMap.put((Integer)orig.get("RowId"), (Integer)inserted.get("RowId"));
                }
            }
        }
    }

    /**
     * Transform which manages visit, and assay specimen FKs from the AssaySpecimenVisit table
     */
    private class AssaySpecimenVisitMapTransform implements TransformHelper
    {
        private void initializeDataMaps(StudyImportContext ctx)
        {
            Study study = StudyService.get().getStudy(ctx.getContainer());
            if (_visitMap.isEmpty())
            {
                for (Visit visit : StudyManager.getInstance().getVisits(study, Visit.Order.SEQUENCE_NUM))
                {
                    _visitMap.put(visit.getSequenceNumMin(), visit);
                }
            }
        }

        @Override
        public List<Map<String, Object>> transform(StudyImportContext ctx, List<Map<String, Object>> origRows) throws ImportException
        {
            List<Map<String, Object>> newRows = new ArrayList<>();
            initializeDataMaps(ctx);

            for (Map<String, Object> row : origRows)
            {
                Map<String, Object> newRow = new CaseInsensitiveHashMap<>();
                newRows.add(newRow);
                newRow.putAll(row);

                if (newRow.containsKey("visitId") && newRow.containsKey("visitId.sequenceNumMin"))
                {
                    Visit visit = _visitMap.get(Double.parseDouble(String.valueOf(newRow.get("visitId.sequenceNumMin"))));
                    if (visit != null)
                        newRow.put("visitId", visit.getId());
                    else
                        ctx.getLogger().warn("No visit found matching the sequence num : " + newRow.get("visitId.sequenceNumMin"));

                    newRow.remove("visitId.sequenceNumMin");
                }

                if (newRow.containsKey("AssaySpecimenId") && _assaySpecimenIdMap.containsKey(newRow.get("AssaySpecimenId")))
                {
                    newRow.put("AssaySpecimenId", _assaySpecimenIdMap.get(newRow.get("AssaySpecimenId")));
                }
                else
                    throw new ImportException("Unable to locate assaySpecimenId in the imported rows");
            }
            return newRows;
        }
    }
}
