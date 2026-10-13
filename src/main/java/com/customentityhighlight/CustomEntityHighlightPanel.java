package com.customentityhighlight;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.JTextComponent;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.ColorJButton;
import net.runelite.client.ui.components.colorpicker.ColorPickerManager;
import net.runelite.client.ui.components.colorpicker.RuneliteColorPicker;
import net.runelite.client.util.ColorUtil;

/**
 * Side panel for adding, removing, reordering and editing highlight groups.
 * Holds its own copies of the groups and saves the whole list after every change.
 */
class CustomEntityHighlightPanel extends PluginPanel
{
	private final CustomEntityHighlightPlugin plugin;
	private final ColorPickerManager colorPickerManager;
	private final JPanel groupsContainer = new JPanel(new DynamicGridLayout(0, 1, 0, 8));

	private List<HighlightGroup> groups;

	@Inject
	private CustomEntityHighlightPanel(CustomEntityHighlightPlugin plugin, ColorPickerManager colorPickerManager)
	{
		this.plugin = plugin;
		this.colorPickerManager = colorPickerManager;

		setLayout(new BorderLayout(0, 8));
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel header = new JPanel(new BorderLayout(0, 6));
		JLabel title = new JLabel("Custom Entity Highlight");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		header.add(title, BorderLayout.NORTH);

		JLabel help = new JLabel("<html>Each group highlights NPCs and objects that match any of its menu options "
			+ "or names. Separate entries with commas or new lines; options and names can use * as a wildcard, "
			+ "e.g. <b>bank*</b>, or a regex in slashes, e.g. <b>/bank(-all)?/</b>.</html>");
		help.setFont(FontManager.getRunescapeSmallFont());
		header.add(help, BorderLayout.CENTER);

		JButton addButton = new JButton("Add group");
		addButton.setFocusPainted(false);
		addButton.addActionListener(e ->
		{
			groups.add(new HighlightGroup());
			save();
			rebuild();
		});
		header.add(addButton, BorderLayout.SOUTH);

		add(header, BorderLayout.NORTH);
		add(groupsContainer, BorderLayout.CENTER);

		rebuild();
	}

	/**
	 * Reloads the groups from config and recreates every group card.
	 */
	void rebuild()
	{
		groups = plugin.readSavedGroups();
		groupsContainer.removeAll();
		for (int i = 0; i < groups.size(); i++)
		{
			groupsContainer.add(createGroupPanel(groups.get(i), i));
		}
		groupsContainer.revalidate();
		groupsContainer.repaint();
	}

	private void save()
	{
		plugin.saveGroups(groups);
	}

	private JPanel createGroupPanel(HighlightGroup group, int index)
	{
		JPanel card = new JPanel(new BorderLayout(0, 6));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(8, 8, 8, 8));

		// Header: enabled toggle, name and collapse toggle
		JPanel header = new JPanel(new BorderLayout(4, 0));
		header.setOpaque(false);

		JCheckBox enabled = checkBox(null, group.isEnabled(), group::setEnabled);
		enabled.setToolTipText("Enable or disable this group");
		header.add(enabled, BorderLayout.WEST);

		JTextField name = new JTextField(group.getName());
		name.setToolTipText("Group name");
		onCommit(name, text -> group.setName(text.trim()));
		header.add(name, BorderLayout.CENTER);
		card.add(header, BorderLayout.NORTH);

		// Body: matching and appearance settings
		JPanel body = new JPanel(new GridBagLayout());
		body.setOpaque(false);
		int row = 0;

		JTextArea options = entryList(group.getMenuOptions());
		onCommit(options, group::setMenuOptions);
		showInvalidEntries(options, "Right-click menu options, e.g. \"bank, collect\", or one per line. "
			+ "Use * as a wildcard, or /regex/.");
		addRow(body, row++, "Options", scrollable(options));

		JTextArea names = entryList(group.getEntityNames());
		onCommit(names, group::setEntityNames);
		showInvalidEntries(names, "NPC or object names, e.g. \"banker, *chest\", or one per line. "
			+ "Use * as a wildcard, or /regex/.");
		addRow(body, row++, "Names", scrollable(names));

		// Each box gets its own width rather than an equal share, so "Objects" isn't cut off
		JPanel targets = new JPanel(new BorderLayout(4, 0));
		targets.setOpaque(false);
		targets.add(checkBox("NPCs", group.isHighlightNpcs(), group::setHighlightNpcs), BorderLayout.WEST);
		targets.add(checkBox("Objects", group.isHighlightObjects(), group::setHighlightObjects), BorderLayout.CENTER);
		addRow(body, row++, "Highlight", targets);

