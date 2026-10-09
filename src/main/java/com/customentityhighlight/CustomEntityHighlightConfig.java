package com.customentityhighlight;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(CustomEntityHighlightConfig.GROUP)
public interface CustomEntityHighlightConfig extends Config
{
	String GROUP = "customentityhighlight";
	String GROUPS_KEY = "groups";

	/**
	 * JSON list of {@link HighlightGroup}s, edited through the plugin's side panel.
	 */
	@ConfigItem(
		keyName = GROUPS_KEY,
		name = "Highlight groups",
		description = "Highlight groups, edited in the Custom Entity Highlight side panel",
		hidden = true
	)
	default String groups()
	{
		return "";
	}
}
