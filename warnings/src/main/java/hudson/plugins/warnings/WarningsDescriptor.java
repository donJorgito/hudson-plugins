package hudson.plugins.warnings;

import groovy.lang.GroovyShell;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.plugins.analysis.core.PluginDescriptor;
import hudson.plugins.warnings.parser.ParserRegistry;
import hudson.util.CopyOnWriteList;
import hudson.util.FormValidation;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.codehaus.groovy.control.CompilationFailedException;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Descriptor for the class {@link WarningsPublisher}. Used as a singleton. The
 * class is marked as public so that it can be accessed from views.
 *
 * @author Ulli Hafner
 */
@Extension(ordinal = 100) // NOCHECKSTYLE
public final class WarningsDescriptor extends PluginDescriptor {
    /** Plug-in name. */
    private static final String PLUGIN_NAME = "warnings";
    /** Icon to use for the result and project action. */
    private static final String ACTION_ICON = "/plugin/warnings/icons/warnings-24x24.png";

    private final CopyOnWriteList<GroovyParser> groovyParsers = new CopyOnWriteList<GroovyParser>();

    /**
     * Instantiates a new {@link WarningsDescriptor}.
     */
    public WarningsDescriptor() {
        super(WarningsPublisher.class);
    }

    /** {@inheritDoc} */
    @Override
    public String getDisplayName() {
        return Messages.Warnings_Publisher_Name();
    }

    /** {@inheritDoc} */
    @Override
    public String getPluginName() {
        return PLUGIN_NAME;
    }

    /** {@inheritDoc} */
    @Override
    public String getIconUrl() {
        return ACTION_ICON;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("rawtypes")
    @Override
    public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public WarningsPublisher newInstance(final StaplerRequest request, final JSONObject formData) throws FormException {
        Set<String> parsers = extractParsers(formData);

        WarningsPublisher publisher = request.bindJSON(WarningsPublisher.class, formData);
        publisher.setParserNames(parsers);

        return publisher;
    }

    /**
     * Extract the list of parsers to use from the JSON form data.
     *
     * @param formData
     *            the JSON form data
     * @return the list of parsers to use
     */
    private Set<String> extractParsers(final JSONObject formData) {
        Set<String> parsers = new HashSet<String>();
        Object values = formData.get("parsers");
        if (values instanceof JSONArray) {
            JSONArray array = (JSONArray)values;
            for (int i = 0; i < array.size(); i++) {
                JSONObject element = array.getJSONObject(i);
                parsers.add(element.getString("parserName"));
            }
            formData.remove("parsers");
        }
        else if (values instanceof JSONObject) {
            JSONObject object = (JSONObject)values;
            parsers.add(object.getString("parserName"));
            formData.remove("parsers");
        }

        return parsers;
    }

    /**
     * Returns the configured Groovy parsers.
     *
     * @return the Groovy parsers
     */
    public CopyOnWriteList<GroovyParser> getParsers() {
        return groovyParsers;
    }

    @Override
    public boolean configure(final StaplerRequest req, final JSONObject formData) {
        groovyParsers.replaceBy(req.bindJSONToList(GroovyParser.class, formData.get("parsers")));

        return true;
    }

    /**
     * Performs on-the-fly validation on the name of the parser that needs to be unique.
     *
     * @param name
     *            the name of the parser
     * @return the validation result
     */
    public FormValidation doCheckName(@QueryParameter(required = true) final String name) {
        if (StringUtils.isBlank(name)) {
            return FormValidation.error(Messages.Warnings_GroovyParser_Error_Name_isEmpty());
        }
        for (String parserName : ParserRegistry.getAvailableParsers()) {
            if (parserName.equals(name)) {
                return FormValidation.error(Messages.Warnings_GroovyParser_Error_Name_isNotUnique());
            }
        }
        return FormValidation.ok();
    }

    /**
     * Performs on-the-fly validation on the regular expression.
     *
     * @param regexp
     *            the regular expression
     * @return the validation result
     */
    public FormValidation doCheckRegexp(@QueryParameter(required = true)  final String regexp) {
        try {
            if (StringUtils.isBlank(regexp)) {
                return FormValidation.error(Messages.Warnings_GroovyParser_Error_Regexp_isEmpty());
            }
            Pattern.compile(regexp);

            return FormValidation.ok();
        }
        catch (PatternSyntaxException exception) {
            return FormValidation.error(Messages.Warnings_GroovyParser_Error_Regexp_invalid(exception.getLocalizedMessage()));
        }
    }

    /**
     * Performs on-the-fly validation on the Groovy script.
     *
     * @param script
     *            the script
     * @return the validation result
     */
    public FormValidation doCheckScript(@QueryParameter(required = true) final String script) {
        try {
            if (StringUtils.isBlank(script)) {
                return FormValidation.error(Messages.Warnings_GroovyParser_Error_Script_isEmpty());
            }

            GroovyShell groovyShell = new GroovyShell();
            groovyShell.parse(script);

            return FormValidation.ok();
        }
        catch (CompilationFailedException exception) {
            return FormValidation.error(Messages.Warnings_GroovyParser_Error_Script_invalid(exception.getLocalizedMessage()));
        }
    }
}