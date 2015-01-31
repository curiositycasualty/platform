package org.labkey.api.announcements.api;

import org.json.JSONObject;

/**
 * Created by Marty on 1/19/2015.
 */
public interface Tour
{
    public Integer getRowId();
    public void setRowId(Integer id);
    public String getTitle();
    public void setTitle(String title);
    public String getDescription();
    public void setDescription(String description);
    public String getJson();
    public void setJson(String json);
    public Integer getMode();
    public void setMode(Integer mode);
    public JSONObject toJSON();
}
