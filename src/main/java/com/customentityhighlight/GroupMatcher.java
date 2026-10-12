package com.customentityhighlight;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

	// Entry -> pattern, in entry order, compiled from the entries above so equal entries always give equal patterns
	@EqualsAndHashCode.Exclude
	@ToString.Exclude
	Map<String, Pattern> optionPatterns;
	@EqualsAndHashCode.Exclude
	@ToString.Exclude
	Map<String, Pattern> namePatterns;

	static GroupMatcher of(HighlightGroup group)
	{
		List<String> options = splitEntries(group.getMenuOptions());
		List<String> names = splitEntries(group.getEntityNames());
		return new GroupMatcher(options, names, compileAll(options), compileAll(names));
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

	private static Map<String, Pattern> compileAll(List<String> entries)
	{
		Map<String, Pattern> patterns = new LinkedHashMap<>();
		for (String entry : entries)
		{
			try
			{
				patterns.put(entry, compile(entry));
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
	 * Whether a definition with this name and menu actions matches any of the entries.
	 */
	boolean matches(String name, String[] actions)
	{
		return matchedEntry(name, actions) != null;
	}

	/**
	 * Returns the first option entry that matches one of the actions, or else the first name entry that matches
	 * the name, as the user wrote it. Null if nothing matches.
	 */
	String matchedEntry(String name, String[] actions)
	{
		String entry = matchingOption(actions);
		return entry != null ? entry : matchingName(name);
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

		for (Map.Entry<String, Pattern> option : optionPatterns.entrySet())
		{
			for (String action : normalized)
			{
				if (option.getValue().matcher(action).matches())
				{
					return option.getKey();
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

		for (Map.Entry<String, Pattern> entry : namePatterns.entrySet())
		{
			if (entry.getValue().matcher(normalized).matches())
			{
				return entry.getKey();
			}
		}
		return null;
	}
}
