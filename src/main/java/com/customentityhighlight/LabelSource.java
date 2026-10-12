package com.customentityhighlight;

import com.google.gson.annotations.SerializedName;
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
	// Replaced the old "Top option" source, so groups saved with it load as Match
	@SerializedName(value = "MATCH", alternate = "TOP_OPTION")
	MATCH("Match"),
	CUSTOM("Custom");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
