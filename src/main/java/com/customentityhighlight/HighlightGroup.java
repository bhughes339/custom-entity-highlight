package com.customentityhighlight;

import java.awt.Color;
import lombok.Data;

/**
 * Which NPCs and objects to highlight (by menu option, name or id) and how to highlight them.
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
	// Comma-separated lists; an entity matches if any entry in any list matches
	private String menuOptions = "";
	private String entityNames = "";
	private String entityIds = "";
	private boolean highlightNpcs = true;
	private boolean highlightObjects = true;
	private HighlightStyle style = HighlightStyle.OUTLINE;
	private int highlightColor = Color.CYAN.getRGB();
	private int fillColor = new Color(0, 255, 255, 20).getRGB();
	private int borderWidth = 2;
	private int outlineFeather = 0;
	private boolean showNames = false;

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
}
