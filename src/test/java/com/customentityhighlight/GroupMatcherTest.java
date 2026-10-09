package com.customentityhighlight;

import java.util.Arrays;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GroupMatcherTest
{
	private static final String[] BANKER_ACTIONS = {"Talk-to", "Bank", "Collect", null, null};

	private static GroupMatcher matcher(String options, String names)
	{
		HighlightGroup group = new HighlightGroup();
		group.setMenuOptions(options);
		group.setEntityNames(names);
		return GroupMatcher.of(group);
	}

	@Test
	public void matchesMenuOptions()
	{
		assertTrue(matcher("bank", "").matches("Banker", BANKER_ACTIONS));
		assertTrue(matcher("coll*", "").matches("Banker", BANKER_ACTIONS));
		assertFalse(matcher("ban", "").matches("Banker", BANKER_ACTIONS));
	}

	@Test
	public void matchesNames()
	{
		assertTrue(matcher("", "banker").matches("Banker", null));
		assertTrue(matcher("", "*chest").matches("<col=ffff>Bank chest</col>", null));
		assertFalse(matcher("", "bank").matches("Banker", null));
	}

	@Test
	public void wildcardNameIgnoresUnnamedDefinitions()
	{
		assertFalse(matcher("", "*").matches("null", null));
	}

	@Test
	public void wildcardsTreatRegexCharactersLiterally()
	{
		assertTrue(matcher("", "rune (p++)*").matches("Rune (p++) dagger", null));
		assertFalse(matcher("", "bank.").matches("Banks", null));
	}

	@Test
	public void matchesRegexes()
	{
		assertTrue(matcher("/coll(ect)?/", "").matches("Banker", BANKER_ACTIONS));
		assertTrue(matcher("", "/(iron|steel) rocks/").matches("<col=ffff>Steel rocks</col>", null));
		assertFalse(matcher("", "/(iron|steel) rocks/").matches("Coal rocks", null));
		// Regexes must match the whole text, like wildcards
		assertFalse(matcher("", "/bank/").matches("Banker", null));
	}

	@Test
	public void regexesCanContainCommas()
	{
		assertEquals(Arrays.asList("/a{2,3}/", "bank", "/x,y/"), GroupMatcher.splitEntries(" /a{2,3}/ , bank,, /x,y/"));
		assertTrue(matcher("", "/ba{1,2}nker/, chest").matches("Banker", null));
	}

	@Test
	public void ignoresInvalidRegexes()
	{
		GroupMatcher matcher = matcher("", "/bank(/, banker");
		assertTrue(matcher.matches("Banker", null));
		assertFalse(matcher.matches("Bank(", null));
		assertEquals(1, GroupMatcher.invalidEntries("/bank(/, banker").size());
		assertTrue(GroupMatcher.invalidEntries("/bank/, bank(").isEmpty());
	}

	@Test
	public void matchesIfAnyListMatches()
	{
		GroupMatcher matcher = matcher("collect", "banker");
		assertTrue(matcher.matches("Banker", null));
		assertTrue(matcher.matches("Rock", BANKER_ACTIONS));
		assertFalse(matcher.matches("Rock", new String[]{"Mine"}));
	}

	@Test
	public void sameEntriesGiveEqualMatchers()
	{
		assertTrue(matcher("bank, collect", "banker").equals(matcher(" bank ,collect", "banker")));
	}
}
