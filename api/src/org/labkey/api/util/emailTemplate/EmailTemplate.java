package org.labkey.api.util.emailTemplate;

import org.labkey.api.util.AppProps;
import org.labkey.api.view.ActionURL;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Jan 15, 2007
 */
public abstract class EmailTemplate
{
    private static Pattern scriptPattern = Pattern.compile("%(.*?)%");
    private static List<ReplacementParam> _replacements = new ArrayList<ReplacementParam>();

    private String _name;
    private String _body;
    private String _subject;
    private String _description;
    private int _priority = 50;

    static {

        _replacements.add(new ReplacementParam("organizationName", "Organization name (set in admin console)"){
            public String getValue() {return AppProps.getInstance().getCompanyName();}
        });
        _replacements.add(new ReplacementParam("siteShortName", "Web site short name"){
            public String getValue() {return AppProps.getInstance().getSystemShortName();}
        });
        _replacements.add(new ReplacementParam("contextPath", "Web application context path"){
            public String getValue() {return AppProps.getInstance().getContextPath();}
        });
        _replacements.add(new ReplacementParam("supportLink", "Page where users can request support"){
            public String getValue() {return AppProps.getInstance().getReportAProblemPath();}
        });
        _replacements.add(new ReplacementParam("systemDescription", "Web site description"){
            public String getValue() {return AppProps.getInstance().getSystemDescription();}
        });
        _replacements.add(new ReplacementParam("systemEmail", "From address for system notification emails"){
            public String getValue() {return AppProps.getInstance().getSystemEmailAddress();}
        });
        _replacements.add(new ReplacementParam("homePageURL", "The home page of this installation"){
            public String getValue() {
                return ActionURL.getBaseServerURL();   // TODO: Use AppProps.getHomePageUrl() instead?
            }
        });
    }

    public EmailTemplate(String name)
    {
        this(name, "", "", "");
    }

    public EmailTemplate(String name, String subject, String body, String description)
    {
        _name = name;
        _subject = subject;
        _body = body;
        _description = description;
    }

    public String getName(){return _name;}
    public void setName(String name){_name = name;}
    public String getSubject(){return _subject;}
    public void setSubject(String subject){_subject = subject;}
    public String getBody(){return _body;}
    public void setBody(String body){_body = body;}
    public void setPriority(int priority){_priority = priority;}
    public int getPriority(){return _priority;}
    public String getDescription(){return _description;}
    public void setDescription(String description){_description = description;}

    public boolean isValid(String[] error)
    {
        try {
            _validate(_subject);
            _validate(_body);
            return true;
        }
        catch (Exception e)
        {
            if (error != null && error.length >= 1)
                error[0] = e.getMessage();
            return false;
        }
    }

    protected boolean _validate(String text) throws Exception
    {
        if (text != null)
        {
            Matcher m = scriptPattern.matcher(text);
            while (m.find())
            {
                String value = m.group(1);
                if (!isValidReplacement(value))
                    throw new IllegalArgumentException("Invalid template, the replacement parameter: " + value + " is unknown.");
            }
        }
        return true;
    }

    protected boolean isValidReplacement(String value)
    {
        for (ReplacementParam param : getValidReplacements())
        {
            if (param.getName().equals(value))
                return true;
        }
        return false;
    }

    public String getReplacement(String value)
    {
        for (ReplacementParam param : getValidReplacements())
        {
            if (param.getName().equals(value))
                return param.getValue();
        }
        return null;
    }

    public String renderSubject() {return render(getSubject());}
    public String renderBody() {return render(getBody());}

    protected String render(String text)
    {
        StringBuffer sb = new StringBuffer();
        Matcher m = scriptPattern.matcher(text);
        int start;
        int end = 0;
        while (m.find())
        {
            start = m.start();
            String value = m.group(1);
            sb.append(text.substring(end, start));
            sb.append(getReplacement(value));
            end = m.end();
        }
        sb.append(text.substring(end));
        return sb.toString();
    }

    public List<ReplacementParam> getValidReplacements()
    {
        return _replacements;
    }

    public static abstract class ReplacementParam
    {
        private String _name;
        private String _description;

        public ReplacementParam(String name, String description)
        {
            _name = name;
            _description = description;
        }
        public String getName(){return _name;}
        public String getDescription(){return _description;}
        public abstract String getValue();
    }
}
