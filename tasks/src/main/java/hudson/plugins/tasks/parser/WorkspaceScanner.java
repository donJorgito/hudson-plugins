package hudson.plugins.tasks.parser;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.plugins.analysis.util.ContextHashCode;
import hudson.plugins.analysis.util.EncodingValidator;
import hudson.plugins.analysis.util.ModuleDetector;
import hudson.plugins.analysis.util.PackageDetectors;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;

import org.apache.commons.lang.StringUtils;
import org.apache.tools.ant.types.FileSet;

/**
 * Scans the workspace and records the found tasks. Each file is then
 * classified, i.e., a module and package is guessed and assigned.
 *
 * @author Ulli Hafner
 */
public class WorkspaceScanner implements FileCallable<TasksParserResult> {
    /** Generated ID. */
    private static final long serialVersionUID = -4355362392102020724L;
    /** Ant file-set pattern to define the files to scan. */
    private final String filePattern;
    /** Ant file-set pattern to define the files to exclude from scan. */
    private final String excludeFilePattern;
    /** The maven module. If <code>null</code>, then the scanner tries to guess it (freestyle project). */
    private String moduleName;
    /** Tag identifiers indicating high priority. */
    private final String high;
    /** Tag identifiers indicating normal priority. */
    private final String normal;
    /** Tag identifiers indicating low priority. */
    private final String low;
    /** Tag identifiers indicating case sensitive parsing. */
    private final boolean ignoreCase;
    /** Prefix of path. */
    private String prefix;
    /** The default encoding to be used when reading and parsing files. */
    private final String defaultEncoding;

    /**
     * Creates a new instance of <code>WorkspaceScanner</code>.
     *
     * @param filePattern
     *            ant file-set pattern to scan for files
     * @param excludeFilePattern
     *            ant file-set pattern to exclude from scan
     * @param defaultEncoding
     *            the default encoding to be used when reading and parsing files
     * @param high
     *            tag identifiers indicating high priority
     * @param normal
     *            tag identifiers indicating normal priority
     * @param low
     *            tag identifiers indicating low priority
     * @param ignoreCase
     *            if case should be ignored during matching
     */
    public WorkspaceScanner(final String filePattern, final String excludeFilePattern, final String defaultEncoding,
            final String high, final String normal, final String low, final boolean ignoreCase) {
        this.filePattern = filePattern;
        this.excludeFilePattern = excludeFilePattern;
        this.defaultEncoding = defaultEncoding;
        this.high = high;
        this.normal = normal;
        this.low = low;
        this.ignoreCase = ignoreCase;
    }

    /**
     * Creates a new instance of <code>WorkspaceScanner</code>.
     *
     * @param filePattern
     *            ant file-set pattern to scan for files
     * @param excludeFilePattern
     *            ant file-set pattern to exclude from scan
     * @param defaultEncoding
     *            the default encoding to be used when reading and parsing files
     * @param high
     *            tag identifiers indicating high priority
     * @param normal
     *            tag identifiers indicating normal priority
     * @param low
     *            tag identifiers indicating low priority
     * @param caseSensitive
     *            determines whether the scanner should work case sensitive
     * @param moduleName
     *            the maven module name
     */
    // CHECKSTYLE:OFF
    public WorkspaceScanner(final String filePattern, final String excludeFilePattern, final String defaultEncoding,
            final String high, final String normal, final String low, final boolean caseSensitive,
            final String moduleName) {
        this(filePattern, excludeFilePattern, defaultEncoding, high, normal, low, caseSensitive);
        this.moduleName = moduleName;
    }
    // CHECKSTYLE:ON

    /**
     * Sets the prefix to the specified value.
     *
     * @param prefix the value to set
     */
    public void setPrefix(final String prefix) {
        this.prefix = prefix + "/";
    }

    /**
     * Returns the prefix.
     *
     * @return the prefix
     */
    public String getPrefix() {
        return StringUtils.defaultIfEmpty(prefix, StringUtils.EMPTY);
    }

    /** {@inheritDoc} */
    public TasksParserResult invoke(final File workspace, final VirtualChannel channel) throws IOException {
        String[] files = findFiles(workspace);

        TaskScanner taskScanner = new TaskScanner(high, normal, low, ignoreCase);
        TasksParserResult javaProject = new TasksParserResult(files.length);
        ModuleDetector moduleDetector = new ModuleDetector(workspace);
        for (String fileName : files) {
            File originalFile = new File(workspace, fileName);
            Collection<Task> tasks = taskScanner.scan(new InputStreamReader(new FilePath(originalFile).read(),
                    EncodingValidator.defaultCharset(defaultEncoding)));
            if (!tasks.isEmpty()) {
                String unixName = fileName.replace('\\', '/');
                String packageName = PackageDetectors.detectPackage(unixName, new FilePath(originalFile).read());
                String guessedModule = moduleDetector.guessModuleName(originalFile.getAbsolutePath());
                String actualModule = StringUtils.defaultIfEmpty(moduleName, guessedModule);

                for (Task task : tasks) {
                    task.setFileName(originalFile.getAbsolutePath());
                    task.setPackageName(packageName);
                    task.setModuleName(actualModule);
                    task.setPathName(workspace.getPath());

                    ContextHashCode hashCode = new ContextHashCode();
                    task.setContextHashCode(hashCode.create(originalFile.getAbsolutePath(), task.getPrimaryLineNumber(), defaultEncoding));
                }

                javaProject.addAnnotations(tasks);
            }
        }

        return javaProject;
    }

    /**
     * Returns an array with the filenames of the files that have been found in
     * the workspace.
     *
     * @param workspaceRoot
     *      root directory of the workspace
     * @return the filenames of the FindBugs files
     */
    private String[] findFiles(final File workspaceRoot) {
        FileSet fileSet = new FileSet();
        org.apache.tools.ant.Project project = new org.apache.tools.ant.Project();
        fileSet.setProject(project);
        fileSet.setDir(workspaceRoot);
        fileSet.setIncludes(filePattern);

        if (StringUtils.isNotBlank(excludeFilePattern)) {
            fileSet.setExcludes(excludeFilePattern);
        }

        return fileSet.getDirectoryScanner(project).getIncludedFiles();
    }
}