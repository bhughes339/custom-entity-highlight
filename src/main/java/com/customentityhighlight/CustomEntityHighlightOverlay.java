package com.customentityhighlight;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Area;
import java.util.BitSet;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import net.runelite.client.util.Text;

class CustomEntityHighlightOverlay extends Overlay
{
	// Used by the area style when the fill color is fully transparent
	private static final int AREA_FALLBACK_ALPHA = 50;

	private final Client client;
	private final CustomEntityHighlightPlugin plugin;
	private final ModelOutlineRenderer modelOutlineRenderer;

	@Inject
	private CustomEntityHighlightOverlay(Client client, CustomEntityHighlightPlugin plugin,
		ModelOutlineRenderer modelOutlineRenderer)
	{
		this.client = client;
		this.plugin = plugin;
		this.modelOutlineRenderer = modelOutlineRenderer;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		List<HighlightGroup> groups = plugin.getGroups();
		if (groups.isEmpty())
		{
			return null;
		}

		GroupStyle[] styles = new GroupStyle[groups.size()];
		for (int i = 0; i < styles.length; i++)
		{
			styles[i] = new GroupStyle(groups.get(i));
		}

		for (NPC npc : plugin.getNpcs())
		{
			NPCComposition comp = npc.getTransformedComposition();
			BitSet matches = comp != null ? plugin.getMatchingGroups(comp) : null;
			if (matches == null)
			{
				continue;
			}

			GroupStyle nameStyle = null;
			for (int i = matches.nextSetBit(0); i >= 0; i = matches.nextSetBit(i + 1))
			{
				GroupStyle style = styles[i];
				if (!style.group.isEnabled() || !style.group.isHighlightNpcs())
				{
					continue;
				}

				renderNpc(graphics, npc, style);
				if (nameStyle == null && style.group.isShowNames())
				{
					nameStyle = style;
				}
			}

			if (nameStyle != null)
			{
				String name = Text.removeTags(comp.getName());
				renderName(graphics, npc.getCanvasTextLocation(graphics, name, npc.getLogicalHeight() + 40), name, nameStyle.color);
			}
		}

		for (TileObject object : plugin.getObjects())
		{
			if (object.getPlane() != object.getWorldView().getPlane())
			{
				continue;
			}

			ObjectComposition comp = plugin.getCurrentComposition(object);
			BitSet matches = comp != null ? plugin.getMatchingGroups(comp) : null;
			if (matches == null)
			{
				continue;
			}

			GroupStyle nameStyle = null;
			for (int i = matches.nextSetBit(0); i >= 0; i = matches.nextSetBit(i + 1))
			{
				GroupStyle style = styles[i];
				if (!style.group.isEnabled() || !style.group.isHighlightObjects())
				{
					continue;
				}

				renderObject(graphics, object, style);
				if (nameStyle == null && style.group.isShowNames())
				{
					nameStyle = style;
				}
			}

			if (nameStyle != null)
			{
				String name = Text.removeTags(comp.getName());
				renderName(graphics, object.getCanvasTextLocation(graphics, name, 0), name, nameStyle.color);
			}
		}

		return null;
	}

	private void renderNpc(Graphics2D graphics, NPC npc, GroupStyle style)
	{
		switch (style.group.getStyleOrDefault())
		{
			case OUTLINE:
				modelOutlineRenderer.drawOutline(npc, style.width, style.color, style.feather);
				break;
			case HULL:
				renderShape(graphics, npc.getConvexHull(), style);
				break;
			case AREA:
				renderArea(graphics, npc.getConvexHull(), style.areaFill);
				break;
			case CLICKBOX:
				renderShape(graphics, getClickbox(npc), style);
				break;
			case TILE:
				renderShape(graphics, npc.getCanvasTilePoly(), style);
				break;
		}
	}

	private void renderObject(Graphics2D graphics, TileObject object, GroupStyle style)
	{
		switch (style.group.getStyleOrDefault())
		{
			case OUTLINE:
				modelOutlineRenderer.drawOutline(object, style.width, style.color, style.feather);
				break;
			case HULL:
				renderShape(graphics, getConvexHull(object), style);
				break;
			case AREA:
				renderArea(graphics, getConvexHull(object), style.areaFill);
				break;
			case CLICKBOX:
				renderShape(graphics, object.getClickbox(), style);
				break;
			case TILE:
				renderShape(graphics, object.getCanvasTilePoly(), style);
				break;
		}
	}

	private Shape getClickbox(NPC npc)
	{
		LocalPoint lp = npc.getLocalLocation();
		WorldView worldView = npc.getWorldView();
		Model model = npc.getModel();
		if (lp == null || worldView == null || model == null)
		{
			return null;
		}

		return Perspective.getClickbox(client, worldView, model, npc.getCurrentOrientation(), lp.getX(), lp.getY(),
			Perspective.getTileHeight(client, lp, worldView.getPlane()));
	}

	private static Shape getConvexHull(TileObject object)
	{
		if (object instanceof GameObject)
		{
			return ((GameObject) object).getConvexHull();
		}
		if (object instanceof GroundObject)
		{
			return ((GroundObject) object).getConvexHull();
		}
		// Walls and wall decorations can have two models, e.g. a diagonal wall's two halves
		if (object instanceof WallObject)
		{
			WallObject wall = (WallObject) object;
			return union(wall.getConvexHull(), wall.getConvexHull2());
		}
		if (object instanceof DecorativeObject)
		{
			DecorativeObject decoration = (DecorativeObject) object;
			return union(decoration.getConvexHull(), decoration.getConvexHull2());
		}
		return null;
	}

	private static Shape union(Shape a, Shape b)
	{
		if (a == null)
		{
			return b;
		}
		if (b == null)
		{
			return a;
		}

		Area area = new Area(a);
		area.add(new Area(b));
		return area;
	}

	private static void renderShape(Graphics2D graphics, Shape shape, GroupStyle style)
	{
		if (shape != null)
		{
			OverlayUtil.renderPolygon(graphics, shape, style.color, style.fill, style.stroke);
		}
	}

	private static void renderArea(Graphics2D graphics, Shape shape, Color fill)
	{
		if (shape != null)
		{
			graphics.setColor(fill);
			graphics.fill(shape);
		}
	}

	private static void renderName(Graphics2D graphics, Point location, String name, Color color)
	{
		if (location != null)
		{
			OverlayUtil.renderTextLocation(graphics, location, name, color);
		}
	}

	/**
	 * A group's drawing values, worked out once per frame.
	 */
	private static class GroupStyle
	{
		private final HighlightGroup group;
		private final Color color;
		private final Color fill;
		private final Color areaFill;
		private final int width;
		private final int feather;
		private final BasicStroke stroke;

		private GroupStyle(HighlightGroup group)
		{
			this.group = group;
			color = group.getHighlightColorValue();
			fill = group.getFillColorValue();
			areaFill = fill.getAlpha() > 0 ? fill
				: new Color(color.getRed(), color.getGreen(), color.getBlue(), AREA_FALLBACK_ALPHA);
			width = group.getBorderWidth();
			feather = group.getOutlineFeather();
			stroke = new BasicStroke(width);
		}
	}
}
