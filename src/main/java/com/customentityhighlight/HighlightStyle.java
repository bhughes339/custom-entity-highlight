package com.customentityhighlight;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HighlightStyle
{
	OUTLINE("Outline"),
	HULL("Hull"),
	AREA("Area"),
	CLICKBOX("Clickbox"),
	TILE("Tile");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
