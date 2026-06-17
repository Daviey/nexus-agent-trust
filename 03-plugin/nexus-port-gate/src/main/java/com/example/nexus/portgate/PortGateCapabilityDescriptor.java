package com.example.nexus.portgate;

import java.util.List;
import java.util.Map;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.capability.CapabilityDescriptorSupport;
import org.sonatype.nexus.capability.CapabilityType;
import org.sonatype.nexus.formfields.FormField;
import org.sonatype.nexus.formfields.NumberTextFormField;
import org.sonatype.nexus.formfields.TextAreaFormField;

@Named(PortGateCapability.TYPE_ID)
@Singleton
public class PortGateCapabilityDescriptor extends CapabilityDescriptorSupport {

    private static final int VERSION = 1;

    private final NumberTextFormField scopedPortField;
    private final TextAreaFormField allowedPathsField;

    public PortGateCapabilityDescriptor() {
        scopedPortField = new NumberTextFormField(
            "scopedPort",
            "Scoped port",
            "The Nexus port that receives agent traffic. Must match the second Jetty connector.",
            FormField.MANDATORY
        );
        scopedPortField.withInitialValue(8443);
        scopedPortField.withMinimumValue(1);
        scopedPortField.withMaximumValue(65535);

        allowedPathsField = new TextAreaFormField(
            "allowedPaths",
            "Allowed repository paths",
            "One repository path prefix per line. # for comments. Blank lines ignored. "
            + "A prefix matches any request URI starting with that string.",
            FormField.MANDATORY
        );
        allowedPathsField.setHelpText(
            "# comments and blank lines ignored\n"
            + "/repository/trusted/\n"
            + "/repository/npm-internal/"
        );
    }

    @Override
    public CapabilityType type() {
        return new CapabilityType(PortGateCapability.TYPE_ID);
    }

    @Override
    public String name() {
        return "Agent Trust: Port Gate";
    }

    @Override
    public int version() {
        return VERSION;
    }

    @Override
    public String about() {
        return "Gates repository access on a scoped port. Only allowlisted "
            + "repository paths are served; all others return 403. "
            + "When disabled, the scoped port denies all repository access.";
    }

    @Override
    public List<FormField> formFields() {
        return List.of(scopedPortField, allowedPathsField);
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected Object createConfig(final Map properties) {
        return properties;
    }
}
