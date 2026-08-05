package de.cas_ual_ty.dueldimension.util;

import java.io.File;
import java.io.FileFilter;

public interface FileFilterSuffix extends FileFilter
{
    String getRequiredSuffix();
    
    @Override
    default boolean accept(File f)
    {
        return f.isFile() && f.getName().toLowerCase().endsWith(getRequiredSuffix());
    }
}