		JCheckBox swap = checkBox("Swap left-click", group.isSwapLeftClick(), group::setSwapLeftClick);
		swap.setToolTipText("<html>Make the option matched by the first matching Options entry the left-click "
			+ "option<br>for matched NPCs and objects. Names entries aren't used.</html>");
		addRow(body, row++, "", swap);

		JComboBox<HighlightStyle> style = new JComboBox<>(HighlightStyle.values());
		style.setSelectedItem(group.getStyleOrDefault());
		style.setToolTipText("<html>Outline: traces the model<br>Hull: border around the model<br>"
			+ "Area: model filled in, no border<br>Clickbox: the exact area you can click<br>Tile: the tiles it stands on<br>"
			+ "None: no highlight, only the label if Show labels is on</html>");
		style.addActionListener(e ->
		{
			group.setStyle((HighlightStyle) style.getSelectedItem());
			save();
		});
		addRow(body, row++, "Style", style);

		addRow(body, row++, "Color", colorButton("Highlight color", group.getHighlightColorValue(),
			color -> group.setHighlightColor(color.getRGB())));
		addRow(body, row++, "Fill", colorButton("Fill color", group.getFillColorValue(),
			color -> group.setFillColor(color.getRGB())));

		addRow(body, row++, "Width", spinner(group.getBorderWidth(), HighlightGroup.MIN_BORDER_WIDTH,
			HighlightGroup.MAX_BORDER_WIDTH, group::setBorderWidth));
		addRow(body, row++, "Feather", spinner(group.getOutlineFeather(), 0,
			HighlightGroup.MAX_OUTLINE_FEATHER, group::setOutlineFeather));

		addRow(body, row++, "", checkBox("Show labels", group.isShowNames(), group::setShowNames));

		JTextField customLabel = new JTextField(group.getCustomLabel());
		customLabel.setToolTipText("Text shown when Label is Custom. Leave blank to show the entity's name.");
		customLabel.setEnabled(group.getLabelSourceOrDefault() == LabelSource.CUSTOM);
		onCommit(customLabel, text -> group.setCustomLabel(text.trim()));

		JComboBox<LabelSource> labelSource = new JComboBox<>(LabelSource.values());
		labelSource.setSelectedItem(group.getLabelSourceOrDefault());
		labelSource.setToolTipText("<html>Text drawn when Show labels is on<br>Name: the NPC or object name<br>"
			+ "Match: the first Options entry it matched, or else the first Names entry<br>Custom: the text below</html>");
		labelSource.addActionListener(e ->
		{
			LabelSource selected = (LabelSource) labelSource.getSelectedItem();
			group.setLabelSource(selected);
			customLabel.setEnabled(selected == LabelSource.CUSTOM);
			save();
		});
		addRow(body, row++, "Label", labelSource);
		addRow(body, row, "Text", customLabel);
		card.add(body, BorderLayout.CENTER);

		JButton collapse = new JButton();
		collapse.setFocusPainted(false);
		collapse.setMargin(new Insets(0, 4, 0, 4));
		Runnable showCollapsed = () ->
		{
			body.setVisible(!group.isCollapsed());
			collapse.setText(group.isCollapsed() ? "+" : "-");
			collapse.setToolTipText(group.isCollapsed() ? "Show this group's settings" : "Hide this group's settings");
		};
		showCollapsed.run();
		collapse.addActionListener(e ->
		{
			group.setCollapsed(!group.isCollapsed());
			showCollapsed.run();
			groupsContainer.revalidate();
			groupsContainer.repaint();
			save();
		});
		header.add(collapse, BorderLayout.EAST);

		// Footer: reorder and delete
		JPanel actions = new JPanel(new GridLayout(1, 3, 4, 0));
		actions.setOpaque(false);

		JButton up = actionButton("Up", "Move up; later groups draw on top");
		up.setEnabled(index > 0);
		up.addActionListener(e -> moveGroup(index, index - 1));
		actions.add(up);

		JButton down = actionButton("Down", "Move down; later groups draw on top");
		down.setEnabled(index < groups.size() - 1);
		down.addActionListener(e -> moveGroup(index, index + 1));
		actions.add(down);

