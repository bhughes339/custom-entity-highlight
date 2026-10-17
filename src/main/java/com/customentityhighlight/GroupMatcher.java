package com.customentityhighlight;

import com.google.common.base.Strings;
import java.util.ArrayList;
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
	private static final Pattern ENTRY = Pattern.compile("\\s*(/.*?/)\\s*(?:[,\\n]|$)|([^,\\n]*)[,\\n]?");

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

	static GroupMatcher of(HighlightGroup group)
	{
		List<String> options = splitEntries(group.getMenuOptions());
		List<String> names = splitEntries(group.getEntityNames());
		return new GroupMatcher(options, names, group.isInteractableOnly(), compileAll(options), compileAll(names));
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

	private static List<Pattern> compileAll(List<String> entries)
	{
		List<Pattern> patterns = new ArrayList<>();
		for (String entry : entries)
		{
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

	private static boolean isRegex(String entry)
	{
		return entry.length() > 2 && entry.startsWith("/") && entry.endsWith("/");
	}

	/**
	 * Compiles an entry into a case-insensitive pattern that must match the whole text.
	 * Entries in slashes are regexes; otherwise * matches any text and everything else is literal.
	 */
	private static Pattern compile(String entry)
	{
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
	 * Whether a definition with this name and menu actions matches any of the entries, ignoring interactability.
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
	 * entry matches it, as the game writes it without tags. Null if nothing matches.
	 */
	String matchedText(String name, String[] actions)
	{
		String action = matchingOption(actions);
		return action != null ? action : matchingName(name);
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

	private String matchingOption(String[] actions)
	{
		if (optionPatterns.isEmpty() || actions == null)
		{
			return null;
		}

		List<String> normalized = new ArrayList<>(actions.length);
		for (String action : actions)
		{
			if (action != null)
			{
				normalized.add(Text.removeTags(action));
			}
		}

		for (Pattern pattern : optionPatterns)
		{
			for (String action : normalized)
			{
				if (pattern.matcher(action).matches())
				{
					return action;
				}
			}
		}
		return null;
	}

	private String matchingName(String name)
	{
		if (namePatterns.isEmpty() || name == null)
		{
			return null;
		}

		String normalized = Text.removeTags(name);
		if (NULL_NAME.equals(normalized))
		{
			return null;
		}

		for (Pattern pattern : namePatterns)
		{
			if (pattern.matcher(normalized).matches())
			{
				return normalized;
			}
		}
		return null;
	}
}
