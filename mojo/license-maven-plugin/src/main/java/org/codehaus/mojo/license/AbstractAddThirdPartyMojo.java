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

package org.codehaus.mojo.license;

import org.apache.commons.collections.CollectionUtils;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.codehaus.mojo.license.api.DefaultThirdPartyHelper;
import org.codehaus.mojo.license.api.DependenciesTool;
import org.codehaus.mojo.license.api.ThirdPartyHelper;
import org.codehaus.mojo.license.api.ThirdPartyTool;
import org.codehaus.mojo.license.api.ThirdPartyToolException;
import org.codehaus.mojo.license.model.LicenseMap;
import org.codehaus.mojo.license.utils.FileUtil;
import org.codehaus.mojo.license.utils.MojoHelper;
import org.codehaus.mojo.license.utils.SortedProperties;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

/**
 * Abstract mojo for all third-party mojos.
 *
 * @author tchemit <chemit@codelutin.com>
 * @since 1.0
 */
public abstract class AbstractAddThirdPartyMojo
    extends AbstractLicenseMojo
{

    /**
     * Directory where to generate files.
     *
     * @parameter expression="${license.outputDirectory}" default-value="${project.build.directory}/generated-sources/license"
     * @required
     * @since 1.0
     */
    private File outputDirectory;

    /**
     * File where to wirte the third-party file.
     *
     * @parameter expression="${license.thirdPartyFilename}" default-value="THIRD-PARTY.txt"
     * @required
     * @since 1.0
     */
    private String thirdPartyFilename;

    /**
     * A flag to use the missing licenses file to consolidate the THID-PARTY file.
     *
     * @parameter expression="${license.useMissingFile}"  default-value="false"
     * @since 1.0
     */
    private boolean useMissingFile;

    /**
     * The file where to fill the license for dependencies with unknwon license.
     *
     * @parameter expression="${license.missingFile}"  default-value="src/license/THIRD-PARTY.properties"
     * @since 1.0
     */
    private File missingFile;

    /**
     * To merge licenses in final file.
     * <p/>
     * Each entry represents a merge (first license is main license to keep), licenses are separated by {@code |}.
     * <p/>
     * Example :
     * <p/>
     * <pre>
     * &lt;licenseMerges&gt;
     * &lt;licenseMerge&gt;The Apache Software License|Version 2.0,Apache License, Version 2.0&lt;/licenseMerge&gt;
     * &lt;/licenseMerges&gt;
     * &lt;/pre&gt;
     *
     * @parameter
     * @since 1.0
     */
    private List<String> licenseMerges;

    /**
     * To specify some licenses to include (separated by {@code |}).
     * <p/>
     * If this parameter is filled and a license is not in this {@code whitelist} then build will failed when property
     * {@link #failIfWarning} is setted on.
     *
     * @parameter expression="${license.includedLicenses}" default-value=""
     * @since 1.1
     */
    private String includedLicenses;

    /**
     * To specify some licenses to exclude (separated by {@code |}).
     * <p/>
     * If a such license is found then build will failed when property
     * {@link #failIfWarning} is setted on.
     *
     * @parameter expression="${license.excludedLicenses}" default-value=""
     * @since 1.1
     */
    private String excludedLicenses;

    /**
     * The path of the bundled third party file to produce when
     * {@link #generateBundle} is on.
     * <p/>
     * <b>Note:</b> This option is not available for {@code pom} module types.
     *
     * @parameter expression="${license.bundleThirdPartyPath}"  default-value="META-INF/${project.artifactId}-THIRD-PARTY.txt"
     * @since 1.0
     */
    private String bundleThirdPartyPath;

    /**
     * A flag to copy a bundled version of the third-party file. This is usefull
     * to avoid for a final application collision name of third party file.
     * <p/>
     * The file will be copied at the {@link #bundleThirdPartyPath} location.
     *
     * @parameter expression="${license.generateBundle}"  default-value="false"
     * @since 1.0
     */
    private boolean generateBundle;

    /**
     * To force generation of the third-party file even if every thing is up to date.
     *
     * @parameter expression="${license.force}"  default-value="false"
     * @since 1.0
     */
    private boolean force;

    /**
     * A flag to fail the build if at least one dependency was detected without a license.
     *
     * @parameter expression="${license.failIfWarning}"  default-value="false"
     * @since 1.0
     */
    private boolean failIfWarning;

    /**
     * A flag to change the grouping of the generated THIRD-PARTY file.
     * <p/>
     * By default, group by dependecies.
     * <p/>
     * If sets to {@code true}, the it will group by license type.
     * <p/>
     * <strong>Note:</strong> This parameter is deprecated, please now use the
     * correct template for this purpose (value {@code /org/codehaus/mojo/license/third-party-file-groupByLicense.ftl}
     * to parameter {@code fileTemplate})
     *
     * @parameter expression="${license.groupByLicense}"  default-value="false"
     * @since 1.0
     * @deprecated since 1.1, please use the correct value for the parameter {@code fileTemplate}
     */
    @Deprecated
    private boolean groupByLicense;

    /**
     * Template used to build the third-party file.
     * <p/>
     * (This template use freemarker).
     *
     * @parameter expression="${license.fileTemplate}" default-value="/org/codehaus/mojo/license/third-party-file.ftl"
     * @since 1.1
     */
    private String fileTemplate;

    /**
     * Local Repository.
     *
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     * @since 1.0.0
     */
    private ArtifactRepository localRepository;

    /**
     * Remote repositories used for the project.
     *
     * @parameter expression="${project.remoteArtifactRepositories}"
     * @required
     * @readonly
     * @since 1.0.0
     */
    private List remoteRepositories;

    /**
     * third party tool.
     *
     * @component
     * @readonly
     * @since 1.0
     */
    private ThirdPartyTool thirdPartyTool;

    /**
     * dependencies tool.
     *
     * @component
     * @readonly
     * @since 1.1
     */
    private DependenciesTool dependenciesTool;

    private ThirdPartyHelper helper;

    private SortedMap<String, MavenProject> projectDependencies;

    private LicenseMap licenseMap;

    private SortedSet<MavenProject> unsafeDependencies;

    private File thirdPartyFile;

    private SortedProperties unsafeMappings;

    private boolean doGenerate;

    private boolean doGenerateBundle;

    protected abstract SortedMap<String, MavenProject> loadDependencies();

    protected abstract SortedProperties createUnsafeMapping()
        throws ProjectBuildingException, IOException, ThirdPartyToolException;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void init()
        throws Exception
    {

        Log log = getLog();

        if ( log.isDebugEnabled() )
        {

            // always be verbose in debug mode
            setVerbose( true );
        }

        thirdPartyFile = new File( getOutputDirectory(), thirdPartyFilename );

        long buildTimestamp = getBuildTimestamp();

        if ( isVerbose() )
        {
            log.info( "Build start   at : " + buildTimestamp );
            log.info( "third-party file : " + thirdPartyFile.lastModified() );
        }

        doGenerate = isForce() || !thirdPartyFile.exists() || buildTimestamp > thirdPartyFile.lastModified();

        if ( groupByLicense )
        {
            fileTemplate = "/org/codehaus/mojo/license/third-party-file-groupByLicense.ftl";
        }
        if ( generateBundle )
        {

            File bundleFile = FileUtil.getFile( getOutputDirectory(), bundleThirdPartyPath );

            if ( isVerbose() )
            {
                log.info( "bundle third-party file : " + bundleFile.lastModified() );
            }
            doGenerateBundle = isForce() || !bundleFile.exists() || buildTimestamp > bundleFile.lastModified();
        }
        else
        {

            // not generating bundled file
            doGenerateBundle = false;
        }

        projectDependencies = loadDependencies();

        licenseMap = getHelper().createLicenseMap( projectDependencies );

        unsafeDependencies = getHelper().getProjectsWithNoLicense( licenseMap );

        if ( !CollectionUtils.isEmpty( unsafeDependencies ) && isUseMissingFile() && isDoGenerate() )
        {

            // load unsafeMapping
            unsafeMappings = createUnsafeMapping();
        }

        getHelper().mergeLicenses( licenseMerges, licenseMap );
    }

    protected ThirdPartyHelper getHelper()
    {
        if ( helper == null )
        {
            helper =
                new DefaultThirdPartyHelper( getProject(), getEncoding(), isVerbose(), dependenciesTool, thirdPartyTool,
                                             localRepository, remoteRepositories, getLog() );
        }
        return helper;
    }

    public List<String> getExcludedLicenses()
    {
        String[] split = excludedLicenses == null ? new String[0] : excludedLicenses.split( "\\s*\\|\\s*" );
        return Arrays.asList( split );
    }

    public List<String> getIncludedLicenses()
    {
        String[] split = includedLicenses == null ? new String[0] : includedLicenses.split( "\\s*\\|\\s*" );
        return Arrays.asList( split );
    }

    protected boolean checkUnsafeDependencies()
    {
        SortedSet<MavenProject> unsafeDependencies = getUnsafeDependencies();
        boolean unsafe = !CollectionUtils.isEmpty( unsafeDependencies );
        if ( unsafe )
        {
            Log log = getLog();
            log.warn( "There is " + unsafeDependencies.size() + " dependencies with no license :" );
            for ( MavenProject dep : unsafeDependencies )
            {

                // no license found for the dependency
                log.warn( " - " + MojoHelper.getArtifactId( dep.getArtifact() ) );
            }
        }
        return unsafe;
    }

    protected boolean checkForbiddenLicenses()
    {
        List<String> includedLicenses = getIncludedLicenses();
        List<String> excludeLicenses = getExcludedLicenses();
        Set<String> unsafeLicenses = new HashSet<String>();
        if ( CollectionUtils.isNotEmpty( excludeLicenses ) )
        {
            Set<String> licenses = getLicenseMap().keySet();
            getLog().info( "Excluded licenses (blacklist): " + excludeLicenses );

            for ( String excludeLicense : excludeLicenses )
            {
                if ( licenses.contains( excludeLicense ) )
                {
                    //bad license found
                    unsafeLicenses.add( excludeLicense );
                }
            }
        }

        if ( CollectionUtils.isNotEmpty( includedLicenses ) )
        {
            Set<String> licenses = getLicenseMap().keySet();
            getLog().info( "Included licenses (whitelist): " + includedLicenses );

            for ( String license : licenses )
            {
                if ( !includedLicenses.contains( license ) )
                {
                    //bad license found
                    unsafeLicenses.add( license );
                }
            }
        }

        boolean safe = CollectionUtils.isEmpty( unsafeLicenses );

        if ( !safe )
        {
            Log log = getLog();
            log.warn( "There is " + unsafeLicenses.size() + " forbidden licenses used:" );
            for ( String unsafeLicense : unsafeLicenses )
            {

                SortedSet<MavenProject> deps = getLicenseMap().get( unsafeLicense );
                StringBuilder sb = new StringBuilder();
                sb.append( "License " ).append( unsafeLicense ).append( "used by " ).append( deps.size() ).append(
                    " dependencies:" );
                for ( MavenProject dep : deps )
                {
                    sb.append( "\n -" ).append( MojoHelper.getArtifactName( dep ) );
                }

                log.warn( sb.toString() );
            }
        }
        return safe;
    }

    protected void writeThirdPartyFile()
        throws IOException
    {

        if ( doGenerate )
        {

            thirdPartyTool.writeThirdPartyFile( getLicenseMap(), thirdPartyFile, isVerbose(), getEncoding(),
                                                fileTemplate );
        }

        if ( doGenerateBundle )
        {

            thirdPartyTool.writeBundleThirdPartyFile( thirdPartyFile, outputDirectory, bundleThirdPartyPath );
        }
    }

    public File getOutputDirectory()
    {
        return outputDirectory;
    }

    public boolean isFailIfWarning()
    {
        return failIfWarning;
    }

    public SortedMap<String, MavenProject> getProjectDependencies()
    {
        return projectDependencies;
    }

    public SortedSet<MavenProject> getUnsafeDependencies()
    {
        return unsafeDependencies;
    }

    public LicenseMap getLicenseMap()
    {
        return licenseMap;
    }

    public boolean isUseMissingFile()
    {
        return useMissingFile;
    }

    public File getMissingFile()
    {
        return missingFile;
    }

    public SortedProperties getUnsafeMappings()
    {
        return unsafeMappings;
    }

    public boolean isForce()
    {
        return force;
    }

    public boolean isDoGenerate()
    {
        return doGenerate;
    }

    public boolean isDoGenerateBundle()
    {
        return doGenerateBundle;
    }
}