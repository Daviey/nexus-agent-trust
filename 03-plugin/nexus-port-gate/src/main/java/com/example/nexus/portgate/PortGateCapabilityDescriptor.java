package com.example.nexus.portgate;

import java.util.List;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.capability.support.CapabilityDescriptorSupport;
import org.sonatype.nexus.capability.support.CapabilityReference;
import org.sonatype.nexus.capability.support.CapabilityRegistryEvent;
import org.sonatype.nexus.formfields.FormField;
import org.sonatype.nexus.formfields.NumberTextFormField;
import org.sonatype.nexus.formfields.TextAreaFormField;

import static com.google.common.base.Preconditions.checkState;

/**
 * Descriptor for the Port-Gate capability. Defines the form fields shown
 * in the Nexus UI when creating or editing the capability.
 *
 * Renders under:
 *   Administration > Capabilities > Create > Agent Trust: Port Gate
 */
@Named(PortGateCapability.TYPE_ID)
@Singleton
public class PortGateCapabilityDescriptor extends CapabilityDescriptorSupport {

    private static final String VERSION = "1.0.0";

    private final NumberTextFormField scopedPortField;
    private TextAreaFormField allowedPathsField;

    public PortGateCapabilityDescriptor() {
        scopedPortField = new NumberTextFormField(
            "scopedPort",
            "Scoped port",
            "The Nexus port that receives agent traffic. Must match the second Jetty connector.",
            FormField.MANDATORY
        );
        scopedPortField.setInitialValue("8443");
        scopedPortField.setMinimumValue(1);
        scopedPortField.setMaximumValue(65535);

        allowedPathsField = new TextAreaFormField(
            "allowedPaths",
            "Allowed repository paths",
            "One repository path prefix per line. Lines starting with # are comments. " +
            "A prefix matches any request URI that starts with that string, so " +
            "/repository/npm-internal/ covers all paths under that repository.",
            FormField.MANDATORY
        );
        allowedPathsField.setInitialValue(
            "# Hosted repositories\n" +
            "/repository/maven-releases/\n" +
            "\n" +
            "# Proxy repositories\n" +
            "/repository/npm-internal/\n" +
            "/repository/pypi-internal/\n"
        );
        allowedPathsField.setHelpText(
            "# comments and blank lines are ignored\n" +
            "Example:\n" +
            "  /repository/trusted/\n" +
            "  /repository/npm-agent/"
        );
    }

    @Override
    public String id() {
        return PortGateCapability.TYPE_ID;
    }

    @Override
    public String name() {
        return "Agent Trust: Port Gate";
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public String description() {
        return "Gates repository access on a scoped port. Only allowlisted " +
               "repository paths are served; all others return 403. " +
               "When disabled, the scoped port denies all repository access.";
    }

    @Override
    public List<FormField> formFields() {
        return List.of(scopedPortField, allowedPathsField);
    }

    @Override
    protected String renderAbout() {
        return "Port-gate filter for agent-trust scoping. Gates repository access " +
               "based on the local TCP port the request arrived on.";
    }

    @Override
    protected String renderDescription(final CapabilityReference reference) {
        PortGateCapability.Config cfg = new PortGateCapability.Config(
            reference.capabilityContext().properties()
        );
        return "Port-Gate: scoped port " + cfg.scopedPort()
            + ", " + cfg.allowedPaths().size() + " allowed path(s)";
    }
}
