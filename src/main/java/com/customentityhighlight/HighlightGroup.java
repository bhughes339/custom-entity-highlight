package com.customentityhighlight;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.Data;

/**
 * Which NPCs and objects to highlight (by menu option or name) and how to highlight them.
 * Saved to config as JSON, so colors are stored as ARGB ints.
 */
@Data
class HighlightGroup
{
	static final int MIN_BORDER_WIDTH = 1;
	static final int MAX_BORDER_WIDTH = 10;
	static final int MAX_OUTLINE_FEATHER = 4;

	private String name = "New group";
	private boolean enabled = true;
	// Whether the panel hides this group's settings
	private boolean collapsed = false;
	// Whether the panel shows this group's advanced settings
	private boolean advancedExpanded = false;
	// Comma-separated lists; an entity matches if any entry in any list matches
	private String menuOptions = "";
	private String entityNames = "";
	private boolean highlightNpcs = true;
	private boolean highlightObjects = true;
	// Skips NPCs and objects that can't be clicked or have no options besides Examine
	private boolean interactableOnly = false;
	// Moves the option matched by the first matching menuOptions entry to left-click
	private boolean swapLeftClick = false;
	private HighlightStyle style = HighlightStyle.OUTLINE;
	private int highlightColor = Color.CYAN.getRGB();
	private int fillColor = new Color(0, 255, 255, 20).getRGB();
	private int borderWidth = 2;
	private int outlineFeather = 0;
	private boolean showNames = false;
	private LabelSource labelSource = LabelSource.NAME;
	// Text shown when labelSource is CUSTOM; blank uses the entity's name
	private String customLabel = "";

	Color getHighlightColorValue()
	{
		return new Color(highlightColor, true);
	}

	Color getFillColorValue()
	{
		return new Color(fillColor, true);
	}

	HighlightStyle getStyleOrDefault()
	{
		// Null if the saved style no longer exists
		return style != null ? style : HighlightStyle.OUTLINE;
	}

	/**
	 * Encodes the group as Base64 JSON, for sharing as text.
	 */
	String toExportString(Gson gson)
	{
		return Base64.getEncoder().encodeToString(gson.toJson(this).getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Decodes a group from {@link #toExportString}. Whitespace is ignored. Returns null if the text isn't a group.
	 */
	static HighlightGroup fromExportString(Gson gson, String text)
	{
		if (text == null)
		{
			return null;
		}

		try
		{
			byte[] json = Base64.getDecoder().decode(text.replaceAll("\\s", ""));
			return gson.fromJson(new String(json, StandardCharsets.UTF_8), HighlightGroup.class);
		}
		catch (IllegalArgumentException | JsonParseException e)
		{
			// Not Base64, or not a group's JSON
			return null;
		}
	}

	LabelSource getLabelSourceOrDefault()
	{
		// Null if the saved label source no longer exists
		return labelSource != null ? labelSource : LabelSource.NAME;
	}
}
