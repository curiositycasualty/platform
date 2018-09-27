package org.labkey.experiment.api;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.query.ExpDataProtocolInputTable;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;

public class ExpDataProtocolInputTableImpl extends ExpProtocolInputTableImpl<ExpDataProtocolInputTable.Column> implements ExpDataProtocolInputTable
{
    protected ExpDataProtocolInputTableImpl(String name, UserSchema schema)
    {
        super(name, ExperimentServiceImpl.get().getTinfoProtocolInput(), schema);

        getFilter().addCondition(FieldKey.fromParts("objectType"), ExpData.DEFAULT_CPAS_TYPE);
    }

    @Override
    protected void populateColumns()
    {
        addColumn(Column.RowId);
        addColumn(Column.Name);
        setTitleColumn(Column.Name.toString());
        addColumn(Column.LSID);
        addColumn(Column.Protocol);
        addColumn(Column.Input);
        addColumn(Column.DataClass);
        addColumn(Column.MinOccurs);
        addColumn(Column.MaxOccurs);
    }

    @Override
    public ColumnInfo createColumn(String alias, Column column)
    {
        switch (column)
        {
            case RowId:
                return createRowIdColumn(alias);
            case Name:
                return createNameColumn(alias);
            case LSID:
                return createLsidColumn(alias);
            case Protocol:
                return createProtocolColumn(alias);
            case Input:
                return createInputColumn(alias);
            case DataClass:
                return createDataClassColumn(alias);
            case MinOccurs:
                return createMinOccursColumn(alias);
            case MaxOccurs:
                return createMaxOccursColumn(alias);
        }
        throw new IllegalArgumentException("Unknown column " + column);
    }
}