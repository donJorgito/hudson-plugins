package hudson.plugins.build_publisher;

import hudson.Functions;
import hudson.Util;
import hudson.matrix.MatrixConfiguration;
import hudson.maven.MavenModule;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.methods.FileRequestEntity;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarOutputStream;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Sends build result via HTTP protocol.
 *
 */
public class HTTPBuildTransmitter implements BuildTransmitter {

    private PostMethod method;
    private boolean aborted = false;

    @Override
    public void sendBuild(AbstractBuild build, HudsonInstance hudsonInstance)
            throws IOException {

        aborted = false;

        AbstractProject project = build.getProject();
        
        String jobUrl = "job/";
        if (project instanceof MavenModule) {
            jobUrl += ((MavenModule) project).getParent().getName()
                    + "/"
                    + ((MavenModule) project).getModuleName()
                            .toFileSystemName();
        } else if (project instanceof MatrixConfiguration) {
            jobUrl += ((MatrixConfiguration)project).getParent().getName()
                    + "/"
                    + ((MatrixConfiguration)project).getCombination().toString();
        } else {
            jobUrl += project.getName();
        }

        method = new PostMethod(hudsonInstance.getUrl()
                + encodeURI(jobUrl) + "/postBuild/acceptBuild");

        File tempFile = null;
        try {

            tempFile = File.createTempFile("hudson_bp", ".tar");
            OutputStream out = new FileOutputStream(tempFile);
            writeToTar(out, build);

            method.setRequestEntity(new FileRequestEntity(tempFile,
                    "application/x-tar"));

            int responseCode = executeMethod(method, hudsonInstance);
            if (responseCode >= 400) {
                // transmission probably failed. Let's notify sender
                throw new HttpException(method.getURI()
                        + ": Server responded with status " + responseCode);
            }
        } catch (IOException e) {
            // May be caused by premature call of HttpMethod.abort()
            if (!aborted) {
                throw (e);
            }
        } catch (RuntimeException e1) {
            if (!aborted) {
                throw (e1);
            }
        } finally {
            if (!tempFile.delete()) {
                throw new IOException("Failed to delete temporary file "
                        + tempFile.getAbsolutePath()
                        + ". Please delete the file manually.");
            }
        }

    }

    @Override
    public void abortTransmission() {
        aborted = true;
        if (method != null) {
            method.abort();
        }
    }

    /* Follows redirects, authenticates if necessary. */
    static int executeMethod(HttpMethodBase method,
            HudsonInstance hudsonInstance) throws IOException {
        int statusCode;

        if (hudsonInstance.requiresAuthentication()) {
            // We need to get authenticated.
            // On some containers and depending on the security configuration,
            // simply sending HTTP BASIC auth would work, but in legacy authentication
            // with some containers in particular, the behavior tends to be
            // different.
            // So while lengthy, let's emulate the user behavior when
            // they clock the login link, which is most stable across different
            // environment
            GetMethod loginMethod = new GetMethod(hudsonInstance.getUrl()
                    + "loginEntry");
            statusCode = followRedirects(loginMethod, hudsonInstance);

            PostMethod credentialsMethod = new PostMethod(hudsonInstance
                    .getUrl()
                    + "j_security_check");
            credentialsMethod.addParameter("j_username", hudsonInstance
                    .getLogin());
            credentialsMethod.addParameter("j_password", hudsonInstance
                    .getPassword());
            credentialsMethod.addParameter("action", "login");
            statusCode = followRedirects(credentialsMethod, hudsonInstance);
        }

        statusCode = followRedirects(method, hudsonInstance);
        return statusCode;
    }

    private static int followRedirects(HttpMethodBase method,
            HudsonInstance hudsonInstance) throws IOException {
        int statusCode;
        HttpClient client = hudsonInstance.getHttpClient();
        try {
            statusCode = client.executeMethod(method);
        } finally {
            method.releaseConnection();
        }

        if ((statusCode >= 300) && (statusCode < 400)) {
            Header locationHeader = method.getResponseHeader("location");
            if (locationHeader != null) {
                String redirectLocation = locationHeader.getValue();
                method
                        .setURI(new org.apache.commons.httpclient.URI(/*method.getURI(),*/ redirectLocation,
                                true));
                statusCode = followRedirects(method, hudsonInstance);
            }
        }

        return statusCode;
    }

    /**
     * Writes to a tar stream and stores obtained files to the base dir.
     *
     * @return number of files/directories that are written.
     */
    // most of this is taken from somewhere of Hudson code. Perhaps it would be
    // good idea to put it in one place.
    private Integer writeToTar(OutputStream out, AbstractBuild build)
            throws IOException {
        File buildDir = build.getRootDir();
        File baseDir = buildDir.getParentFile();
        String buildXmlFile = buildDir.getName() + "/build.xml";
        FileSet fileSet = new FileSet();
        fileSet.setDir(baseDir);
        fileSet.setIncludes(buildDir.getName() + "/**");
        fileSet.setExcludes(buildXmlFile);

        byte[] buffer = new byte[8192];

        TarOutputStream tar = new TarOutputStream(new BufferedOutputStream(out));
        tar.setLongFileMode(TarOutputStream.LONGFILE_GNU);

        DirectoryScanner dirScanner = fileSet
                .getDirectoryScanner(new org.apache.tools.ant.Project());
        String[] files = dirScanner.getIncludedFiles();
        for (String fileName : files) {

            if (aborted) {
                break;
            }

            if (Functions.isWindows()) {
                fileName = fileName.replace('\\', '/');
            }

            File file = new File(baseDir, fileName);

            if (!file.isDirectory()) {
                writeStreamToTar(tar, new FileInputStream(file), fileName, file
                        .length(), buffer);
            }
        }

        File buildFile = new File(build.getRootDir(), "build.xml");
        String buildXml = Util.loadFile(buildFile);
        byte[] bytes = buildXml.getBytes();
        writeStreamToTar(tar, new ByteArrayInputStream(bytes), buildXmlFile,
                bytes.length, buffer);

        tar.close();

        return files.length;
    }

    private void writeStreamToTar(TarOutputStream tar, InputStream in,
            String fileName, long length, byte[] buf) throws IOException {
        TarEntry te = new TarEntry(fileName);
        te.setSize(length);

        tar.putNextEntry(te);

        int len;
        while ((len = in.read(buf)) >= 0) {

            if (aborted) {
                break;
            }

            tar.write(buf, 0, len);
        }
        tar.closeEntry();

        in.close();
    }

    public static String encodeURI(String uri) {
        try {
            return new URI(null,uri,null).toASCIIString();
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return uri;
        }
    }


}
