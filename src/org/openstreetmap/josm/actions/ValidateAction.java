// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.Optional;

import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.preferences.sources.ValidatorPrefHelper;
import org.openstreetmap.josm.data.validation.OsmValidator;
import org.openstreetmap.josm.data.validation.Test;
import org.openstreetmap.josm.data.validation.ValidationTask;
import org.openstreetmap.josm.data.validation.util.AggregatePrimitivesVisitor;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.autofilter.AutoFilterManager;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * The action that does the validate thing.
 * <p>
 * This action iterates through all active tests and give them the data, so that
 * each one can test it.
 *
 * @author frsantos
 */
public class ValidateAction extends JosmAction {

    /** Last selection used to validate */
    private transient Collection<OsmPrimitive> lastSelection;

    /**
     * Constructor
     */
    public ValidateAction() {
        super(tr("Validation"), "dialogs/validator", tr("Performs the data validation"),
                Shortcut.registerShortcut("tools:validate", tr("Validation"),
                        KeyEvent.VK_V, Shortcut.SHIFT), true);
    }

    @Override
    public void actionPerformed(ActionEvent ev) {
        // Warn user if validating while filters are active and see if they wish to
        // continue, as OSM primitives are excluded from validation due to the
        // filtering (which could possibly lead to a false sense of confidence)
        boolean filtersActive = AutoFilterManager.getInstance().getCurrentAutoFilter() != null ||
            (MainApplication.getMap() != null &&
             MainApplication.getMap().filterDialog != null &&
             MainApplication.getMap().filterDialog.getFilterModel().hasDisabledOrHiddenPrimitives());
        if (filtersActive && "fail".equals(ValidatorPrefHelper.PREF_VALIDATE_WITH_FILTERS_ENABLED_ACTION.get())) {
            // To avoid any confusion, if user has already indicated they do not want to validate
            // when filters are active, then let them know that validation was not performed
            // due to active filters
            GuiHelper.runInEDT(() -> JOptionPane.showMessageDialog(
                    MainApplication.getMainFrame(),
                    tr("Validation not performed due to filters being active. Please disable filters."),
                    tr("Error"),
                    JOptionPane.ERROR_MESSAGE
            ));
            return;
        }

        if (filtersActive && "ask".equals(ValidatorPrefHelper.PREF_VALIDATE_WITH_FILTERS_ENABLED_ACTION.get())) {
            JPanel p = new JPanel(new GridBagLayout());
            p.add(new JLabel("<html><body style=\"width: 375px;\">" +
                    tr("You are validating while filters are active, this could: ") + "<br/>" +
                    "<ul>" +
                    "<li>" + tr("Potentially exclude objects from validation.") + "</li>" +
                    "<li>" + tr("Make it harder to see validation objects if they are hidden by a filter.") + "</li>" +
                    "</ul><br/>" +
                    tr("Do you want to continue?") +
                    "</body></html>"), GBC.eol());

            JRadioButton validateWithFiltersAsk = new JRadioButton(tr("Show this dialog again the next time"));
            validateWithFiltersAsk.setActionCommand("ask");
            JRadioButton validateWithFiltersContinue = new JRadioButton(tr("Do not show this again (remembers choice)"));
            validateWithFiltersContinue.setActionCommand("continue");
            JRadioButton validateWithFiltersFail = new JRadioButton(
                    tr("Skip validation and show an error message the next time (remembers choice)"));
            validateWithFiltersFail.setActionCommand("fail");

            switch(ValidatorPrefHelper.PREF_VALIDATE_WITH_FILTERS_ENABLED_ACTION.get()) {
                case "ask":
                    validateWithFiltersAsk.setSelected(true);
                    break;
                case "continue":
                    validateWithFiltersContinue.setSelected(true);
                    break;
                case "fail":
                    validateWithFiltersFail.setSelected(true);
                    break;
            }

            ButtonGroup validateWithFiltersGroup = new ButtonGroup();
            validateWithFiltersGroup.add(validateWithFiltersAsk);
            validateWithFiltersGroup.add(validateWithFiltersContinue);
            validateWithFiltersGroup.add(validateWithFiltersFail);

            p.add(validateWithFiltersAsk, GBC.eol().insets(30, 10, 0, 0));
            p.add(validateWithFiltersContinue, GBC.eol().insets(30, 0, 0, 0));
            p.add(validateWithFiltersFail, GBC.eol().insets(30, 0, 0, 10));

            ExtendedDialog ed = new ExtendedDialog(MainApplication.getMainFrame(),
                    tr("Validation with active filters"),
                    tr("Continue"),
                    tr("Cancel"))
                    .setContent(p)
                    .configureContextsensitiveHelp("Dialog/Validator", true);

            ed.setButtonIcons("ok", "cancel");

            int result = ed.showDialog().getValue();

            if (result != 1) {
                Logging.debug("Dialog result indicates the user does not want to continue with validation");
                return;
            }

            // Update the persistent preference based on the user's selection
            ValidatorPrefHelper.PREF_VALIDATE_WITH_FILTERS_ENABLED_ACTION.put(validateWithFiltersGroup.getSelection().getActionCommand());

            // Return (skip validation) if the user wants to skip validation and show the error dialog next time
            if ("fail".equals(validateWithFiltersGroup.getSelection().getActionCommand())) {
                return;
            }
        }

        doValidate(true);
    }

    /**
     * Does the validation.
     * <p>
     * If getSelectedItems is true, the selected items (or all items, if no one
     * is selected) are validated. If it is false, last selected items are revalidated
     *
     * @param getSelectedItems If selected or last selected items must be validated
     */
    public void doValidate(boolean getSelectedItems) {
        MapFrame map = MainApplication.getMap();
        if (map == null || !map.isVisible())
            return;

        OsmValidator.initializeTests();

        Collection<Test> tests = OsmValidator.getEnabledTests(false);
        if (tests.isEmpty())
            return;

        Collection<OsmPrimitive> selection;
        if (getSelectedItems) {
            selection = getLayerManager().getActiveDataSet().getAllSelected();
            if (selection.isEmpty()) {
                selection = getLayerManager().getActiveDataSet().allNonDeletedPrimitives();
                lastSelection = null;
            } else {
                AggregatePrimitivesVisitor v = new AggregatePrimitivesVisitor();
                selection = v.visit(selection);
                lastSelection = selection;
            }
        } else {
            selection = Optional.ofNullable(lastSelection).orElseGet(
                    () -> getLayerManager().getActiveDataSet().allNonDeletedPrimitives());
        }

        MainApplication.worker.submit(new ValidationTask(tests, selection, lastSelection));
    }

    @Override
    public void updateEnabledState() {
        setEnabled(getLayerManager().getActiveDataSet() != null);
    }

    @Override
    public void destroy() {
        // Hack - this action should stay forever because it could be added to toolbar
        // Do not call super.destroy() here
        lastSelection = null;
    }

}
