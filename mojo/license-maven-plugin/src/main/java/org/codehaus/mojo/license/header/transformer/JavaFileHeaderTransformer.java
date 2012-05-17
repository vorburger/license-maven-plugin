package org.codehaus.mojo.license.header.transformer;

/*
 * #%L
 * License Maven Plugin
 * %%
 * Copyright (C) 2008 - 2011 CodeLutin, Codehaus, Tony Chemit
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as 
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

/**
 * Implementation of {@link FileHeaderTransformer} for java format.
 *
 * @author tchemit <chemit@codelutin.com>
 * @plexus.component role-hint="java"
 * @since 1.0
 */
public class JavaFileHeaderTransformer
    extends AbstractFileHeaderTransformer
{

    /**
     * Default constructor.
     */
    public JavaFileHeaderTransformer()
    {
        super( "java", "header transformer with java comment style", "/*", " */", " * " );
    }

    /**
     * {@inheritDoc}
     */
    public String[] getDefaultAcceptedExtensions()
    {
        return new String[]{ "java", "groovy", "css", "cs", "as", "aj", "c", "h", "cpp", "js", "json"

        };
    }

}
