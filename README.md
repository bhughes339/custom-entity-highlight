# Custom Entity Highlight

Highlights NPCs and objects by their right-click menu options or their name. This plugin combines the functionalities of the various excellent entity highlighter plugins in the plugin hub, with an emphasis on customization and scale.

## Highlight groups

Open the **Custom Entity Highlight** side panel in RuneLite. Click **Add group** to add a new highlight group and define its entries and appearance options, or **Import group** to add one someone has shared with you (see [Sharing groups](#sharing-groups)).

Settings marked *Advanced* are in the group's **Advanced** section, which is closed by default. Click the **+** next to it to show them.

| Setting | Description |
| --- | --- |
| Enabled / name | The checkbox turns the group on or off. The name is just a label for you; change it with the pencil button. |
| - / + | Hide or show the group's settings, to keep the list short. |
| Options | Right-click menu options to look for, separated by commas or new lines, e.g. `bank, collect`. Case-insensitive, with `*` as a wildcard or `/.../` for a regex (see below). |
| Names | NPC or object names, separated by commas or new lines, e.g. `banker, *chest`. Case-insensitive, with `*` as a wildcard or `/.../` for a regex. |
| Highlight NPCs / Objects | Turn highlighting on or off for each type of target. |
| Style | Outline (traces the model), Hull (border around the model), Area (model filled in, no border), Clickbox (exact clickable area), Tile (tiles it stands on) or None (no highlight, only the label if Show labels is on). |
| Color / Fill | Border and fill colors, including transparency. The border color is also used for labels. |
| Width / Feather | *Advanced.* Line thickness, and edge softness for the outline style. |
| Swap left-click | *Advanced.* Makes the option matched by the group's first matching Options entry the left-click option for matched NPCs and objects. Names entries aren't used. Options the game has moved down the menu, such as Attack under your NPC attack option setting, are never moved to left-click. |
| Show labels | *Advanced.* Draws text above each highlight. |
| Label / Text | *Advanced.* What Show labels draws: the NPC or object **Name**, the **Match** (the first Options entry that matched, as you wrote it, or else the first Names entry), or **Custom** text typed in the Text field. Falls back to the name if the custom text is blank. |
| ▲ / ▼ | Move the group up or down the list. |
| Export / Delete | Copy the group to the clipboard to share it, or remove it. |

Options and Names are saved and applied when you leave the field; Enter starts a new line. An NPC or object matches a group if it matches **any** entry in either list.

You can create as many groups as you want. An NPC or object that matches several groups is drawn by each of them, so you can combine styles (for example, an outline from one group and a tile from another). Groups lower in the list draw on top. If more than one matching group shows labels, the label uses the color and label settings of the highest one. Likewise, if more than one matching group swaps the left-click option, the highest one that has a matching option wins.

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

A regex can contain commas, e.g. `/a{2,3}/`; it ends at a `/` followed by a comma, a new line or the end of the field, so it must fit on one line. A regex that isn't valid is ignored, and its field turns red until it's fixed. Hover over the field to see the error.

NPCs and objects that change form based on game state (for example, a bank chest that only offers "Bank" after a quest) are tracked, and highlighted only while their current form has a matching option.

### Example

Problem: You want to highlight all objects and NPCs that can be used to bank. However, setting the `Options` filter to "bank" isn't enough, since there are several objects (e.g. bank chest) that have the "Use" menu option instead of "Bank". Conversely, setting the "Names" filter to "bank*" is too broad, as it will highlight unusable bank booths and other erroneous objects.

Solution: Set the `Options` filter to "bank,deposit" and add entries to the `Names` filter as you see them, such as "bank chest" and "bank crab".

## Sharing groups

Click a group's **Export** button to copy it to the clipboard as a line of text, which you can paste anywhere, such as a chat message. To add a shared group, copy its text and click **Import group** at the top of the panel. The group is added to the end of your list with all of its settings. Line breaks added when the text was pasted are ignored.

## Development

Run a development client with the plugin loaded:

```
./gradlew run
```

---

**Disclaimer:** This project was developed with the assistance of one or more LLMs.
