package com.customentityhighlight;

import com.google.gson.Gson;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.AbstractButton;
import javax.swing.Icon;
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
	// Slightly lighter than the group cards, so fields and buttons stand out from them
	private static final Color CONTROL_BACKGROUND = ColorScheme.DARK_GRAY_COLOR;
	// RuneLite's look and feel is FlatLaf, which draws a checkbox's box from these style keys rather than its background
	private static final String CHECKBOX_STYLE = String.format("icon.background: #%1$06x; icon.selectedBackground: #%1$06x",
		CONTROL_BACKGROUND.getRGB() & 0xFFFFFF);

	private final CustomEntityHighlightPlugin plugin;
	private final ColorPickerManager colorPickerManager;
	private final Gson gson;
	private final JPanel groupsContainer = new JPanel(new DynamicGridLayout(0, 1, 0, 8));

	private List<HighlightGroup> groups;

	@Inject
	private CustomEntityHighlightPanel(CustomEntityHighlightPlugin plugin, ColorPickerManager colorPickerManager, Gson gson)
	{
		this.plugin = plugin;
		this.colorPickerManager = colorPickerManager;
		this.gson = gson;

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

		JButton importButton = new JButton("Import group");
		importButton.setFocusPainted(false);
		importButton.setToolTipText("Add a group copied with a group's Export button");
		importButton.addActionListener(e -> importGroup());

		JPanel headerButtons = new JPanel(new GridLayout(1, 2, 4, 0));
		headerButtons.add(addButton);
		headerButtons.add(importButton);
		header.add(headerButtons, BorderLayout.SOUTH);

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

	/**
	 * Adds the group on the clipboard, if it holds one exported by {@link #exportGroup}.
	 */
	private void importGroup()
	{
		String text;
		try
		{
			text = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
		}
		catch (UnsupportedFlavorException | IOException | IllegalStateException e)
		{
			text = null;
		}

		HighlightGroup group = HighlightGroup.fromExportString(gson, text);
		if (group == null)
		{
			JOptionPane.showMessageDialog(this, "The clipboard doesn't contain an exported highlight group.",
				"Import group", JOptionPane.WARNING_MESSAGE);
			return;
		}

		// Show the imported group's settings, however it was left when exported
		group.setCollapsed(false);
		groups.add(group);
		save();
		rebuild();
	}

	private void exportGroup(HighlightGroup group)
	{
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(group.toExportString(gson)), null);
		JOptionPane.showMessageDialog(this, "Copied the group \"" + group.getName() + "\" to the clipboard. "
			+ "Use Import group to add it.", "Export group", JOptionPane.INFORMATION_MESSAGE);
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

		// Advanced: rows hidden until expanded; they stay in the same grid so their labels line up
		JPanel advancedHeader = new JPanel(new BorderLayout(4, 0));
		advancedHeader.setOpaque(false);
		advancedHeader.setBorder(new EmptyBorder(4, 0, 0, 0));
		JLabel advancedTitle = new JLabel("Advanced");
		advancedTitle.setFont(FontManager.getRunescapeSmallFont());
		advancedHeader.add(advancedTitle, BorderLayout.WEST);
		JButton advancedToggle = new JButton();
		advancedToggle.setFocusPainted(false);
		advancedToggle.setMargin(new Insets(0, 4, 0, 4));
		advancedHeader.add(advancedToggle, BorderLayout.EAST);
		addWideRow(body, row++, advancedHeader);

		List<JComponent> advanced = new ArrayList<>();

		advanced.addAll(addRow(body, row++, "Width", spinner(group.getBorderWidth(), HighlightGroup.MIN_BORDER_WIDTH,
			HighlightGroup.MAX_BORDER_WIDTH, group::setBorderWidth)));
		advanced.addAll(addRow(body, row++, "Feather", spinner(group.getOutlineFeather(), 0,
			HighlightGroup.MAX_OUTLINE_FEATHER, group::setOutlineFeather)));

		JCheckBox swap = checkBox("Swap left-click", group.isSwapLeftClick(), group::setSwapLeftClick);
		swap.setToolTipText("<html>Make the option matched by the first matching Options entry the left-click "
			+ "option<br>for matched NPCs and objects. Names entries aren't used.</html>");
		advanced.addAll(addRow(body, row++, "", swap));

		advanced.addAll(addRow(body, row++, "", checkBox("Show labels", group.isShowNames(), group::setShowNames)));

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
		advanced.addAll(addRow(body, row++, "Label", labelSource));
		advanced.addAll(addRow(body, row, "Text", customLabel));
		card.add(body, BorderLayout.CENTER);

		Runnable showAdvanced = () ->
		{
			for (JComponent component : advanced)
			{
				component.setVisible(group.isAdvancedExpanded());
			}
			advancedToggle.setText(group.isAdvancedExpanded() ? "-" : "+");
			advancedToggle.setToolTipText(group.isAdvancedExpanded() ? "Hide advanced settings" : "Show advanced settings");
		};
		showAdvanced.run();
		advancedToggle.addActionListener(e ->
		{
			group.setAdvancedExpanded(!group.isAdvancedExpanded());
			showAdvanced.run();
			groupsContainer.revalidate();
			groupsContainer.repaint();
			save();
		});

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

		// Footer: reorder, export and delete
		JPanel actions = new JPanel(new BorderLayout(4, 0));
		actions.setOpaque(false);

		JPanel arrows = new JPanel(new GridLayout(1, 2, 4, 0));
		arrows.setOpaque(false);

		JButton up = arrowButton(true, "Move up; later groups draw on top");
		up.setEnabled(index > 0);
		up.addActionListener(e -> moveGroup(index, index - 1));
		arrows.add(up);

		JButton down = arrowButton(false, "Move down; later groups draw on top");
		down.setEnabled(index < groups.size() - 1);
		down.addActionListener(e -> moveGroup(index, index + 1));
		arrows.add(down);
		actions.add(arrows, BorderLayout.WEST);

		JPanel groupActions = new JPanel(new GridLayout(1, 2, 4, 0));
		groupActions.setOpaque(false);

		JButton export = actionButton("Export", "Copy this group to the clipboard, to share or import later");
		export.addActionListener(e -> exportGroup(group));
		groupActions.add(export);

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
		groupActions.add(delete);
		actions.add(groupActions, BorderLayout.CENTER);
		card.add(actions, BorderLayout.SOUTH);

		lightenControls(card);
		return card;
	}

	/**
	 * Gives the text fields, dropdowns, spinners, buttons and checkboxes in a card the lighter control background,
	 * including the parts inside dropdowns and spinners. Color buttons keep showing their color.
	 */
	private static void lightenControls(Container container)
	{
		for (Component component : container.getComponents())
		{
			if (component instanceof ColorJButton)
			{
				continue;
			}

			if (component instanceof JCheckBox)
			{
				((JCheckBox) component).putClientProperty("FlatLaf.style", CHECKBOX_STYLE);
			}
			else if (component instanceof JTextComponent || component instanceof JComboBox
				|| component instanceof JSpinner || component instanceof AbstractButton)
			{
				component.setBackground(CONTROL_BACKGROUND);
			}

			if (component instanceof Container)
			{
				lightenControls((Container) component);
			}
		}
	}

	private void moveGroup(int from, int to)
	{
		groups.add(to, groups.remove(from));
		save();
		rebuild();
	}

	/**
	 * Adds a labelled row and returns its label and component, e.g. to hide them together.
	 */
	private static List<JComponent> addRow(JPanel body, int row, String label, JComponent component)
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
		return Arrays.asList(jLabel, component);
	}

	/**
	 * Adds a row whose component spans both the label and component columns.
	 */
	private static void addWideRow(JPanel body, int row, JComponent component)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.gridy = row;
		c.gridx = 0;
		c.gridwidth = 2;
		c.insets = new Insets(2, 0, 2, 0);
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

	private static JButton arrowButton(boolean up, String tooltip)
	{
		JButton button = new JButton(new ArrowIcon(up));
		button.setFocusPainted(false);
		button.setToolTipText(tooltip);
		return button;
	}

	/**
	 * A small triangle pointing up or down, drawn in the button's text color. The RuneScape font has no arrow glyphs.
	 */
	private static class ArrowIcon implements Icon
	{
		private static final int WIDTH = 9;
		private static final int HEIGHT = 5;

		private final boolean up;

		private ArrowIcon(boolean up)
		{
			this.up = up;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(c.isEnabled() ? c.getForeground() : ColorScheme.MEDIUM_GRAY_COLOR);
			int tipY = up ? y : y + HEIGHT;
			int baseY = up ? y + HEIGHT : y;
			g2.fill(new Polygon(new int[]{x, x + WIDTH / 2, x + WIDTH}, new int[]{baseY, tipY, baseY}, 3));
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return WIDTH;
		}

		@Override
		public int getIconHeight()
		{
			return HEIGHT;
		}
	}
}
