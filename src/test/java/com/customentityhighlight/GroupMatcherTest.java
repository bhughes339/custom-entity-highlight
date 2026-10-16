package com.customentityhighlight;

import java.util.Arrays;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
	public void newlinesSeparateEntries()
	{
		assertEquals(Arrays.asList("bank", "/x,y/", "chest", "banker"),
			GroupMatcher.splitEntries("bank\n/x,y/\r\n\nchest, banker\n"));
	}

	@Test
	public void reportsFirstMatchedEntry()
	{
		// Entries are checked in the order written, options before names
		assertEquals("coll*", matcher("talk, coll*, bank", "banker").matchedEntry("Banker", BANKER_ACTIONS));
		assertEquals("/bank.*/", matcher("pickpocket", "chest, /bank.*/").matchedEntry("Banker", BANKER_ACTIONS));
		assertEquals("bank", matcher("bank", "").matchedEntry("Banker", new String[]{"<col=ff0000>Bank</col>"}));
		assertNull(matcher("steal", "chest").matchedEntry("Banker", BANKER_ACTIONS));
	}

	@Test
	public void findsFirstMatchingOption()
	{
		// Entries are tried in the order written; for each, the earliest matching option wins
		String[] options = {"Talk-to", null, "Bank", "Bank-all", "Collect"};
		assertEquals(4, matcher("coll*, bank*", "").firstMatchingOption(options));
		assertEquals(2, matcher("pickpocket, bank*", "").firstMatchingOption(options));
		assertEquals(-1, matcher("steal", "banker").firstMatchingOption(options));
	}

	@Test
	public void interactableOnlySkipsEntitiesWithoutOptions()
	{
		HighlightGroup group = new HighlightGroup();
		group.setEntityNames("bank booth");
		GroupMatcher any = GroupMatcher.of(group);
		group.setInteractableOnly(true);
		GroupMatcher interactable = GroupMatcher.of(group);

		String[] none = {null, "", "<col=ff0000></col>", null, null};
		assertTrue(any.matches("Bank booth", none, true));
		assertFalse(interactable.matches("Bank booth", none, true));
		assertFalse(interactable.matches("Bank booth", null, true));
		assertTrue(interactable.matches("Bank booth", new String[]{null, "Bank", null}, true));
		// NPCs the game won't let you click don't count, even with options
		assertFalse(interactable.matches("Bank booth", BANKER_ACTIONS, false));
		// Changing the setting changes which entities match, so the plugin rescans
		assertFalse(any.equals(interactable));
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
