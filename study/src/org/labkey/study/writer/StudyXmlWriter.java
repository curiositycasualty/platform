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
package org.labkey.study.writer;

import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.XmlTokenSource;
import org.labkey.api.data.MvUtil;
import org.labkey.api.util.VirtualFile;
import org.labkey.study.model.Study;
import org.labkey.study.xml.SecurityType;
import org.labkey.study.xml.StudyDocument;

import java.io.PrintWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.Map;

/**
 * User: adam
 * Date: Apr 23, 2009
 * Time: 9:39:31 AM
 */

// This writer is largely responsible for study.xml.  It constructs the StudyDocument (xml bean used to read/write study.xml)
//  that gets added to the ExportContext, writes the top-level study attributes, and writes out the bean when it's complete.
//  However, each top-level writer is responsible for their respective elements in study.xml -- VisitMapWriter handles "visits"
//  element, DataSetWriter2 is responsible for "datasets" element, etc.  As a result, StudyXmlWriter must be invoked after all
//  writers are done modifying the StudyDocument.  Locking the StudyDocument after writing it out helps ensure this ordering.
public class StudyXmlWriter implements Writer<Study>
{
    public String getSelectionText()
    {
        return null;
    }

    public void write(Study study, ExportContext ctx, VirtualFile fs) throws Exception
    {
        StudyDocument.Study studyXml = ctx.getStudyXml();

        // Study attributes
        studyXml.setLabel(study.getLabel());
        studyXml.setDateBased(study.isDateBased());

        if (null != study.getStartDate())
        {
            Calendar startDate = Calendar.getInstance();
            startDate.setTime(study.getStartDate());
            studyXml.setStartDate(startDate);        // TODO: TimeZone?
        }

        studyXml.setSecurityType(SecurityType.Enum.forString(study.getSecurityType().name()));

        // Export the MV indicators -- always write these, even if they're blank
        Map<String, String> mvMap = MvUtil.getIndicatorsAndLabels(study.getContainer());
        StudyDocument.Study.MissingValueIndicators mvXml = studyXml.addNewMissingValueIndicators();

        for (Map.Entry<String, String> mv : mvMap.entrySet())
        {
            StudyDocument.Study.MissingValueIndicators.MissingValueIndicator indXml = mvXml.addNewMissingValueIndicator();
            indXml.setIndicator(mv.getKey());
            indXml.setLabel(mv.getValue());
        }

        // This gets called last, after all other writers have populated the other sections.  Save the study.xml
        PrintWriter pw = fs.getPrintWriter("study.xml");
        saveDoc(pw, ctx.getStudyDocument());
        ctx.lockStudyDocument();
    }

    public static StudyDocument getStudyDocument()
    {
        StudyDocument doc = StudyDocument.Factory.newInstance();
        doc.addNewStudy();
        return doc;
    }

    public static void saveDoc(PrintWriter pw, XmlTokenSource doc) throws IOException
    {
        XmlOptions options = new XmlOptions();
        options.setSavePrettyPrint();
        options.setUseDefaultNamespace();
        doc.save(pw, options);
        pw.close();
    }
}
