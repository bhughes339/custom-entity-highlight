# Custom Entity Highlight

Highlights NPCs and objects by their right-click menu options or their name. This plugin combines the functionalities of the various excellent entity highlighter plugins in the plugin hub, with an emphasis on customization and scale.

See the [wiki](https://github.com/bhughes339/custom-entity-highlight/wiki/Usage) for how to set up highlight groups, match with wildcards and regular expressions, and share groups.

## Example

Problem: You want to highlight all objects and NPCs that can be used to bank. However, setting the `Options` filter to "bank" isn't enough, since there are several objects (e.g. bank chest) that have the "Use" menu option instead of "Bank". Conversely, setting the "Names" filter to "bank*" is too broad, as it will highlight unusable bank booths and other erroneous objects.

Solution: Set the `Options` filter to "bank,deposit" and add entries to the `Names` filter as you see them, such as "bank chest" and "bank crab".

## Development

Run a development client with the plugin loaded:

```
./gradlew run
```

---

**Disclaimer:** This project was developed with the assistance of one or more LLMs.
