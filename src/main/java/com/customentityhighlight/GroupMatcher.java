package com.customentityhighlight;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import net.runelite.client.util.Text;

/**
 * The parsed entries of a {@link HighlightGroup}. Equal matchers match exactly the same entities.
 */
@Value
class GroupMatcher
{
	// Unnamed definitions are named "null" in the cache
	private static final String NULL_NAME = "null";

	// Either a regex in slashes, which may contain commas, or anything up to the next comma or newline
	private static final Pattern ENTRY = Pattern.compile("\\s*(-?\\s*/.*?/)\\s*(?:[,\\n]|$)|([^,\\n]*)[,\\n]?");

	// Prefix for entries that stop an entity from matching
	private static final String EXCLUDE_PREFIX = "-";

	List<String> options;
	List<String> names;
	// Whether to skip entities that can't be interacted with
	boolean interactableOnly;

	// Patterns in entry order, compiled from the entries above so equal entries always give equal patterns
	@EqualsAndHashCode.Exclude
	@ToString.Exclude
	List<Pattern> optionPatterns;
	@EqualsAndHashCode.Exclude
	@ToString.Exclude
	List<Pattern> namePatterns;
	@EqualsAndHashCode.Exclude
	@ToString.Exclude
	List<Pattern> excludedOptionPatterns;
	@EqualsAndHashCode.Exclude
	@ToString.Exclude
	List<Pattern> excludedNamePatterns;

	static GroupMatcher of(HighlightGroup group)
	{
		List<String> options = splitEntries(group.getMenuOptions());
		List<String> names = splitEntries(group.getEntityNames());
		return new GroupMatcher(options, names, group.isInteractableOnly(), compileAll(options, false),
			compileAll(names, false), compileAll(options, true), compileAll(names, true));
	}

	/**
	 * Splits a list of option or name entries separated by commas or newlines. Commas inside a regex entry don't split it.
	 */
	static List<String> splitEntries(String text)
	{
		List<String> entries = new ArrayList<>();
		Matcher matcher = ENTRY.matcher(Strings.nullToEmpty(text));
		while (matcher.find())
		{
			String entry = matcher.group(1) != null ? matcher.group(1) : matcher.group(2).trim();
			if (!entry.isEmpty())
			{
				entries.add(entry);
			}
		}
		return entries;
	}

	/**
	 * Returns a message for each regex entry in the text that doesn't compile.
	 */
	static List<String> invalidEntries(String text)
	{
		List<String> errors = new ArrayList<>();
		for (String entry : splitEntries(text))
		{
			try
			{
				compile(entry);
			}
			catch (PatternSyntaxException e)
			{
				errors.add(entry + ": " + e.getDescription());
			}
		}
		return errors;
	}

	/**
	 * Compiles the entries that are, or aren't, exclusions.
	 */
	private static List<Pattern> compileAll(List<String> entries, boolean excluded)
	{
		List<Pattern> patterns = new ArrayList<>();
		for (String entry : entries)
		{
			if (isExclusion(entry) != excluded)
			{
				continue;
			}

			try
			{
				patterns.add(compile(entry));
			}
			catch (PatternSyntaxException e)
			{
				// Ignore invalid regexes; the panel points them out
			}
		}
		return patterns;
	}

	/**
	 * Whether the entry starts with a hyphen, so entities it matches are never highlighted. A lone hyphen is literal.
	 */
	private static boolean isExclusion(String entry)
	{
		return entry.length() > 1 && entry.startsWith(EXCLUDE_PREFIX);
	}

	private static boolean isRegex(String entry)
	{
		return entry.length() > 2 && entry.startsWith("/") && entry.endsWith("/");
	}

	/**
	 * Compiles an entry, without any exclusion prefix, into a case-insensitive pattern that must match the whole text.
	 * Entries in slashes are regexes; otherwise * matches any text and everything else is literal.
	 */
	private static Pattern compile(String entry)
	{
		if (isExclusion(entry))
		{
			entry = entry.substring(EXCLUDE_PREFIX.length()).trim();
		}

		if (isRegex(entry))
		{
			return Pattern.compile(entry.substring(1, entry.length() - 1), Pattern.CASE_INSENSITIVE);
		}

		StringBuilder regex = new StringBuilder();
		String[] parts = entry.split("\\*", -1);
		for (int i = 0; i < parts.length; i++)
		{
			if (i > 0)
			{
				regex.append(".*");
			}
			if (!parts[i].isEmpty())
			{
				regex.append(Pattern.quote(parts[i]));
			}
		}
		return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
	}

	boolean isEmpty()
	{
		return options.isEmpty() && names.isEmpty();
	}

	/**
	 * Whether a definition with this name and menu actions matches any of the entries and none of the exclusions,
	 * ignoring interactability.
	 */
	boolean matches(String name, String[] actions)
	{
		return matchedText(name, actions) != null;
	}

	/**
	 * Whether a definition matches any of the entries and, if the group is limited to interactable entities, is one.
	 * An entity is interactable if the game lets it be clicked and it has an option other than Examine, which
	 * definitions don't list.
	 */
	boolean matches(String name, String[] actions, boolean clickable)
	{
		if (interactableOnly && !(clickable && hasAction(actions)))
		{
			return false;
		}
		return matches(name, actions);
	}

	private static boolean hasAction(String[] actions)
	{
		if (actions != null)
		{
			for (String action : actions)
			{
				if (action != null && !Text.removeTags(action).trim().isEmpty())
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Returns the action matched by the first option entry that matches one of them, or else the name if a name
	 * entry matches it, as the game writes it without tags. Null if nothing matches or any exclusion entry matches.
	 */
	String matchedText(String name, String[] actions)
	{
		List<String> actionTexts = actionTexts(actions);
		List<String> nameTexts = nameTexts(name);
		if (firstMatch(excludedOptionPatterns, actionTexts) != null || firstMatch(excludedNamePatterns, nameTexts) != null)
		{
			return null;
		}

		String action = firstMatch(optionPatterns, actionTexts);
		return action != null ? action : firstMatch(namePatterns, nameTexts);
	}

	/**
	 * Returns the index of the option matched by the first option entry that matches any of them, or -1 if none
	 * match. Null options are skipped, and when one entry matches several options the earliest wins.
	 */
	int firstMatchingOption(String[] options)
	{
		for (Pattern pattern : optionPatterns)
		{
			for (int i = 0; i < options.length; i++)
			{
				if (options[i] != null && pattern.matcher(Text.removeTags(options[i])).matches())
				{
					return i;
				}
			}
		}
		return -1;
	}

	private static List<String> actionTexts(String[] actions)
	{
		if (actions == null)
		{
			return Collections.emptyList();
		}

		List<String> texts = new ArrayList<>(actions.length);
		for (String action : actions)
		{
			if (action != null)
			{
				texts.add(Text.removeTags(action));
			}
		}
		return texts;
	}

	private static List<String> nameTexts(String name)
	{
		if (name == null)
		{
			return Collections.emptyList();
		}

		String text = Text.removeTags(name);
		return NULL_NAME.equals(text) ? Collections.emptyList() : Collections.singletonList(text);
	}

	/**
	 * Returns the text matched by the first pattern that matches any of them, or null if none match.
	 */
	private static String firstMatch(List<Pattern> patterns, List<String> texts)
	{
		for (Pattern pattern : patterns)
		{
			for (String text : texts)
			{
				if (pattern.matcher(text).matches())
				{
					return text;
				}
			}
		}
		return null;
	}
}
