package org.labkey.di.columnTransforms;

import org.labkey.api.di.columnTransform.ColumnTransform;

/**
 * User: tgaluhn
 * Date: 9/26/2016
 *
 * Used in automated testing and gives an example of implementing a transform.
 * Prepends the value of the "id" column of the source query
 * to the value of the source column specified in the ETL xml,
 * then appends the value of the "myConstant" constant set in the xml.
 */
public class TestColumnTransform extends ColumnTransform
{
    @Override
    protected Object doTransform(Object inputValue)
    {
        Object prefix = getInputValue("id");
        String prefixStr = null == prefix ? "" : prefix.toString();
        return prefixStr + "_" + inputValue + "_" + getConstant("myConstant");
    }
}
