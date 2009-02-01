package hudson.plugins.jabber.im.transport.bot;

import org.jivesoftware.smack.GroupChat;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;

import hudson.model.*;
import hudson.tasks.test.AbstractTestResultAction;;

/**
 * Print out the latest test results for a build
 * @author R. Tyler Ballance <tyler@slide.com>
 */
public class TestResultCommand implements BotCommand {
	
	private static final String HELP = " <job> - specify which job's test results you want to see";

	public void executeCommand(GroupChat groupChat, Message message, String sender, String[] args) throws XMPPException {
		//groupChat.sendMessage(new StringBuffer(sender).append(": thanks a lot! nom nom nom ").toString());
		if (args.length >= 2) {
			String jobName = args[1];
			jobName = jobName.replaceAll("\"", "");

			AbstractProject<?, ?> project = Hudson.getInstance().getItemByFullName(jobName, AbstractProject.class);
			if (project == null) {
				// Invalid job name
				groupChat.sendMessage(new StringBuffer(sender).append(": that doesn't look like a valid job").toString());
				return;
			}
			AbstractBuild build = project.getLastBuild();
			if (build == null) {
				// No builds? lolwut?
				groupChat.sendMessage(new StringBuffer(sender).append(": it appears this job has never been built").toString());
				return;
			}	
			AbstractTestResultAction tests = build.getTestResultAction();
			if (tests == null) {
				// no test results associated with this job
				groupChat.sendMessage(new StringBuffer(sender).append(": sorry, looks like the latest build doesn't contain test results").toString());
				return;
			}
			groupChat.sendMessage(new StringBuffer(sender).append(": Tabulating the results...").toString());
			groupChat.sendMessage(String.format("Out of %s tests, %s of them failed", tests.getTotalCount(), tests.getFailCount()));
		} 
		else {
			// No job name specified
			groupChat.sendMessage(new StringBuffer(sender).append(": you need to specify a job name").toString());
			return; 
		}
	}

	public String getHelp() {
		return HELP;
	}

}
