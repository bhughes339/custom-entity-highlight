package com.customentityhighlight;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Custom Entity Highlight",
	description = "Highlights NPCs and objects by right-click menu option or name",
	tags = {"highlight", "menu", "option", "name", "npc", "object", "bank"}
)
public class CustomEntityHighlightPlugin extends Plugin
{
	// Menu entries for an NPC's or object's own options, which left-click swaps can move
	private static final Set<MenuAction> NPC_OPTIONS = EnumSet.of(MenuAction.NPC_FIRST_OPTION,
		MenuAction.NPC_SECOND_OPTION, MenuAction.NPC_THIRD_OPTION, MenuAction.NPC_FOURTH_OPTION,
		MenuAction.NPC_FIFTH_OPTION);
	private static final Set<MenuAction> OBJECT_OPTIONS = EnumSet.of(MenuAction.GAME_OBJECT_FIRST_OPTION,
		MenuAction.GAME_OBJECT_SECOND_OPTION, MenuAction.GAME_OBJECT_THIRD_OPTION,
		MenuAction.GAME_OBJECT_FOURTH_OPTION, MenuAction.GAME_OBJECT_FIFTH_OPTION);

	private static final Type GROUP_LIST_TYPE = new TypeToken<List<HighlightGroup>>()
	{
	}.getType();

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	@Inject
	private CustomEntityHighlightOverlay overlay;

	@Inject
	private CustomEntityHighlightConfig config;

	private CustomEntityHighlightPanel panel;
	private NavigationButton navigationButton;

	/**
	 * The groups being drawn. Only touched on the client thread; the panel edits its own copies.
	 */
	@Getter(AccessLevel.PACKAGE)
	private List<HighlightGroup> groups = Collections.emptyList();

	// Parsed entries of each group, by group index
	private List<GroupMatcher> matchers = Collections.emptyList();

	/**
	 * NPCs that have, or can transform into something that has, an option matching any group.
	 */
	@Getter(AccessLevel.PACKAGE)
	private final Set<NPC> npcs = new HashSet<>();

	/**
	 * Objects that have, or can transform into something that has, an option matching any group.
	 */
	@Getter(AccessLevel.PACKAGE)
	private final Set<TileObject> objects = new HashSet<>();

	// Definition id -> indexes of the groups whose entries match one of that definition's options
	private final Map<Integer, BitSet> npcMatches = new HashMap<>();
	private final Map<Integer, BitSet> objectMatches = new HashMap<>();

	// Group index and definition id -> the entry that group matched on, for Match labels
	private final Map<Long, String> npcMatchedEntries = new HashMap<>();
	private final Map<Long, String> objectMatchedEntries = new HashMap<>();

