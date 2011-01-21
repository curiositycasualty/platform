/*
 * Copyright (c) 2005-2011 LabKey Corporation
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
package org.labkey.api.view;

import java.awt.*;

/**
 * User: jeckels
 * Date: Oct 11, 2005
 */
public class WebTheme
{
    private final String _friendlyName;
    private final String _stylesheet;

    private final String _textColor;
    private final String _linkColor;
    private final String _gridColor;
    private final String _primaryBGColor;
    private final String _secondBGColor;
    private final String _borderTitleColor;
    private final String _webpartColor;

    WebTheme(String friendlyName, String textColor, String linkColor, String gridColor, String primaryBackgroundColor,
             String secondaryBackgroundColor, String borderTitleColor, String webPartColor)
    {
        _friendlyName = friendlyName;

        parseColor(textColor);
        parseColor(linkColor);
        parseColor(gridColor);
        parseColor(primaryBackgroundColor);
        parseColor(secondaryBackgroundColor);
        parseColor(borderTitleColor);
        parseColor(webPartColor);
        
        _textColor = textColor;
        _linkColor = linkColor;
        _gridColor = gridColor;
        _primaryBGColor = primaryBackgroundColor;
        _secondBGColor = secondaryBackgroundColor;
        _borderTitleColor = borderTitleColor;
        _webpartColor = webPartColor;
        
        _stylesheet = "stylesheet.css";
    }

    private Color parseColor(String s)
    {
        if (null == s)
        {
            throw new IllegalArgumentException("You must specify a value for every color");
        }
        if (s.length() != 6)
        {
            throw new IllegalArgumentException("Colors must be 6 hex digits, but was " + s);
        }
        int r = Integer.parseInt(s.substring(0, 2), 16);
        int g = Integer.parseInt(s.substring(2, 4), 16);
        int b = Integer.parseInt(s.substring(4, 6), 16);
        return new Color(r, g, b);
    }

    public String getStyleSheet()
    {
        return _stylesheet;
    }

    public String getFriendlyName()
    {
        return _friendlyName;
    }

    public String toString()
    {
        return _friendlyName;
    }

    private static String toRGB(Color c)
    {
        String rgb = Integer.toHexString(0x00ffffff & c.getRGB());
        return rgb.length() == 6 ? rgb : "000000".substring(rgb.length()) + rgb;
    }

    public String getTextColor()
    {
        return _textColor;
    }

    public String getLinkColor()
    {
        return _linkColor;
    }

    public String getGridColor()
    {
        return _gridColor;
    }

    public String getPrimaryBackgroundColor()
    {
        return _primaryBGColor;
    }

    public String getSecondaryBackgroundColor()
    {
        return _secondBGColor;
    }

    public String getBorderTitleColor()
    {
        return _borderTitleColor;
    }

    public String getWebPartColor()
    {
        return _webpartColor;
    }
}

