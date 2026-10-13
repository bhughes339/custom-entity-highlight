package com.customentityhighlight;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.awt.Color;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class HighlightGroupTest
{
	private static final Type GROUP_LIST_TYPE = new TypeToken<List<HighlightGroup>>()
	{
	}.getType();

	private final Gson gson = new Gson();

	@Test
	public void roundTripsThroughJson()
	{
		HighlightGroup group = new HighlightGroup();
		group.setName("Thieving");
		group.setCollapsed(true);
		group.setSwapLeftClick(true);
		group.setAdvancedExpanded(true);
		group.setMenuOptions("pick*, steal-from");
		group.setStyle(HighlightStyle.CLICKBOX);
		group.setHighlightColor(new Color(255, 128, 0, 200).getRGB());
		group.setShowNames(true);
		group.setLabelSource(LabelSource.MATCH);
		group.setCustomLabel("Pickpocket");

		List<HighlightGroup> loaded = gson.fromJson(gson.toJson(Collections.singletonList(group), GROUP_LIST_TYPE), GROUP_LIST_TYPE);

		assertEquals(Collections.singletonList(group), loaded);
		assertEquals(new Color(255, 128, 0, 200), loaded.get(0).getHighlightColorValue());
	}

	@Test
	public void missingFieldsUseDefaults()
	{
		List<HighlightGroup> loaded = gson.fromJson("[{\"name\":\"Old\",\"menuOptions\":\"bank\"}]", GROUP_LIST_TYPE);
		HighlightGroup group = loaded.get(0);

		assertTrue(group.isEnabled());
		assertFalse(group.isCollapsed());
		assertFalse(group.isSwapLeftClick());
		assertFalse(group.isAdvancedExpanded());
		assertTrue(group.isHighlightNpcs());
		assertEquals(HighlightStyle.OUTLINE, group.getStyleOrDefault());
		assertEquals(2, group.getBorderWidth());
		assertEquals(LabelSource.NAME, group.getLabelSourceOrDefault());
	}

	@Test
	public void topOptionLabelsLoadAsMatch()
	{
		List<HighlightGroup> loaded = gson.fromJson("[{\"labelSource\":\"TOP_OPTION\"}]", GROUP_LIST_TYPE);

		assertEquals(LabelSource.MATCH, loaded.get(0).getLabelSourceOrDefault());
	}

	@Test
	public void unknownStyleFallsBackToOutline()
	{
		List<HighlightGroup> loaded = gson.fromJson("[{\"style\":\"NOT_A_STYLE\"}]", GROUP_LIST_TYPE);

		assertEquals(HighlightStyle.OUTLINE, loaded.get(0).getStyleOrDefault());
	}
}
