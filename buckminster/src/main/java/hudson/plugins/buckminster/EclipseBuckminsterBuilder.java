package hudson.plugins.buckminster;

import hudson.CopyOnWrite;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.Hudson;
import hudson.plugins.buckminster.command.CommandLineBuilder;
import hudson.plugins.buckminster.targetPlatform.NoTargetPlatformReference;
import hudson.plugins.buckminster.targetPlatform.TargetPlatformPublisher;
import hudson.plugins.buckminster.targetPlatform.TargetPlatformReference;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Publisher;
import hudson.tools.ToolProperty;
import hudson.util.DescribableList;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Build Step that invokes Eclipse Buckminster.
 * 
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked and a new
 * {@link EclipseBuckminsterBuilder} is created. The created instance is
 * persisted to the project configuration XML by using XStream, so this allows
 * you to use instance fields (like {@link #name}) to remember the
 * configuration.
 * 
 * <p>
 * When a build is performed, the
 * {@link #perform(AbstractBuild, Launcher, BuildListener)} method will be invoked.
 * 
 * @author Johannes Utzig
 */
public class EclipseBuckminsterBuilder extends Builder {

	private final String installationName, commands, logLevel, params, targetPlatformName, userTemp, userOutput, userCommand, userWorkspace, globalPropertiesFile;

	@DataBoundConstructor
	public EclipseBuckminsterBuilder(String installationName, String commands, String logLevel, String params, String targetPlatformName, String userTemp, String userOutput, String userCommand, String userWorkspace, String globalPropertiesFile) {
		this.installationName = installationName;
		this.commands = commands;
		this.logLevel = logLevel;
		this.params = params;
		this.targetPlatformName = targetPlatformName;
		this.userTemp = userTemp;
		this.userOutput = userOutput;
		this.userCommand = userCommand;
		this.userWorkspace = userWorkspace;
		this.globalPropertiesFile = globalPropertiesFile;
	}

	/**
	 * We'll use this from the <tt>config.jelly</tt>.
	 */
	public String getEclipseHome() {
		return getInstallation().getHome();
	}

	public String getCommands() {
		return commands;
	}
	
	public String getParams() {
		return params;
	}
	
	public String getUserWorkspace() {
		return userWorkspace;
	}

	public String getLogLevel(){
		if(logLevel!=null && logLevel.length()!=0)
			return logLevel;
		return "info";
	}
	
	public boolean isSelected(String item){
		return getLogLevel().equals(item);
	}
	
	public BuckminsterInstallation getInstallation() {
		for (BuckminsterInstallation si : DESCRIPTOR.getBuckminsterInstallations()) {
			if (installationName != null
					&& si.getName().equals(installationName)) {
				return si;
			}
		}

		return null;
	}
	
	public String getUserTemp() {
		return userTemp;
	}

	public String getUserOutput() {
		return userOutput;
	}

	public String getUserCommand() {
		return userCommand;
	}

	public TargetPlatformReference getTargetPlatform() {
		for (TargetPlatformReference reference : DESCRIPTOR.getTargetPlatforms()) {
			if (targetPlatformName != null
					&& reference.getName().equals(targetPlatformName)) {
				return reference;
			}
		}

		return null;
	}

	@Override
	public boolean perform(AbstractBuild<?,?> build, Launcher launcher,
			BuildListener listener) {
		try {
			BuckminsterInstallation installation = getInstallation();
			if(installation==null)
			{

				installation = pickDefault(listener);
				if(installation==null)
				{
					listener.error("No Buckminster Tool Installation has been configured.");
					return false;
				}
			}
	        installation = installation.forNode(Computer.currentComputer().getNode(), listener);
	        installation = installation.forEnvironment(build.getEnvironment(listener));
			String modifiedCommands = getCommands();
			TargetPlatformReference targetPlatform = getTargetPlatform();
			if(targetPlatform!=null && targetPlatform.getPath()!=null){
				modifiedCommands = "setpref targetPlatformPath=\""+targetPlatform.getPath()+"\"" +"\n" + modifiedCommands;
			}
			CommandLineBuilder cmdBuilder = CommandLineBuilder.forInstallation(installation).commands(modifiedCommands).loglevel(getLogLevel()).additionalParams(getParams()).userWorkspace(getUserWorkspace()).userTemp(getUserTemp()).userOutput(getUserOutput()).userCommandFile(getUserCommand()).propertyFile(getGlobalPropertiesFile());
			List<String> buildCommands = cmdBuilder.buildCommands(build,listener, launcher);
			Proc proc = launcher.launch().envs(build.getEnvironment(listener)).pwd(build.getWorkspace()).cmds(buildCommands).stdout(listener).start();
			return proc.join()==0;

		} 
		catch (Exception e) {
			listener.error(e.getLocalizedMessage());
			return false;
		}

	}

	public String getGlobalPropertiesFile() {
		return globalPropertiesFile;
	}

	private BuckminsterInstallation pickDefault(BuildListener listener) {
		BuckminsterInstallation[] available = DESCRIPTOR.getBuckminsterInstallations();
		if(available==null || available.length==0)
			return null;
		String message = "The selected Buckminster Tool Installation \"{0}\" has not been found, using \"{1}\" instead!";
		message = MessageFormat.format(message, installationName,available[0].getName());
		listener.error(message);
		return available[0];
	}

	/**
	 * Descriptor should be singleton.
	 */
        @Extension
	public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

	/**
	 * Descriptor for {@link EclipseBuckminsterBuilder}. Used as a singleton. The class
	 * is marked as public so that it can be accessed from views.
	 */
    @SuppressWarnings("deprecation")
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

		@CopyOnWrite
		private volatile BuckminsterInstallation[] buckminsterInstallations = new BuckminsterInstallation[0];
		
		@CopyOnWrite
		private volatile EclipseInstallation[] installations = new EclipseInstallation[0];

		DescriptorImpl() {
			super(EclipseBuckminsterBuilder.class);
			load();
		}
		
		/**
		 * Asks hudson for all available {@link AbstractProject}s and iterates over the available {@link Publisher}s.
		 * <p>
		 * If a {@link TargetPlatformPublisher} is found and it offers a {@link TargetPlatformReference}, that reference is added to the result list.
		 * 
		 * @return a list of all {@link TargetPlatformReference}s published by a {@link TargetPlatformPublisher}
		 */
		@SuppressWarnings("rawtypes")
		public List<TargetPlatformReference> getTargetPlatforms() {	
			
			List<AbstractProject> projects = Hudson.getInstance().getAllItems(AbstractProject.class);
			List<TargetPlatformReference> references = new ArrayList<TargetPlatformReference>();
			references.add(new NoTargetPlatformReference());
			for (AbstractProject<?,?> project : projects) {
				DescribableList<Publisher,Descriptor<Publisher>> publishersList = project.getPublishersList();
				for (Describable<?> describable : publishersList) {
					if (describable instanceof TargetPlatformPublisher) {
						TargetPlatformPublisher publisher = (TargetPlatformPublisher) describable;
						TargetPlatformReference reference = publisher.getTargetPlatformReference(project);
						if(reference!=null)
						{
							references.add(reference);	
						}
					}
				}

			}
			return references;
			
		}

		
		public EclipseInstallation[] getInstallations() {
			return installations;
		}
		
		public BuckminsterInstallation[] getBuckminsterInstallations() {
			if(installations!=null && installations.length>0)
			{
				//convert the old installations and reset the installations field
				List<BuckminsterInstallation> converted = convertLegacyInstallations(installations);
				
				if(buckminsterInstallations!=null && buckminsterInstallations.length>0)
				{
					Collections.addAll(converted, buckminsterInstallations);
				}
				buckminsterInstallations = converted.toArray(new BuckminsterInstallation[converted.size()]);
				installations = new EclipseInstallation[0];
			}
			return buckminsterInstallations;
		}

		/**
		 * performs on-thy-fly validation of the eclipse home path field.
		 * @param req the request
		 * @param rsp the response
		 * @return one of {@link FormValidation#error(String)} or {@link FormValidation#ok()}
		 * @throws IOException
		 * @throws ServletException
		 */
		public FormValidation doCheckEclipseHome(StaplerRequest req, StaplerResponse rsp)
				throws IOException, ServletException {
			final String value = req.getParameter("value");
			File eclipseHome = new File(value);
			if (!eclipseHome.exists()) {
				String error = "The path {0} does not exist";
				error = MessageFormat.format(error, value);
				return FormValidation.error(error);
			} else if (!eclipseHome.isDirectory()) {
				String error = "{0} is not a directory";
				error = MessageFormat.format(error, value);
				return FormValidation.error(error);
			} else if (!new File(eclipseHome.getAbsolutePath()
					+ "/plugins").exists()) {
				String error = "{0} does not contain a plugins directory";
				error = MessageFormat.format(error, value);
				return FormValidation.error(error);
			}
			return FormValidation.ok();
		}

		public FormValidation doCheckEclipseVersion(StaplerRequest req,
				StaplerResponse rsp) throws IOException, ServletException {
			final String version = req.getParameter("value");
			if (!(version.startsWith("3.4") || version.startsWith("3.5") || version.startsWith("3.6") || version.startsWith("3.7"))) {
				return FormValidation.error("Eclipse version is not valid. Currently only 3.4, 3.5, 3.6 and 3.7 are supported.");
				
			}

			return FormValidation.ok();
		}
		
        /**
         * Performs on-the-fly validation on the file mask wildcard.
         */
        public FormValidation doCheckUserCommand(@AncestorInPath AbstractProject<?,?> project, @QueryParameter String value) throws IOException {
            FilePath path = project.getSomeWorkspace();
            if(path==null)
            	//we tried, but we couldn't get a workspace
            	return FormValidation.ok();
        	return path.validateRelativePath(value, true, true);
        }
        
        /**
         * Performs on-the-fly validation for the global properties file
         */
        public FormValidation doCheckGlobalPropertiesFile(@AncestorInPath AbstractProject<?,?> project, @QueryParameter String value) throws IOException {
            FilePath path = project.getSomeWorkspace();
            if(path==null)
            	//we tried, but we couldn't get a workspace
            	return FormValidation.ok();
        	return path.validateRelativePath(value, true, true);
        }
		

		/**
		 * This human readable name is used in the configuration screen.
		 */
		public String getDisplayName() {
			return "Run Buckminster";
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject o)
				throws FormException {
			save();
			return super.configure(req, o);
		}


		private List<BuckminsterInstallation> convertLegacyInstallations(
				EclipseInstallation[] installations) {
			List<BuckminsterInstallation> convertedInstallations = new ArrayList<BuckminsterInstallation>(installations.length);
			for (EclipseInstallation eclipseInstallation : installations) {
				BuckminsterInstallation inst = new BuckminsterInstallation(eclipseInstallation.getName(), eclipseInstallation.getHome(), eclipseInstallation.getVersion(), eclipseInstallation.getParams(), Collections.<ToolProperty<?>>emptyList());
				convertedInstallations.add(inst);
			}
			return convertedInstallations;
		}

		@Override
		public Builder newInstance(StaplerRequest req, JSONObject formData)
				throws hudson.model.Descriptor.FormException {
			
			return req.bindJSON(EclipseBuckminsterBuilder.class,
			formData);
		}

		@SuppressWarnings("rawtypes")
		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return FreeStyleProject.class.isAssignableFrom(jobType) || MatrixProject.class.isAssignableFrom(jobType);
		}

		public void setBuckminsterInstallations(
				BuckminsterInstallation... installations) {
            this.buckminsterInstallations = installations;
            save();
			
		}
	}
    
}
