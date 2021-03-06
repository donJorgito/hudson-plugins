/**
 *	 __                                        
 *	/\ \      __                               
 *	\ \ \/'\ /\_\    ___     ___   __  __  __  
 *	 \ \ , < \/\ \ /' _ `\  / __`\/\ \/\ \/\ \ 
 *	  \ \ \\`\\ \ \/\ \/\ \/\ \L\ \ \ \_/ \_/ \
 *	   \ \_\ \_\ \_\ \_\ \_\ \____/\ \___x___/'
 *	    \/_/\/_/\/_/\/_/\/_/\/___/  \/__//__/  
 *                                          
 * Copyright (c) 1999-present Kinow
 * Casa Verde - S�o Paulo - SP. Brazil.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Kinow ("Confidential Information").  You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Kinow.                                      
 * 
 * @author Bruno P. Kinoshita - http://www.kinoshita.eti.br
 * @since 7 april, 2010 
 */
package hudson.plugins.ccm;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.plugins.ccm.config.CCMConfigCallable;
import hudson.plugins.ccm.config.CCMResultCallable;
import hudson.plugins.ccm.model.CCM;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;


/**
 * <p>
* When the user configures the project and enables this builder,
* {@link CCMBuilderDescriptor#newInstance(StaplerRequest)} is invoked
* and a new {@link CcmBuilder} is created. The created
* instance is persisted to the project configuration XML by using
* XStream, so this allows you to use instance fields (like {@link #ccmName})
* to remember the configuration.
*
* <p>
* When a build is performed, the {@link #perform(Build, Launcher, BuildListener)} method
* will be invoked. 
* 
* @author Bruno P. Kinoshita - http://www.kinoshita.eti.br
* @since 1.0
*/
public class CCMBuilder 
extends Builder 
implements Serializable
{

	/**
	 * Identifies {@link CCM} to be used.
	 */
    private final String ccmName;
    /**
     * The list of source folders CCM must scan.
     */
    private final String srcFolders;
    /**
     * List of files that CCM must not scan.
     */
    private final String excludeFiles;
    /**
     * List of folders that CCM must not scan.
     */
    private final String excludeFolders;
    /**
     * List of functions that CCM must not scan.
     */
    private final String excludeFunctions;
    /**
     * Whether CCM should traverse the directories recursivelly or not.
     */
    private final Boolean recursive;
    /**
     * Whether CCM should output its report in XML format or not.
     */
    private final Boolean outputXml;
    /**
     * Maximum of metrics to be shown in the report. 
     */
    private final String numMetrics;
	
    @Extension
    public static final CCMBuilderDescriptor DESCRIPTOR = new CCMBuilderDescriptor();
    
    /**
     * Name of generated config file for CCM.
     */
    public final static String CCM_CONFIG_FILE = "ccm.config.xml";
    
    /**
     * Name of generated result file of CCM.
     */
    public static final String CCM_RESULT_FILE = "ccm.result.xml";
	
    /**
     * Default number of metrics.
     */
    public static final Integer DEFAULT_NUMBER_OF_METRICS = 30;
    
    @DataBoundConstructor
    public CCMBuilder(
    		String ccmName, 
    		String srcFolders,
    		String excludeFiles, 
    		String excludeFolders, 
    		String excludeFunctions, 
    		Boolean recursive, 
    		Boolean outputXml, 
    		String numMetrics) 
    {
		super();
		this.ccmName = ccmName;
		this.srcFolders = srcFolders;
		this.excludeFiles = excludeFiles;
		this.excludeFolders = excludeFolders;
		this.excludeFunctions = excludeFunctions;
		this.recursive = recursive;
		this.outputXml = outputXml;
		this.numMetrics = ((numMetrics == null || numMetrics.length()<=0) ? DEFAULT_NUMBER_OF_METRICS.toString() : numMetrics);
	}
    
    public String getCcmName() 
    {
		return ccmName;
	}

	public String getSrcFolders() 
	{
		return srcFolders;
	}
	
	public String getExcludeFiles()
	{
		return excludeFiles;
	}
	
	public String getExcludeFolders()
	{
		return excludeFolders;
	}
	
	public String getExcludeFunctions()
	{
		return excludeFunctions;
	}

	public Boolean isRecursive() 
	{
		return recursive;
	}
	
	public Boolean getRecursive() 
	{
		return recursive;
	}

	public Boolean isOutputXml() 
	{
		return outputXml;
	}

	public String getNumMetrics() 
	{
		return numMetrics;
	}
	
	public Descriptor<Builder> getDescriptor()
	{
		return DESCRIPTOR;
	}
	
	/**
	 * <p>Even though we may have many installations of the CCM in Hudson, CCM 
	 * Plugin grabs the first one that it finds.</p>
	 * 
	 * @return The {@link CCMBuilderInstallation} containing details of the 
	 * CCM installed.
	 */
	public CCMBuilderInstallation getCCM()
    {
		CCMBuilderInstallation foundInstallation = null;
    	
    	for ( CCMBuilderInstallation installation : DESCRIPTOR.getInstallations() )
    	{
    		if ( this.getCcmName() != null && installation.getName().equals(this.getCcmName()))
    		{
    			foundInstallation =  installation;
    		}
    	}
    	
    	return foundInstallation;
    }
    
	/**
	 * <p>Called when the job is executed.</p>
	 * 
	 * <p>It calls the CCM.exe executable passing the config xml file created using 
	 * the inputs provided by the user. Then it redirects the output of the 
	 * command (using >) to a new file (overwriting it if already created).</p>
	 * 
	 * <p>Later this output xml if processed by another extension point, the 
	 * {@link CCMPublisher}.</p>
	 */
	@Override
	public boolean perform(
			final AbstractBuild<?, ?> build, 
			final Launcher launcher,
			final BuildListener listener ) 
	throws InterruptedException, IOException 
	{
		// List of arguments
		final ArgumentListBuilder args = new ArgumentListBuilder();
    	
		// CCM installation
    	final CCMBuilderInstallation installation = getCCM();
    	
    	// ------------------------- 
    	// Check CCM installation    
    	// ------------------------- 
    	if ( installation == null )
    	{
    		listener.error("Missing CCM installation");
    		build.setResult(Result.FAILURE);
    		return false;
    	} 
    	
    	// ------------------------- 
    	// Preparing arguments list   
    	// ------------------------- 
    	
    	// Path to CCM.exe
    	final String pathToCCM = installation.getExecutable(launcher);
    	listener.getLogger().println("Path To CCM.exe: " + pathToCCM);
    	args.add("\"");
    	args.add(pathToCCM);		
    	
    	final FilePath workspace = build.getWorkspace();
    	// ------------------------- 
    	// Creating CCM config file   
    	// ------------------------- 
        
    	// create project ccm config file and result file
        
    	args.addKeyValuePairs("-P:",build.getBuildVariables());
    	
        CCMConfigCallable ccmConfigGenerator = new CCMConfigCallable( srcFolders, excludeFiles, excludeFolders, excludeFunctions, recursive, numMetrics, listener );
        String ccmConfigFile = workspace.act( ccmConfigGenerator );
    	args.add( ccmConfigFile );
    	
    	args.add( ">" );
    	
    	CCMResultCallable ccmResultCallable = new CCMResultCallable();
    	String ccmResultFile = workspace.act( ccmResultCallable );
    	args.add( ccmResultFile );
    	
    	//According to the Ant builder source code, in order to launch a program 
        //from the command line in windows, we must wrap it into cmd.exe.  This 
        //way the return code can be used to determine whether or not the build failed.
        if( ! launcher.isUnix() ) // maybe user is using Wine? 
        {
            args.prepend("cmd.exe","/C");
            args.add( "&&", "exit", "%%ERRORLEVEL%%" );
        } 
        args.add("\"");

		// ------------------------- 
    	// Executing CCM    
    	// ------------------------- 
        
        listener.getLogger().println("Executing CCM command: "+args.toStringWithQuote());
        
        try 
        {
            Map<String,String> env = build.getEnvironment(listener);
            int r = launcher.launch().cmds(args).envs(env).stdout(listener).pwd(build.getModuleRoot()).join();
            
            return r==0;
        }
        catch (Exception e) 
        {
            e.printStackTrace( listener.error("CCM command execution failed") );
            build.setResult(Result.FAILURE);
            return false;
        }
		
	}
	
	
    
}
