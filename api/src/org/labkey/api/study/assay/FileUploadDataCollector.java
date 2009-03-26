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

package org.labkey.api.study.assay;

import org.labkey.api.exp.ExperimentException;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.Map;
import java.util.Collections;
import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;

/**
 * User: jeckels
 * Date: Jul 12, 2007
 */
public class FileUploadDataCollector extends AbstractAssayDataCollector
{
    private boolean _uploadComplete = false;

    public String getHTML(AssayRunUploadContext context)
    {
        return "<input type=\"file\" size=\"40\" name=\"uploadedFile\" />";
    }

    public String getShortName()
    {
        return "File upload";
    }

    public String getDescription()
    {
        return "Upload a data file";
    }

    public Map<String, File> createData(AssayRunUploadContext context) throws IOException, IllegalArgumentException, ExperimentException
    {
        if (_uploadComplete)
            return Collections.emptyMap();

        if (!(context.getRequest() instanceof MultipartHttpServletRequest))
            throw new IllegalStateException("Expected MultipartHttpServletRequest when posting files.");
        
        Map<String, File> files = savePostedFiles(context, Collections.singleton("uploadedFile"));
        if (files.isEmpty())
            throw new FileNotFoundException("No data file was uploaded. Please enter a file name.");
        return files;
    }

    public boolean isVisible()
    {
        return true;
    }

    public void uploadComplete(AssayRunUploadContext context)
    {
        _uploadComplete = true;
    }
}