		JButton delete = actionButton("Delete", "Delete this group");
		delete.addActionListener(e ->
		{
			int confirm = JOptionPane.showConfirmDialog(this,
				"Delete the group \"" + group.getName() + "\"?", "Delete group", JOptionPane.YES_NO_OPTION);
			if (confirm == JOptionPane.YES_OPTION)
			{
				groups.remove(index);
				save();
				rebuild();
			}
		});
		actions.add(delete);
		card.add(actions, BorderLayout.SOUTH);

		return card;
	}

	private void moveGroup(int from, int to)
	{
		groups.add(to, groups.remove(from));
		save();
		rebuild();
	}

	private static void addRow(JPanel body, int row, String label, JComponent component)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.gridy = row;
		c.insets = new Insets(2, 0, 2, 0);
		c.anchor = GridBagConstraints.WEST;

		c.gridx = 0;
		c.ipadx = 6;
		JLabel jLabel = new JLabel(label);
		jLabel.setFont(FontManager.getRunescapeSmallFont());
		body.add(jLabel, c);

		c.gridx = 1;
		c.ipadx = 0;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		body.add(component, c);
	}

	/**
	 * A three-row text area for a list of entries, where Enter starts a new line.
	 * Tab still moves to the next field instead of typing a tab.
	 */
	private static JTextArea entryList(String text)
	{
		// One column keeps the preferred width small so the area fills the row instead of widening the panel
		JTextArea area = new JTextArea(text, 3, 1);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
		area.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
		return area;
	}

	private static JScrollPane scrollable(JTextArea area)
	{
		return new JScrollPane(area, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
	}

	/**
	 * Saves a text component's value when the user leaves it, or presses Enter in a single-line field.
	 */
	private void onCommit(JTextComponent field, Consumer<String> setter)
	{
		Runnable commit = () ->
		{
			setter.accept(field.getText());
			save();
		};
		if (field instanceof JTextField)
		{
			((JTextField) field).addActionListener(e -> commit.run());
		}
		field.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				commit.run();
			}
		});
	}

	/**
	 * Shows the field's text in red, with the errors as its tooltip, while it has a regex that doesn't compile.
	 * Checked now and whenever the user leaves the field.
	 */
	private static void showInvalidEntries(JTextArea field, String tooltip)
	{
		Color foreground = field.getForeground();
		Runnable check = () ->
		{
			List<String> errors = GroupMatcher.invalidEntries(field.getText());
			field.setForeground(errors.isEmpty() ? foreground : ColorScheme.PROGRESS_ERROR_COLOR);
			field.setToolTipText(errors.isEmpty() ? tooltip : "Invalid regex, ignored: " + String.join("; ", errors));
		};
		check.run();
		field.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				check.run();
			}
		});
	}

	private JCheckBox checkBox(String text, boolean selected, Consumer<Boolean> setter)
	{
		JCheckBox checkBox = new JCheckBox(text, selected);
		checkBox.setOpaque(false);
		checkBox.setFocusPainted(false);
		checkBox.addActionListener(e ->
		{
			setter.accept(checkBox.isSelected());
			save();
		});
		return checkBox;
	}

	private JSpinner spinner(int value, int min, int max, Consumer<Integer> setter)
	{
		JSpinner spinner = new JSpinner(new SpinnerNumberModel(Math.max(min, Math.min(max, value)), min, max, 1));
		spinner.addChangeListener(e ->
		{
			setter.accept((Integer) spinner.getValue());
			save();
		});
		return spinner;
	}

	private ColorJButton colorButton(String title, Color initial, Consumer<Color> setter)
	{
		ColorJButton button = new ColorJButton(ColorUtil.colorToAlphaHexCode(initial), initial);
		button.setFocusPainted(false);
		button.addActionListener(e ->
		{
			RuneliteColorPicker picker = colorPickerManager.create(SwingUtilities.windowForComponent(this),
				button.getColor(), title, false);
			picker.setLocationRelativeTo(button);
			picker.setOnColorChange(color ->
			{
				button.setColor(color);
				button.setText(ColorUtil.colorToAlphaHexCode(color));
				setter.accept(color);
				save();
			});
			picker.setVisible(true);
		});
		return button;
	}

	private static JButton actionButton(String text, String tooltip)
	{
		JButton button = new JButton(text);
		button.setFocusPainted(false);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setToolTipText(tooltip);
		return button;
	}
}
