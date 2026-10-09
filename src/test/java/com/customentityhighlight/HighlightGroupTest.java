package com.customentityhighlight;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.awt.Color;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
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
		group.setMenuOptions("pick*, steal-from");
		group.setStyle(HighlightStyle.CLICKBOX);
		group.setHighlightColor(new Color(255, 128, 0, 200).getRGB());
		group.setShowNames(true);

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
		assertTrue(group.isHighlightNpcs());
		assertEquals(HighlightStyle.OUTLINE, group.getStyleOrDefault());
		assertEquals(2, group.getBorderWidth());
	}

	@Test
	public void unknownStyleFallsBackToOutline()
	{
		List<HighlightGroup> loaded = gson.fromJson("[{\"style\":\"NOT_A_STYLE\"}]", GROUP_LIST_TYPE);

		assertEquals(HighlightStyle.OUTLINE, loaded.get(0).getStyleOrDefault());
	}
}
