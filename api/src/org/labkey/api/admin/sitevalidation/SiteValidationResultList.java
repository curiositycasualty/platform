package org.labkey.api.admin.sitevalidation;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.view.ActionURL;

import java.util.ArrayList;
import java.util.List;

/**
 * User: tgaluhn
 * Date: 4/16/2015
 *
 * Top level object to hold a list of SiteValidationResult's, provide helper add/create methods, and filter results on level
 */
public class SiteValidationResultList
{
    List<SiteValidationResult> results = new ArrayList<>();
    private String eol = "<br/>";

    public SiteValidationResult addResult(SiteValidationResult.Level level, String message)
    {
        return addResult(level, message, null);
    }

    public SiteValidationResult addResult(SiteValidationResult.Level level, String message, ActionURL link)
    {
        SiteValidationResult result = level.create(message, link);
        results.add(result);
        return result;
    }

    public SiteValidationResult addBlank()
    {
        return addInfo("");
    }

    public SiteValidationResult addInfo(String message)
    {
        return addInfo(message, null);
    }

    public SiteValidationResult addInfo(String message, ActionURL link)
    {
        return addResult(SiteValidationResult.Level.INFO, message, link);
    }

    public SiteValidationResult addWarn(String message)
    {
        return addWarn(message, null);
    }

    public SiteValidationResult addWarn(String message, ActionURL link)
    {
        return addResult(SiteValidationResult.Level.WARN, message, link);
    }

    public SiteValidationResult addError(String message)
    {
        return addError(message, null);
    }

    public SiteValidationResult addError(String message, ActionURL link)
    {
        return addResult(SiteValidationResult.Level.ERROR, message, link);
    }

    public List<SiteValidationResult> getResults()
    {
        return getResults(null);
    }

    public List<SiteValidationResult> getResults(@Nullable SiteValidationResult.Level level)
    {
        if (null == level)
            return results;

        List<SiteValidationResult> filteredResults = new ArrayList<>();
        for (SiteValidationResult result : results)
        {
            if (level.equals(result.getLevel()))
                filteredResults.add(result);
        }
        return filteredResults;
    }

    public void addAll(SiteValidationResultList resultsToAdd)
    {
        this.results.addAll(resultsToAdd.getResults());
    }

    public void setEol(String eol)
    {
        this.eol = eol;
    }

    public String getResultsString()
    {
        if (results.isEmpty())
            return null;

        StringBuilder sb = new StringBuilder();

        for (SiteValidationResult result : results)
            sb.append(result.getMessage()).append(eol);

        return sb.toString();
    }
}
