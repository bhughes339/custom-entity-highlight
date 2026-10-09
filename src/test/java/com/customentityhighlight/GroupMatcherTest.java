package com.customentityhighlight;

import java.util.Arrays;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GroupMatcherTest
{
	private static final String[] BANKER_ACTIONS = {"Talk-to", "Bank", "Collect", null, null};

	private static GroupMatcher matcher(String options, String names, String ids)
	{
		HighlightGroup group = new HighlightGroup();
		group.setMenuOptions(options);
		group.setEntityNames(names);
		group.setEntityIds(ids);
		return GroupMatcher.of(group);
	}

	@Test
	public void matchesMenuOptions()
	{
		assertTrue(matcher("bank", "", "").matches(1, "Banker", BANKER_ACTIONS));
		assertTrue(matcher("coll*", "", "").matches(1, "Banker", BANKER_ACTIONS));
		assertFalse(matcher("ban", "", "").matches(1, "Banker", BANKER_ACTIONS));
	}

	@Test
	public void matchesNames()
	{
		assertTrue(matcher("", "banker", "").matches(1, "Banker", null));
		assertTrue(matcher("", "*chest", "").matches(1, "<col=ffff>Bank chest</col>", null));
		assertFalse(matcher("", "bank", "").matches(1, "Banker", null));
	}

	@Test
	public void wildcardNameIgnoresUnnamedDefinitions()
	{
		assertFalse(matcher("", "*", "").matches(1, "null", null));
	}

	@Test
	public void wildcardsTreatRegexCharactersLiterally()
	{
		assertTrue(matcher("", "rune (p++)*", "").matches(1, "Rune (p++) dagger", null));
		assertFalse(matcher("", "bank.", "").matches(1, "Banks", null));
	}

	@Test
	public void matchesRegexes()
	{
		assertTrue(matcher("/coll(ect)?/", "", "").matches(1, "Banker", BANKER_ACTIONS));
		assertTrue(matcher("", "/(iron|steel) rocks/", "").matches(1, "<col=ffff>Steel rocks</col>", null));
		assertFalse(matcher("", "/(iron|steel) rocks/", "").matches(1, "Coal rocks", null));
		// Regexes must match the whole text, like wildcards
		assertFalse(matcher("", "/bank/", "").matches(1, "Banker", null));
	}

	@Test
	public void regexesCanContainCommas()
	{
		assertEquals(Arrays.asList("/a{2,3}/", "bank", "/x,y/"), GroupMatcher.splitEntries(" /a{2,3}/ , bank,, /x,y/"));
		assertTrue(matcher("", "/ba{1,2}nker/, chest", "").matches(1, "Banker", null));
	}

	@Test
	public void ignoresInvalidRegexes()
	{
		GroupMatcher matcher = matcher("", "/bank(/, banker", "");
		assertTrue(matcher.matches(1, "Banker", null));
		assertFalse(matcher.matches(1, "Bank(", null));
		assertEquals(1, GroupMatcher.invalidEntries("/bank(/, banker").size());
		assertTrue(GroupMatcher.invalidEntries("/bank/, bank(").isEmpty());
	}

	@Test
	public void matchesIds()
	{
		GroupMatcher matcher = matcher("", "", "1613, 10355, not-a-number");
		assertTrue(matcher.matches(10355, "Bank booth", null));
		assertFalse(matcher.matches(10356, "Bank booth", null));
	}

	@Test
	public void matchesIfAnyListMatches()
	{
		GroupMatcher matcher = matcher("deposit", "banker", "42");
		assertTrue(matcher.matches(1, "Banker", null));
		assertTrue(matcher.matches(42, "Rock", null));
		assertFalse(matcher.matches(1, "Rock", BANKER_ACTIONS));
	}

	@Test
	public void sameEntriesGiveEqualMatchers()
	{
		assertTrue(matcher("bank", "banker", "1, 2").equals(matcher(" bank ", "banker", "2,1")));
	}
}
