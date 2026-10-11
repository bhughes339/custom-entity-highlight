# Custom Entity Highlight

Highlights NPCs and objects by their right-click menu options or their name. This plugin combines the functionalities of the various excellent entity highlighter plugins in the plugin hub, with an emphasis on customization and scale.

## Highlight groups

Open the **Custom Entity Highlight** side panel in RuneLite. Click **Add group** to add a new highlight group and define its entries and appearance options.

| Setting | Description |
| --- | --- |
| Enabled / name | The checkbox turns the group on or off. The name is just a label for you. |
| Options | Comma-delimited list of right-click menu options to look for, e.g. `bank, collect`. Case-insensitive, with `*` as a wildcard or `/.../` for a regex (see below). |
| Names | Comma-delimited list of NPC or object names, e.g. `banker, *chest`. Case-insensitive, with `*` as a wildcard or `/.../` for a regex. |
| Highlight NPCs / Objects | Turn highlighting on or off for each type of target. |
| Style | Outline (traces the model), Hull (border around the model), Area (model filled in, no border), Clickbox (exact clickable area) or Tile (tiles it stands on). |
| Color / Fill | Border and fill colors, including transparency. |
| Width / Feather | Line thickness, and edge softness for the outline style. |
| Show labels | Draws text above each highlight. |
| Label / Text | What Show labels draws: the NPC or object **Name**, its **Top option** (the first option in its right-click menu), or **Custom** text typed in the Text field. Falls back to the name if there is no option or the custom text is blank. |
| Up / Down / Delete | Reorder or remove the group. |

Options and Names are saved and applied when you press Enter or leave the field. An NPC or object matches a group if it matches **any** entry in either list.

You can create as many groups as you want. An NPC or object that matches several groups is drawn by each of them, so you can combine styles (for example, an outline from one group and a tile from another). Groups lower in the list draw on top. If more than one matching group shows names, the name uses the color and label of the highest one.

### Wildcards

An option or name entry matches the whole text unless it contains `*`, which matches any text:

| Entry | Matches |
| --- | --- |
| `bank` | `Bank` only |
| `bank*` | `Bank`, `Bank-all` |
| `*bank` | `Bank`, `Use-bank` |
| `*bank*` | any option containing `bank` |
| `pick*` | `Pick`, `Pick-up`, `Pickpocket` |

### Regular expressions

For anything wildcards can't express, wrap the entry in forward slashes to use a [Java regular expression](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/regex/Pattern.html). Like other entries, a regex is case-insensitive and must match the whole text:

| Entry | Matches |
| --- | --- |
| `/bank(-all)?/` | `Bank`, `Bank-all` |
| `/(iron\|gold) rocks/` | `Iron rocks`, `Gold rocks` |
| `/.*bank.*/` | any option containing `bank` |

A regex can contain commas, e.g. `/a{2,3}/`; it ends at a `/` followed by a comma or the end of the field. A regex that isn't valid is ignored, and its field turns red until it's fixed. Hover over the field to see the error.

NPCs and objects that change form based on game state (for example, a bank chest that only offers "Bank" after a quest) are tracked, and highlighted only while their current form has a matching option.

### Example

Problem: You want to highlight all objects and NPCs that can be used to bank. However, setting the `Options` filter to "bank" isn't enough, since there are several objects (e.g. bank chest) that have the "Use" menu option instead of "Bank". Conversely, setting the "Names" filter to "bank*" is too broad, as it will highlight unusable bank booths and other erroneous objects.

Solution: Set the `Options` filter to "bank,deposit" and add entries to the `Names` filter as you see them, such as "bank chest" and "bank crab".

## Development

Run a development client with the plugin loaded:

```
./gradlew run
```

---

**Disclaimer:** This project was developed with the assistance of one or more LLMs.
