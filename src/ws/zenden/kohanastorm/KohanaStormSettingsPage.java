package ws.zenden.kohanastorm;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import  com.intellij.ide.util.PropertiesComponent;
import ws.zenden.kohanastorm.DefaultSettings;

public class KohanaStormSettingsPage  implements Configurable  {

    private JTextField appPathTextField;
    private JCheckBox enableKohanaStorm;
    private JTextField secretKeyTextField;
    Project project;

    public KohanaStormSettingsPage(Project project) {
        this.project = project;
    }

    @Nls
    @Override
    public String getDisplayName() {
        return "KohanaStorm";
    }

    @Override
    public JComponent createComponent() {

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout
                (panel,  BoxLayout.Y_AXIS));
        JPanel panel1 = new JPanel();
        panel1.setLayout(new BoxLayout(panel1, BoxLayout.X_AXIS));

        enableKohanaStorm = new JCheckBox("Enable Kohana Storm for this project");
        panel1.add(enableKohanaStorm);
        panel1.add(Box.createHorizontalGlue());

        JPanel panel2 = new JPanel();
        panel2.setLayout( new BoxLayout(panel2,  BoxLayout.X_AXIS));



        appPathTextField = new JTextField(30);
        JLabel label = new JLabel("Kohana APP PATH:");
        panel2.add( label );
        label.setLabelFor(appPathTextField);

        panel2.add( appPathTextField );
        panel2.add(Box.createHorizontalGlue());


        appPathTextField.setMaximumSize( appPathTextField.getPreferredSize() );

        JPanel panel3 = new JPanel();
        panel3.setLayout(new BoxLayout(panel3, BoxLayout.X_AXIS));

        JLabel label2 = new JLabel("Secret key:");

        secretKeyTextField = new JTextField(15);
        label2.setLabelFor(secretKeyTextField);
        panel3.add(label2);
        panel3.add(secretKeyTextField);
        panel3.add(Box.createHorizontalGlue());
        secretKeyTextField.setMaximumSize( appPathTextField.getPreferredSize() );

        panel.add( panel1);
        panel.add(Box.createVerticalStrut(8));
        
        panel.add( panel2 );
        panel.add(Box.createVerticalStrut(8));
        panel.add(panel3);
        panel.add(Box.createVerticalGlue());
        PropertiesComponent properties = PropertiesComponent.getInstance(project);
        appPathTextField.setText(properties.getValue("kohanaAppPath", DefaultSettings.kohanaAppPath));
        enableKohanaStorm.setSelected(properties.getBoolean("enableKohanaStorm", true));
        secretKeyTextField.setText(properties.getValue("kohanaStormSecretKey", DefaultSettings.secretKey));


        return panel;
    }

    @Override
    public void apply() throws ConfigurationException {
        PropertiesComponent properties = PropertiesComponent.getInstance(project);
        properties.setValue("kohanaAppPath", appPathTextField.getText());
        properties.setValue("enableKohanaStorm", String.valueOf(enableKohanaStorm.isSelected()) );
        properties.setValue("kohanaStormSecretKey", secretKeyTextField.getText());

    }

    @Override
    public boolean isModified() {
        return true;
    }

    @Override
    public String getHelpTopic() {
        return null;
    }

    @Override
    public void disposeUIResources() {

    }

    @Override
    public void reset() {

    }
}
