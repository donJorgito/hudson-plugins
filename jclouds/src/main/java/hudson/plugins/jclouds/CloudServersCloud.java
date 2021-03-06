package hudson.plugins.jclouds;

import hudson.Extension;

import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;

public class CloudServersCloud extends JCloudsCloud {
	
	
	@DataBoundConstructor
    public CloudServersCloud(String identity, String credential,
			String instanceCapStr, List<JCloudTemplate> templates) {
		super("cloudservers", identity, credential, instanceCapStr, templates);
    }

    @Extension
    public static class DescriptorImpl extends JCloudsCloud.DescriptorImpl {

    	@Override
    	public String getDisplayName() {
    		return "Rackspace CloudServers";
    	}
    }

}
