/*
 * Copyright (c) 2013 LabKey Corporation
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

import org.apache.commons.io.IOUtils;
import org.apache.xmlbeans.XmlObject;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.writer.VirtualFile;
import org.labkey.study.model.CustomParticipantView;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.xml.StudyDocument;
import org.labkey.study.xml.studyViews.ViewsDocument;
import org.springframework.validation.BindException;

import java.io.InputStream;

/**
 * User: klum
 * Date: 10/8/13
 */
public class StudyViewsImporter implements InternalStudyImporter
{
    @Override
    public String getDescription()
    {
        return "custom participant view";
    }

    @Override
    public void process(StudyImportContext ctx, VirtualFile root, BindException errors) throws Exception
    {
        StudyImpl study = StudyManager.getInstance().getStudy(ctx.getContainer());
        StudyDocument.Study.StudyViews viewsXml = ctx.getXml().getStudyViews();

        if (viewsXml != null)
        {
            VirtualFile folder = root.getDir(viewsXml.getDir());
            String settingsFileName = viewsXml.getSettings();

            if (settingsFileName != null)
            {
                XmlObject doc = folder.getXmlBean(settingsFileName);
                if (doc instanceof ViewsDocument)
                {
                    XmlBeansUtil.validateXmlDocument(doc, "Validating ViewsDocument during study import");
                    ViewsDocument.Views views = ((ViewsDocument) doc).getViews();
                    if (views != null)
                    {
                        ViewsDocument.Views.ParticipantView participantView = views.getParticipantView();
                        if (participantView != null)
                        {
                            String participantFileName = participantView.getFile();
                            try (InputStream is = folder.getInputStream(participantFileName))
                            {
                                if (is != null)
                                {
                                    CustomParticipantView view = StudyManager.getInstance().getCustomParticipantView(study);
                                    if (view == null)
                                        view = new CustomParticipantView();

                                    if (!view.isModuleParticipantView())
                                    {
                                        view.setBody(IOUtils.toString(is));
                                        view.setActive(participantView.getActive());
                                        StudyManager.getInstance().saveCustomParticipantView(study, ctx.getUser(), view);
                                    }
                                    else
                                        ctx.getLogger().warn("Unable to load the custom participant view, there is an active module with an existing custom participant view that cannot be overwritten.");
                                }
                                else
                                    ctx.getLogger().fatal("Unable to load custom participant view file specified in settings : " + participantFileName);
                            }
                        }
                    }
                }
                else
                    ctx.getLogger().fatal("Unable to load the study views setting file : " + settingsFileName);
            }
        }
    }
}