	@Provides
	CustomEntityHighlightConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CustomEntityHighlightConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = injector.getInstance(CustomEntityHighlightPanel.class);
		navigationButton = NavigationButton.builder()
			.tooltip("Custom Entity Highlight")
			.icon(ImageUtil.loadImageResource(getClass(), "panel_icon.png"))
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);

		overlayManager.add(overlay);
		clientThread.invoke(this::loadGroups);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navigationButton);
		navigationButton = null;
		panel = null;

		clientThread.invoke(() ->
		{
			npcs.clear();
			objects.clear();
			npcMatches.clear();
			objectMatches.clear();
			npcMatchedEntries.clear();
			objectMatchedEntries.clear();
			groups = Collections.emptyList();
			matchers = Collections.emptyList();
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (CustomEntityHighlightConfig.GROUP.equals(event.getGroup())
			&& CustomEntityHighlightConfig.GROUPS_KEY.equals(event.getKey()))
		{
			clientThread.invoke(this::loadGroups);
		}
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		clientThread.invoke(this::loadGroups);
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.rebuild();
			}
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOADING:
				// Objects are respawned after the scene loads
				objects.clear();
				break;
			case LOGIN_SCREEN:
			case HOPPING:
				objects.clear();
				npcs.clear();
				break;
		}
	}

	/**
	 * Moves the option chosen by a left-click swapping group to the top of the menu, for the entity the
	 * left-click option belongs to.
	 */
	@Subscribe
	public void onPostMenuSort(PostMenuSort event)
	{
		if (client.isMenuOpen() || matchers.isEmpty())
		{
			return;
		}

		Menu menu = client.getMenu();
		MenuEntry[] entries = menu.getMenuEntries();
		if (entries.length < 2)
		{
			return;
		}

		MenuEntry top = entries[entries.length - 1];
		boolean isNpc = NPC_OPTIONS.contains(top.getType());
		if (!isNpc && !OBJECT_OPTIONS.contains(top.getType()))
		{
			return;
		}

		BitSet matches;
		if (isNpc)
		{
			NPCComposition comp = top.getNpc() != null ? top.getNpc().getTransformedComposition() : null;
			matches = comp != null ? getMatchingGroups(comp) : null;
		}
		else
		{
			ObjectComposition comp = getCurrentComposition(top.getIdentifier());
			matches = comp != null ? getMatchingGroups(comp) : null;
		}
		if (matches == null)
		{
			return;
		}

		// The same entity's options, top first; others are left null so they can't be chosen.
		// Deprioritized options, such as Attack under the game's attack option setting, are never promoted.
		String[] options = new String[entries.length];
		for (int i = 0; i < entries.length; i++)
		{
			MenuEntry entry = entries[entries.length - 1 - i];
			if (!entry.isDeprioritized() && isSameEntity(entry, top, isNpc))
			{
				options[i] = entry.getOption();
			}
		}

		for (int g = matches.nextSetBit(0); g >= 0; g = matches.nextSetBit(g + 1))
		{
			HighlightGroup group = groups.get(g);
			if (!group.isEnabled() || !group.isSwapLeftClick()
				|| !(isNpc ? group.isHighlightNpcs() : group.isHighlightObjects()))
			{
				continue;
			}

			int option = matchers.get(g).firstMatchingOption(options);
			if (option >= 0)
			{
				if (option > 0)
				{
					int index = entries.length - 1 - option;
					MenuEntry chosen = entries[index];
					System.arraycopy(entries, index + 1, entries, index, entries.length - 1 - index);
					entries[entries.length - 1] = chosen;
					menu.setMenuEntries(entries);
				}
				return;
			}
		}
	}

	private static boolean isSameEntity(MenuEntry entry, MenuEntry top, boolean isNpc)
	{
		if (isNpc)
		{
			return NPC_OPTIONS.contains(entry.getType()) && entry.getNpc() == top.getNpc();
		}

		return OBJECT_OPTIONS.contains(entry.getType())
			&& entry.getIdentifier() == top.getIdentifier()
			&& entry.getParam0() == top.getParam0()
			&& entry.getParam1() == top.getParam1()
			&& entry.getWorldViewId() == top.getWorldViewId();
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		addNpc(event.getNpc());
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event)
	{
		NPC npc = event.getNpc();
		npcs.remove(npc);
		addNpc(npc);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		npcs.remove(event.getNpc());
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		addObject(event.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		objects.remove(event.getGameObject());
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		addObject(event.getWallObject());
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		objects.remove(event.getWallObject());
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
	{
		addObject(event.getDecorativeObject());
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
	{
		objects.remove(event.getDecorativeObject());
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		addObject(event.getGroundObject());
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		objects.remove(event.getGroundObject());
	}

	/**
	 * Reads the saved groups. Returns new objects on every call, so callers can edit them freely.
	 */
	List<HighlightGroup> readSavedGroups()
	{
		String json = config.groups();
		if (Strings.isNullOrEmpty(json))
		{
			return new ArrayList<>();
		}

		try
		{
			List<HighlightGroup> saved = gson.fromJson(json, GROUP_LIST_TYPE);
			if (saved != null)
			{
				saved.removeIf(group -> group == null);
				return saved;
			}
		}
		catch (JsonParseException e)
		{
			log.warn("Unable to parse saved highlight groups", e);
		}
		return new ArrayList<>();
	}

	void saveGroups(List<HighlightGroup> groups)
	{
		configManager.setConfiguration(CustomEntityHighlightConfig.GROUP, CustomEntityHighlightConfig.GROUPS_KEY,
			gson.toJson(groups, GROUP_LIST_TYPE));
	}

	/**
	 * Returns the indexes of the groups that match the NPC's current form, or null if none do.
	 */
	BitSet getMatchingGroups(NPCComposition current)
	{
		return nullIfEmpty(npcMatches(current.getId()));
	}

	/**
	 * Returns the indexes of the groups that match the object's current form, or null if none do.
	 */
	BitSet getMatchingGroups(ObjectComposition current)
	{
		return nullIfEmpty(objectMatches(current.getId()));
	}

	/**
	 * Returns the entry, as written, that the group matched the NPC's current form on, or null if it doesn't match.
	 */
	String getMatchedEntry(int group, NPCComposition current)
	{
		return npcMatchedEntries.computeIfAbsent(matchKey(group, current.getId()),
			k -> matchers.get(group).matchedEntry(current.getName(), current.getActions()));
	}

	/**
	 * Returns the entry, as written, that the group matched the object's current form on, or null if it doesn't match.
	 */
	String getMatchedEntry(int group, ObjectComposition current)
	{
		return objectMatchedEntries.computeIfAbsent(matchKey(group, current.getId()),
			k -> matchers.get(group).matchedEntry(current.getName(), current.getActions()));
	}

	private static long matchKey(int group, int id)
	{
		return ((long) group << 32) | (id & 0xFFFFFFFFL);
	}

	private static BitSet nullIfEmpty(BitSet matches)
	{
		return matches.isEmpty() ? null : matches;
	}

	/**
	 * Returns the object's current form, following any varbit-driven transformation.
	 */
	ObjectComposition getCurrentComposition(TileObject object)
	{
		return getCurrentComposition(object.getId());
	}

	private ObjectComposition getCurrentComposition(int id)
	{
		ObjectComposition comp = client.getObjectDefinition(id);
		if (comp != null && comp.getImpostorIds() != null)
		{
			comp = comp.getImpostor();
		}
		return comp;
	}

	private void loadGroups()
	{
		List<HighlightGroup> loaded = readSavedGroups();
		List<GroupMatcher> loadedMatchers = new ArrayList<>(loaded.size());
		for (HighlightGroup group : loaded)
		{
			loadedMatchers.add(GroupMatcher.of(group));
		}

		groups = loaded;

		// Appearance changes don't affect which entities match, so only rescan when the entries change
		if (!loadedMatchers.equals(matchers))
		{
			matchers = loadedMatchers;
			npcMatches.clear();
			objectMatches.clear();
			npcMatchedEntries.clear();
			objectMatchedEntries.clear();
			npcs.clear();
			objects.clear();

			if (client.getGameState() == GameState.LOGGED_IN)
			{
				rescan(client.getTopLevelWorldView());
			}
		}
	}

	private void rescan(WorldView worldView)
	{
		for (NPC npc : worldView.npcs())
		{
			addNpc(npc);
		}

		Scene scene = worldView.getScene();
		for (Tile[][] plane : scene.getTiles())
		{
			for (Tile[] row : plane)
			{
				for (Tile tile : row)
				{
					if (tile == null)
					{
						continue;
					}

					GameObject[] gameObjects = tile.getGameObjects();
					if (gameObjects != null)
					{
						for (GameObject gameObject : gameObjects)
						{
							// Players and NPCs also occupy game object slots
							if (gameObject != null && !(gameObject.getRenderable() instanceof Actor))
							{
								addObject(gameObject);
							}
						}
					}

					addObject(tile.getWallObject());
					addObject(tile.getDecorativeObject());
					addObject(tile.getGroundObject());
				}
			}
		}

		for (WorldView subWorldView : worldView.worldViews())
		{
			rescan(subWorldView);
		}
	}

	private void addNpc(NPC npc)
	{
		if (matchers.isEmpty() || npc == null)
		{
			return;
		}

		NPCComposition comp = npc.getComposition();
		if (comp == null)
		{
			return;
		}

		if (!npcMatches(comp.getId()).isEmpty() || anyIdMatches(comp.getConfigs(), this::npcMatches))
		{
			npcs.add(npc);
		}
	}

	private void addObject(TileObject object)
	{
		if (matchers.isEmpty() || object == null)
		{
			return;
		}

		ObjectComposition comp = client.getObjectDefinition(object.getId());
		if (comp == null)
		{
			return;
		}

		if (!objectMatches(comp.getId()).isEmpty() || anyIdMatches(comp.getImpostorIds(), this::objectMatches))
		{
			objects.add(object);
		}
	}

	private BitSet npcMatches(int id)
	{
		return npcMatches.computeIfAbsent(id, i ->
		{
			NPCComposition comp = client.getNpcDefinition(i);
			return comp != null ? matchingGroups(comp.getName(), comp.getActions()) : new BitSet();
		});
	}

	private BitSet objectMatches(int id)
	{
		return objectMatches.computeIfAbsent(id, i ->
		{
			ObjectComposition comp = client.getObjectDefinition(i);
			return comp != null ? matchingGroups(comp.getName(), comp.getActions()) : new BitSet();
		});
	}

	private static boolean anyIdMatches(int[] ids, IntFunction<BitSet> matches)
	{
		if (ids == null)
		{
			return false;
		}

		for (int id : ids)
		{
			if (id != -1 && !matches.apply(id).isEmpty())
			{
				return true;
			}
		}
		return false;
	}

	private BitSet matchingGroups(String name, String[] actions)
	{
		BitSet matches = new BitSet();
		for (int i = 0; i < matchers.size(); i++)
		{
			if (matchers.get(i).matches(name, actions))
			{
				matches.set(i);
			}
		}
		return matches;
	}
}
