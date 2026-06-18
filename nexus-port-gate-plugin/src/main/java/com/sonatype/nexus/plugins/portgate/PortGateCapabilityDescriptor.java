package com.sonatype.nexus.plugins.portgate;

import java.util.List;
import java.util.Map;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonatype.nexus.capability.CapabilityDescriptorSupport;
import org.sonatype.nexus.capability.CapabilityType;
import org.sonatype.nexus.common.upgrade.AvailabilityVersion;
import org.sonatype.nexus.formfields.CheckboxFormField;
import org.sonatype.nexus.formfields.FormField;
import org.sonatype.nexus.formfields.ItemselectFormField;
import org.sonatype.nexus.formfields.NumberTextFormField;

/**
 * Defines the capability form shown in the Nexus UI.
 *
 * Four fields:
 * <ol>
 *   <li>Scoped port (number) - TCP port for agent traffic</li>
 *   <li>Enabled repositories (itemselect) - two-column repo picker</li>
 *   <li>Audit mode (checkbox) - log without blocking</li>
 *   <li>Read-only mode (checkbox) - block uploads and deletes</li>
 * </ol>
 *
 * The {@code storeApi} uses {@code coreui_Repository.read} (ExtDirect
 * {@code len:0}) rather than {@code readReferences} ({@code len:1})
 * because the itemselect widget sends {@code data:null}.
 */
@AvailabilityVersion(from = "1.0")
@Named("port-gate-descriptor")
@Singleton
public class PortGateCapabilityDescriptor extends CapabilityDescriptorSupport
{
    private final NumberTextFormField portField;
    private final ItemselectFormField reposField;
    private final CheckboxFormField auditField;
    private final CheckboxFormField readOnlyField;

    public PortGateCapabilityDescriptor() {
        portField = new NumberTextFormField(
            "scopedPort", "Scoped port",
            "TCP port for agent traffic. Each capability instance manages one port. "
            + "Create separate instances for different agent contexts (e.g. CI vs dev). "
            + "Must match the -Dportgate.port JVM argument.",
            FormField.MANDATORY);
        portField.withInitialValue(8443);

        reposField = new ItemselectFormField(
            "enabledRepos", "Enabled repositories",
            "Repositories accessible to agents on this port. "
            + "Repositories left in Available are blocked (HTTP 403 in enforce mode). "
            + "If zero repos are enabled, ALL requests on this port are denied.",
            FormField.MANDATORY);
        reposField.setStoreApi("coreui_Repository.read");
        reposField.setIdMapping("name");
        reposField.setNameMapping("name");
        reposField.getAttributes().put("fromTitle", "Available (blocked)");
        reposField.getAttributes().put("toTitle", "Enabled (agent access)");
        reposField.getAttributes().put("buttons",
            java.util.List.of("add", "remove", "addAll", "removeAll"));

        auditField = new CheckboxFormField(
            "auditMode", "Audit mode (log only)",
            "When checked, the port-gate logs what it WOULD deny but allows all requests through. "
            + "Use this to test your allowlist before switching to enforcement. "
            + "Monitor logs with: grep AUDIT-DENY nexus.log",
            FormField.OPTIONAL);

        readOnlyField = new CheckboxFormField(
            "readOnly", "Read-only mode (block writes)",
            "When checked, agents can download from enabled repositories but cannot upload, "
            + "publish, or delete artifacts. POST, PUT, DELETE, and PATCH methods are blocked "
            + "with HTTP 403. GET and HEAD are unaffected.",
            FormField.OPTIONAL);
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
        return 4;
    }

    @Override
    public String about() {
        return "Controls which repositories AI agents can access on a scoped port. "
            + "Audit mode logs without blocking. Read-only mode blocks uploads and deletes. "
            + "Create multiple instances for different agent contexts. "
            + "Changes apply instantly without restart.";
    }

    @Override
    public List<FormField> formFields() {
        return List.of(portField, reposField, auditField, readOnlyField);
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected Object createConfig(Map properties) {
        return properties;
    }
}
