ALTER TABLE exp.PropertyDescriptor ADD COLUMN DerivationDataScope VARCHAR(20) NULL;
ALTER TABLE exp.Material ADD COLUMN RootMaterialLSID LSIDtype NULL;
ALTER TABLE exp.Material ADD COLUMN AliquotedFromLSID LSIDtype NULL;