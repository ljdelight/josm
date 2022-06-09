// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.io;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import javax.swing.JLabel;
import javax.swing.JTextField;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

import java.util.Arrays;
import java.util.Collections;

@BasicPreferences
class UploadTextComponentValidatorTest {
    /**
     * Unit test of {@link UploadTextComponentValidator.UploadCommentValidator}
     */
    @Test
    void testUploadCommentValidator() {
        JTextField textField = new JTextField();
        JLabel feedback = new JLabel();
        new UploadTextComponentValidator.UploadCommentValidator(textField, feedback);
        assertThat(feedback.getText(), containsString("Your upload comment is <i>empty</i>, or <i>very short</i>"));
        textField.setText("a comment long enough");
        assertThat(feedback.getText(), containsString("Thank you for providing a changeset comment"));
        textField.setText("a");
        assertThat(feedback.getText(), containsString("Your upload comment is <i>empty</i>, or <i>very short</i>"));
    }

    /**
     * Unit test of {@link UploadTextComponentValidator.UploadSourceValidator}
     */
    @Test
    void testUploadSourceValidator() {
        JTextField textField = new JTextField();
        JLabel feedback = new JLabel();
        new UploadTextComponentValidator.UploadSourceValidator(textField, feedback);
        assertThat(feedback.getText(), containsString("You did not specify a source for your changes"));
        textField.setText("a comment long enough");
        assertThat(feedback.getText(), containsString("Thank you for providing the data source"));
    }

    /**
     * Unit test of {@link UploadTextComponentValidator.UploadCommentValidator} with mandatory terms
     */
    @Test
    void testUploadCommentWithMandatoryTerm() {
        testUploadWithMandatoryTermHelper("upload.comment.mandatory-terms");
    }


    /**
     * Unit test of {@link UploadTextComponentValidator.UploadSourceValidator} with mandatory terms
     */
    @Test
    void testUploadSourceWithMandatoryTerm() {
        testUploadWithMandatoryTermHelper("upload.source.mandatory-terms");
    }

    void testUploadWithMandatoryTermHelper(String confPref) {
        Config.getPref().putList(confPref, Arrays.asList("myrequired", "xyz"));
        JTextField textField = new JTextField("");
        JLabel feedback = new JLabel();

        if ("upload.comment.mandatory-terms".equalsIgnoreCase(confPref)) {
            new UploadTextComponentValidator.UploadCommentValidator(textField, feedback);
        } else if ("upload.source.mandatory-terms".equalsIgnoreCase(confPref)) {
            new UploadTextComponentValidator.UploadSourceValidator(textField, feedback);
        } else {
            assertThat("Invalid configuration pref", false);
        }

        // A too-short string should fail validation
        textField.setText("");
        assertThat(feedback.getText(), containsString("The following required terms are missing: [myrequired, xyz]"));

        // A long enough string without the mandatory terms should claim that the required terms are missing
        textField.setText("a string long enough but missing the mandatory term");
        assertThat(feedback.getText(), containsString("The following required terms are missing: [myrequired, xyz]"));

        // A valid string should pass
        textField.setText("a string long enough with the mandatory term #myrequired #xyz");
        if ("upload.comment.mandatory-terms".equalsIgnoreCase(confPref)) {
            assertThat(feedback.getText(), containsString("Thank you for providing a changeset comment"));
        } else {
            assertThat(feedback.getText(), containsString("Thank you for providing the data source"));
        }

        Config.getPref().putList(confPref, Collections.emptyList());
    }
}
