// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.colocation;

import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.spi.preferences.Config;

import javax.swing.ButtonGroup;
import javax.swing.ButtonModel;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.event.ChangeEvent;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.HashMap;
import java.util.Map;

import static org.openstreetmap.josm.tools.I18n.tr;

/**
 * Duplicated node resolver is used to detect nodes that are at the same LatLon point during a geojson import.
 *
 * @since xxx
 */
public class DuplicatedNodesResolver {
    // Resolution choices. Strings are used for easy interoperability with
    // preference storage
    /**
     * Keep only one node found at location (recommended behavior)
     */
    public static final String RESOLVE_KEEP_ONE = "one";

    /**
     * Keep all nodes found at location
     */
    public static final String RESOLVE_KEEP_ALL = "all";

    // Future application choices
    /**
     * Prompt user to manually resolve next future incident
     */
    public static final int APPLY_PROMPT = 1;

    /**
     * Resolve detected colocations in chosen manner for all nodes sharing the
     * same location as this incident, but prompting user again for new
     * locations
     */
    public static final int APPLY_ALL_AT_LOCATION = 2;

    /**
     * Resolve detected colocations in chosen manner for all further incidents
     * and do not prompt user further
     */
    public static final int APPLY_ALL = 3;

    /**
     * The current resolution choice for this resolver (see RESOLVE_* constants)
     */
    private String currentResolution;

    /**
     * The current application choice for this resolver (see APPLY_* constants)
     */
    private int currentApplication;

    /**
     * Map of locations to resolution decision. An entry exists when the user has chosen to apply a
     * resolution to all future detected nodes at that location
     */
    private final Map<LatLon, String> locationDecisions;

    /**
     * Constructs a {@link DuplicatedNodesResolver} with the backward-compatible behavior of
     * automatically keeping only one node found at a location
     */
    public DuplicatedNodesResolver() {
        this(RESOLVE_KEEP_ONE, APPLY_ALL);
    }

    public DuplicatedNodesResolver(final String defaultResolution, final int defaultApplication) {
        this.currentResolution = defaultResolution;
        this.currentApplication = defaultApplication;
        this.locationDecisions = new HashMap<>();
    }

    /**
     * Resolves detected duplicated nodes at a specified location, either using a
     * decision made in past that is to apply to future duplications, or else
     * prompting the user to make a decision
     *
     * @param latlon the location to check for a resolution
     * @return the resolution
     */
    public String resolveDuplicatedNodes(final LatLon latlon) {
        // First, is there a decision specific to this location?
        if (this.locationDecisions.containsKey(latlon)) {
            return this.locationDecisions.get(latlon);
        }

        // Next, is there a decision to apply to all detected colocations?
        if (this.currentApplication == APPLY_ALL) {
            return this.currentResolution;
        }

        // Is there a previously saved choice stored in preferences?
        final String preferenceKey = "import.duplicated-nodes.keep";
        final String resolution = Config.getPref().get(preferenceKey, null);

        if (RESOLVE_KEEP_ONE.equals(resolution) || RESOLVE_KEEP_ALL.equals(resolution)) {
            return resolution;
        }

        // Otherwise ask the user how to resolve
        GuiHelper.runInEDTAndWait(() -> {
            ResolveDialog dialog = new ResolveDialog(latlon, this.currentApplication);
            dialog.showDialog();
            switch (dialog.getValue()) {
                case 1:
                    this.currentResolution = RESOLVE_KEEP_ONE;
                    break;
                case 2:
                    this.currentResolution = RESOLVE_KEEP_ALL;
                    break;
            }
            this.currentApplication = dialog.getApplyToValue();

            if (this.currentApplication == APPLY_ALL_AT_LOCATION) {
                this.locationDecisions.put(latlon, this.currentResolution);
            }

            if (dialog.shouldSaveChoice()) {
                Config.getPref().put(preferenceKey, this.currentResolution);
            }
        });

        return this.currentResolution;
    }

    /**
     * Dialog that prompts user to decide how to treat detected duplicated nodes
     */
    private static class ResolveDialog extends ExtendedDialog {
        private final ButtonGroup applyOptionsGroup;
        private final JCheckBox rememberCheckbox;

        ResolveDialog(final LatLon latlon, final int currentApplication) {
            super(MainApplication.getMainFrame(),
                    tr("Resolve Co-located Nodes"),
                    tr("Keep One Node (recommended)"), tr("Keep All Nodes"));

            setIcon(JOptionPane.WARNING_MESSAGE);
            JPanel dialogPanel = new JPanel(new BorderLayout());
            JPanel rememberChoicePanel = new JPanel(new BorderLayout());
            dialogPanel.add(new JLabel("<html>"
                            + tr("Import contains multiple nodes positioned at ")
                            + latlon.toDisplayString()
                            + ".<br/>"
                            + tr("How would you like to proceed with these nodes?")
                            + "<br/><br/>"
                            + "</html>"),
                    BorderLayout.NORTH);

            // Options for applying chosen resolution to future duplicated nodes
            JPanel applyOptionsPanel = new JPanel(new FlowLayout());
            JPanel buttonGroupPanel = new JPanel(new FlowLayout());
            this.applyOptionsGroup = new ButtonGroup();

            JRadioButton locationOption = new JRadioButton(tr("This location"));
            locationOption.setActionCommand("location");
            this.applyOptionsGroup.add(locationOption);
            locationOption.setSelected(currentApplication == APPLY_ALL_AT_LOCATION);
            buttonGroupPanel.add(locationOption);

            JRadioButton allOption = new JRadioButton(tr("All locations"));
            allOption.setActionCommand("all");
            this.applyOptionsGroup.add(allOption);
            allOption.setSelected(currentApplication != APPLY_ALL_AT_LOCATION);

            // Listen for changes to the applies-to-all radio button, as we
            // only want to offer the option to remember choice if the choice
            // is being applied to all nodes rather than a single location, as
            // we naturally have to prompt for each new location if user wants
            // per-location option
            allOption.addChangeListener((ChangeEvent e) -> {
                if (allOption.isSelected()) {
                    if (!dialogPanel.isAncestorOf(rememberChoicePanel)) {
                        dialogPanel.add(rememberChoicePanel, BorderLayout.SOUTH);
                    }
                } else {
                    dialogPanel.remove(rememberChoicePanel);
                }

                this.revalidate();
                this.pack();
                this.repaint();
            });
            buttonGroupPanel.add(allOption);

            applyOptionsPanel.add(new JLabel(tr("Apply this choice to:")));
            applyOptionsPanel.add(buttonGroupPanel);
            dialogPanel.add(applyOptionsPanel, BorderLayout.CENTER);

            // Remember this decision? Only available for all-locations choice,
            // as we naturally have to prompt for each new location if user
            // wants per-location option
            this.rememberCheckbox = new JCheckBox(tr("Don''t ask me again"));
            rememberChoicePanel.add(this.rememberCheckbox, BorderLayout.SOUTH);
            if (currentApplication != APPLY_ALL_AT_LOCATION) {
                dialogPanel.add(rememberChoicePanel, BorderLayout.SOUTH);
            }

            setContent(dialogPanel);
        }

        public int getApplyToValue() {
            final ButtonModel selected = this.applyOptionsGroup.getSelection();
            if (selected != null && "all".equals(selected.getActionCommand())) {
                return APPLY_ALL;
            } else {
                return APPLY_ALL_AT_LOCATION;
            }
        }

        public boolean shouldSaveChoice() {
            // Only allow apply-to-all choices to be saved
            return this.getApplyToValue() == APPLY_ALL && this.rememberCheckbox.isSelected();
        }
    }
}
