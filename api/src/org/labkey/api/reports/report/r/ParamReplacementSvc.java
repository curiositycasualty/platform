/*
 * Copyright (c) 2008 LabKey Corporation
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

package org.labkey.api.reports.report.r;

import org.apache.log4j.Logger;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: May 5, 2008
 */
public class ParamReplacementSvc
{
    private static Logger _log = Logger.getLogger(ParamReplacementSvc.class);
    private static final ParamReplacementSvc _instance = new ParamReplacementSvc();
    private static Map<String, String> _outputSubstitutions = new HashMap<String, String>();

    public static Pattern scriptPattern = Pattern.compile("\\$\\{(.*?)\\}");

    private ParamReplacementSvc(){}

    public static ParamReplacementSvc get()
    {
        return _instance;
    }

    public void registerHandler(ParamReplacement rout)
    {
        if (_outputSubstitutions.containsKey(rout.getId()))
            throw new IllegalStateException("Replacement parameter type: " + rout.getId() + " has previously been registered");

        if (!rout.getId().endsWith(":"))
            throw new IllegalArgumentException("The ID of a replacement parameter must end with a ':'");

        _outputSubstitutions.put(rout.getId(), rout.getClass().getName());
    }

    /**
     * Returns a ParamReplacement from a replacement parameter id
     */
    public ParamReplacement getHandlerInstance(String id)
    {
        if (_outputSubstitutions.containsKey(id))
        {
            try {
                String className = _outputSubstitutions.get(id);
                return (ParamReplacement)Class.forName(className).newInstance();
            }
            catch (Exception e)
            {
                _log.error("Unable to create report output handler", e);
            }
        }
        return null;
    }

    /**
     * Returns a ParamReplacement from a replacement parameter of the form: <id><name>
     */
    public ParamReplacement getHandler(String token)
    {
        return fromToken(token);
    }

    /**
     * Finds all the replacement parameters for a given script block
     */
    public List<ParamReplacement> getParamReplacements(String script)
    {
        List<ParamReplacement> params = new ArrayList<ParamReplacement>();
        if (script != null)
        {
            Matcher m = ParamReplacementSvc.scriptPattern.matcher(script);

            while (m.find())
            {
                ParamReplacement param = fromToken(m.group(1));
                if (param != null)
                    params.add(param);
            }
        }
        return params;
    }

    private ParamReplacement fromToken(String value)
    {
        int idx = value.indexOf(':');
        if (idx != -1)
        {
            String id = value.substring(0, idx+1);
            String name = value.substring(idx+1);

            ParamReplacement param = getHandlerInstance(id);
            if (param != null)
            {
                param.setName(name);
                return param;
            }
        }
        return null;
    }

    /**
     * Finds and processes all replacement parameters for a given script block. The
     * returned string will have all valid replacement references converted.
     *
     * @param parentDirectory - the parent directory to create the output files for each param replacement.
     * @param outputReplacements - the list of processed replacements found in the source script.
     */
    public String processParamReplacement(String script, File parentDirectory, List<ParamReplacement> outputReplacements) throws Exception
    {
        Matcher m = ParamReplacementSvc.scriptPattern.matcher(script);
        StringBuffer sb = new StringBuffer();

        while (m.find())
        {
            ParamReplacement param = fromToken(m.group(1));
            if (param != null)
            {
                File resultFile = param.convertSubstitution(parentDirectory);
                String resultFileName = resultFile.getAbsolutePath();
                resultFileName = resultFileName.replaceAll("\\\\", "/");

                outputReplacements.add(param);
                m.appendReplacement(sb, resultFileName);
            }
        }
        m.appendTail(sb);

        return sb.toString();
    }

    public void toFile(List<ParamReplacement> outputSubst, File file) throws Exception
     {
         BufferedWriter bw = null;
         try {
             bw = new BufferedWriter(new FileWriter(file));
             for (ParamReplacement output : outputSubst)
             {
                 if (output.getName() != null && output.getFile() != null)
                    bw.write(output.getId() + '\t' + output.getName() + '\t' + output.getFile().getAbsolutePath() + '\n');
             }
         }
         finally
         {
             if (bw != null)
                 try {bw.close();} catch (IOException ioe) {}
         }
     }

     public List<ParamReplacement> fromFile(File file) throws Exception
     {
         BufferedReader br = null;
         List<ParamReplacement> outputSubst = new ArrayList();

         try {
             if (file.exists())
             {
                 br = new BufferedReader(new FileReader(file));
                 String l;
                 while ((l = br.readLine()) != null)
                 {
                     String[] parts = l.split("\\t");
                     if (parts.length == 3)
                     {
                         ParamReplacement handler = getHandlerInstance(parts[0]);
                         if (handler != null)
                         {
                             handler.setName(parts[1]);
                             handler.setFile(new File(parts[2]));

                             outputSubst.add(handler);
                         }
                     }
                 }
             }
         }
         finally
         {
             if (br != null)
                 try {br.close();} catch(IOException ioe) {}
         }
         return outputSubst;
     }
}


