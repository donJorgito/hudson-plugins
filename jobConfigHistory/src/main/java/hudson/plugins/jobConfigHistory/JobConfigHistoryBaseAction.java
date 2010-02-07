package hudson.plugins.jobConfigHistory;

import hudson.XmlFile;
import hudson.model.Action;
import hudson.model.Hudson;
import hudson.security.AccessControlled;
import hudson.security.Permission;

import java.io.File;
import java.io.IOException;

import org.kohsuke.stapler.Stapler;

/**
 * Implements some basic methods for returning baseUrl and image paths. This is the base class for javascript actions.
 *
 * @author mfriedenhagen
 */
public abstract class JobConfigHistoryBaseAction implements Action {

    /**
     * The hudson instance.
     */
    private final Hudson hudson;

    /**
     * Set the {@link Hudson} instance.
     */
    public JobConfigHistoryBaseAction() {
        hudson = Hudson.getInstance();
    }

    /**
     * {@inheritDoc}
     *
     * Make method final, as we always want the same display name.
     */
    // @Override
    public final String getDisplayName() {
        return JobConfigHistoryConsts.DISPLAYNAME;
    }

    /**
     * {@inheritDoc}
     *
     * Make method final, as we always want the same icon file.
     */
    // @Override
    public final String getIconFileName() {
        return JobConfigHistoryConsts.ICONFILENAME;
    }

    /**
     * {@inheritDoc}
     *
     * Do not make this method final as {@link JobConfigHistoryRootAction} overrides this method.
     */
    // @Override
    public String getUrlName() {
        return JobConfigHistoryConsts.URLNAME;
    }

    /**
     * Do we want 'raw' output?
     *
     * @return true if request parameter type is 'raw'
     */
    public final boolean wantRawOutput() {
        return getType().equalsIgnoreCase("raw");

    }

    /**
     * Do we want 'xml' output?
     *
     * @return true if request parameter type is 'xml'
     */
    public final boolean wantXmlOutput() {
        return getType().equalsIgnoreCase("xml");

    }

    /**
     * Returns {@link JobConfigHistoryBaseAction#getConfigXml(String)} as String.
     *
     * @return content of the {@code config.xml} found in directory given by the request parameter {@code file}.
     * @throws IOException
     *             if the config file could not be read.
     */
    public final String getFile() throws IOException {
        checkReadPermission();
        return getConfigXml(getRequestParameter("file")).asString();
    }

    /**
     * See whether the current user may read configurations.
     */
    protected void checkReadPermission() {
        final AccessControlled accessControled = hudson;
        final Permission permission = Permission.READ;
        accessControled.checkPermission(permission);
    }

    /**
     * Returns the type parameter of the current request.
     *
     * @return type.
     */
    public final String getType() {
        return getRequestParameter("type");
    }

    /**
     * Returns the {@code config.xml} located in {@code diffDir}.
     *
     * @param diffDir
     *            timestamped history directory.
     * @return xmlfile.
     */
    protected XmlFile getConfigXml(final String diffDir) {
        final File rootDir = hudson.getRootDir();
        final String absoluteRootDirPath = rootDir.getAbsolutePath();
        if (!diffDir.startsWith(absoluteRootDirPath) || !diffDir.contains("config-history")) {
            throw new IllegalArgumentException(diffDir + " does not start with " + absoluteRootDirPath
                    + " or does not contain 'config-history'");
        }
        return new XmlFile(new File(diffDir, "config.xml"));
    }

    /**
     * Returns the parameter named {@code parameterName} from current request.
     *
     * @param parameterName
     *            name of the parameter.
     * @return value of the request parameter or null if it does not exist.
     */
    protected String getRequestParameter(final String parameterName) {
        return Stapler.getCurrentRequest().getParameter(parameterName);
    }

}
