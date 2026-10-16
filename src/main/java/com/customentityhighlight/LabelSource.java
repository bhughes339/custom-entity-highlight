package com.customentityhighlight;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * What text a group draws above each highlighted entity when it shows names.
 */
@Getter
@RequiredArgsConstructor
public enum LabelSource
{
	NAME("Name"),
	MATCH("Match"),
	CUSTOM("Custom");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
