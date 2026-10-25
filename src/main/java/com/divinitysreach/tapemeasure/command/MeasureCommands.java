package com.divinitysreach.tapemeasure.command;

import com.divinitysreach.tapemeasure.TapeMeasureClient;
import com.divinitysreach.tapemeasure.config.TapeConfig;
import com.divinitysreach.tapemeasure.gui.TapeSettingsScreen;
import com.divinitysreach.tapemeasure.input.Actions;
import com.divinitysreach.tapemeasure.input.Targeting;
import com.divinitysreach.tapemeasure.math.Mode;
import com.divinitysreach.tapemeasure.math.Pt;
import com.divinitysreach.tapemeasure.math.Readout;
import com.divinitysreach.tapemeasure.math.Side;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * {@code /measure} client commands. They run entirely on your client and never reach the server.
 */
public final class MeasureCommands {
	private MeasureCommands() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext buildContext) {
		LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal("measure")
			.executes(MeasureCommands::info);

		root.then(ClientCommands.literal("info").executes(MeasureCommands::info));
		root.then(ClientCommands.literal("help").executes(MeasureCommands::help));

		LiteralArgumentBuilder<FabricClientCommandSource> mode = ClientCommands.literal("mode");
		for (Mode m : Mode.values()) {
			mode.then(ClientCommands.literal(m.id()).executes(ctx -> {
				Actions.setMode(ctx.getSource().getClient(), m);
				return 1;
			}));
		}
		root.then(mode);

		root.then(pointCommand("p1", Actions::primaryAt));
		root.then(pointCommand("p2", Actions::secondaryAt));
		root.then(pointCommand("add", Actions::secondaryAt));

		root.then(resizeCommand("expand", 1));
		root.then(resizeCommand("contract", -1));
		root.then(shiftCommand());

		root.then(ClientCommands.literal("undo").executes(ctx -> {
			Actions.undo(ctx.getSource().getClient());
			return 1;
		}));
		root.then(ClientCommands.literal("clear").executes(ctx -> {
			Actions.clear(ctx.getSource().getClient());
			return 1;
		}));
		root.then(ClientCommands.literal("count")
			.executes(ctx -> {
				Actions.count(ctx.getSource().getClient());
				return 1;
			})
			.then(ClientCommands.literal("stop").executes(ctx -> {
				TapeMeasureClient.get().counter().cancel();
				ctx.getSource().sendFeedback(Component.literal("Block count stopped").withStyle(ChatFormatting.GRAY));
				return 1;
			})));

		root.then(ClientCommands.literal("copy").executes(ctx -> {
			Readout readout = TapeMeasureClient.get().pickedReadout(ctx.getSource().getClient());
			ctx.getSource().getClient().keyboardHandler.setClipboard(readout.toPlainText());
			ctx.getSource().sendFeedback(Component.literal("Measurement copied to clipboard").withStyle(ChatFormatting.GREEN));
			return 1;
		}));

		LiteralArgumentBuilder<FabricClientCommandSource> hud = ClientCommands.literal("hud").executes(ctx -> {
			Actions.toggleHud(ctx.getSource().getClient());
			return 1;
		});
		for (TapeConfig.HudCorner corner : TapeConfig.HudCorner.values()) {
			hud.then(ClientCommands.literal(corner.name().toLowerCase(java.util.Locale.ROOT)).executes(ctx -> {
				TapeConfig config = TapeMeasureClient.get().config();
				config.hudCorner = corner.name();
				config.showHud = true;
				saved(ctx, "Panel moved to " + corner.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
				return 1;
			}));
		}
		root.then(hud);

		root.then(toggle("grid", "Grid", c -> c.showGrid, (c, v) -> c.showGrid = v));
		root.then(toggle("labels", "Floating labels", c -> c.showLabels, (c, v) -> c.showLabels = v));
		root.then(toggle("preview", "Live preview", c -> c.livePreview, (c, v) -> c.livePreview = v));
		root.then(toggle("face", "Face targeting (pick the air block in front of a face)", c -> c.targetFace, (c, v) -> c.targetFace = v));
		root.then(toggle("xray", "Faint lines through blocks", c -> !c.hideObstructedLines, (c, v) -> c.hideObstructedLines = !v));
		root.then(toggle("scroll", "Sneak and scroll changes mode", c -> c.sneakScrollChangesMode, (c, v) -> c.sneakScrollChangesMode = v));
		root.then(toggle("ctrlscroll", "Ctrl and scroll grows or shrinks the side you look at", c -> c.ctrlScrollResizes, (c, v) -> c.ctrlScrollResizes = v));

		root.then(ClientCommands.literal("show").executes(ctx -> {
			TapeMeasureClient.get().setVisible(true);
			ctx.getSource().sendFeedback(Component.literal("Tape Measure shown").withStyle(ChatFormatting.GREEN));
			return 1;
		}));
		root.then(ClientCommands.literal("hide").executes(ctx -> {
			TapeMeasureClient.get().setVisible(false);
			ctx.getSource().sendFeedback(Component.literal("Tape Measure hidden (/measure show to bring it back)").withStyle(ChatFormatting.YELLOW));
			return 1;
		}));

		root.then(ClientCommands.literal("reach")
			.then(ClientCommands.argument("blocks", IntegerArgumentType.integer(4, 512)).executes(ctx -> {
				TapeConfig config = TapeMeasureClient.get().config();
				config.reach = IntegerArgumentType.getInteger(ctx, "blocks");
				saved(ctx, "Reach set to " + config.reach + " blocks");
				return 1;
			})));

		root.then(ClientCommands.literal("tool")
			.then(ClientCommands.literal("none").executes(ctx -> {
				TapeConfig config = TapeMeasureClient.get().config();
				config.toolEnabled = false;
				saved(ctx, "Tape item turned off (use keybinds or commands)");
				return 1;
			}))
			.then(ClientCommands.literal("hand").executes(ctx -> {
				Item item = ctx.getSource().getPlayer().getMainHandItem().getItem();
				return setTool(ctx, item);
			}))
			.then(ClientCommands.argument("item", ItemArgument.item(buildContext)).executes(ctx ->
				setTool(ctx, ItemArgument.getItem(ctx, "item").item().value()))));

		for (String name : new String[] {"config", "settings"}) {
			root.then(ClientCommands.literal(name).executes(ctx -> {
				TapeMeasureClient.get().openScreenNextTick(new TapeSettingsScreen(null));
				return 1;
			}));
		}

		root.then(ClientCommands.literal("reload").executes(ctx -> {
			TapeMeasureClient.get().reloadConfig();
			ctx.getSource().sendFeedback(Component.literal("Config reloaded").withStyle(ChatFormatting.GREEN));
			return 1;
		}));

		dispatcher.register(root);
	}

	private interface PointSetter {
		void set(net.minecraft.client.Minecraft mc, Pt p);
	}

	/** {@code /measure p1}, {@code /measure p1 here}, {@code /measure p1 <x> <y> <z>}. */
	private static LiteralArgumentBuilder<FabricClientCommandSource> pointCommand(String name, PointSetter setter) {
		return ClientCommands.literal(name)
			.executes(ctx -> {
				Pt target = Targeting.target(ctx.getSource().getClient());
				if (target == null) {
					ctx.getSource().sendError(Component.literal("Not looking at a block"));
					return 0;
				}
				setter.set(ctx.getSource().getClient(), target);
				return 1;
			})
			.then(ClientCommands.literal("here").executes(ctx -> {
				setter.set(ctx.getSource().getClient(), Targeting.feet(ctx.getSource().getClient()));
				return 1;
			}))
			.then(ClientCommands.argument("x", IntegerArgumentType.integer())
				.then(ClientCommands.argument("y", IntegerArgumentType.integer())
					.then(ClientCommands.argument("z", IntegerArgumentType.integer()).executes(ctx -> {
						Pt p = new Pt(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"),
							IntegerArgumentType.getInteger(ctx, "z"));
						setter.set(ctx.getSource().getClient(), p);
						return 1;
					}))));
	}

	/**
	 * {@code /measure expand <amount> [side]} and {@code /measure contract <amount> [side]}. Without a side it
	 * uses the side you are looking toward; {@code vert} means both up and down.
	 */
	private static LiteralArgumentBuilder<FabricClientCommandSource> resizeCommand(String name, int sign) {
		RequiredArgumentBuilder<FabricClientCommandSource, Integer> amount = ClientCommands.argument("amount", IntegerArgumentType.integer(1, 4096))
			.executes(ctx -> {
				Actions.resize(ctx.getSource().getClient(), sign * IntegerArgumentType.getInteger(ctx, "amount"));
				return 1;
			});
		for (Side side : Side.values()) {
			amount.then(ClientCommands.literal(side.id()).executes(ctx -> {
				Actions.resize(ctx.getSource().getClient(), sign * IntegerArgumentType.getInteger(ctx, "amount"), side);
				return 1;
			}));
		}
		amount.then(ClientCommands.literal("vert").executes(ctx -> {
			Actions.resize(ctx.getSource().getClient(), sign * IntegerArgumentType.getInteger(ctx, "amount"), Side.UP, Side.DOWN);
			return 1;
		}));
		return ClientCommands.literal(name).then(amount);
	}

	/** {@code /measure shift <amount> [side]} moves the whole shape. */
	private static LiteralArgumentBuilder<FabricClientCommandSource> shiftCommand() {
		RequiredArgumentBuilder<FabricClientCommandSource, Integer> amount = ClientCommands.argument("amount", IntegerArgumentType.integer(1, 30_000_000))
			.executes(ctx -> {
				Actions.shift(ctx.getSource().getClient(), IntegerArgumentType.getInteger(ctx, "amount"), null);
				return 1;
			});
		for (Side side : Side.values()) {
			amount.then(ClientCommands.literal(side.id()).executes(ctx -> {
				Actions.shift(ctx.getSource().getClient(), IntegerArgumentType.getInteger(ctx, "amount"), side);
				return 1;
			}));
		}
		return ClientCommands.literal("shift").then(amount);
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> toggle(String name, String what,
			Function<TapeConfig, Boolean> getter, BiConsumer<TapeConfig, Boolean> setter) {
		return ClientCommands.literal(name)
			.executes(ctx -> {
				TapeConfig config = TapeMeasureClient.get().config();
				boolean value = !getter.apply(config);
				setter.accept(config, value);
				saved(ctx, what + (value ? " on" : " off"));
				return 1;
			})
			.then(ClientCommands.literal("on").executes(ctx -> {
				setter.accept(TapeMeasureClient.get().config(), true);
				saved(ctx, what + " on");
				return 1;
			}))
			.then(ClientCommands.literal("off").executes(ctx -> {
				setter.accept(TapeMeasureClient.get().config(), false);
				saved(ctx, what + " off");
				return 1;
			}));
	}

	private static int setTool(CommandContext<FabricClientCommandSource> ctx, Item item) {
		String id = BuiltInRegistries.ITEM.getKey(item).toString();
		if (id.equals("minecraft:air")) {
			ctx.getSource().sendError(Component.literal("Hold an item first"));
			return 0;
		}
		TapeConfig config = TapeMeasureClient.get().config();
		config.toolItem = id;
		config.toolEnabled = true;
		saved(ctx, "Tape item is now " + id);
		return 1;
	}

	private static void saved(CommandContext<FabricClientCommandSource> ctx, String message) {
		TapeConfig config = TapeMeasureClient.get().config();
		config.sanitize();
		config.save();
		ctx.getSource().sendFeedback(Component.literal(message).withStyle(ChatFormatting.GREEN));
	}

	private static int info(CommandContext<FabricClientCommandSource> ctx) {
		FabricClientCommandSource source = ctx.getSource();
		Readout readout = TapeMeasureClient.get().pickedReadout(source.getClient());
		if (readout.rows().isEmpty()) {
			source.sendFeedback(Component.literal("Nothing measured yet. Hold a " + TapeMeasureClient.get().config().toolItem
				+ " and left click a block, or see /measure help").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		source.sendFeedback(Component.literal("Tape Measure: ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal(readout.title()).withStyle(ChatFormatting.WHITE))
			.append(Component.literal("  [Copy]").withStyle(style -> style
				.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent.CopyToClipboard(readout.toPlainText()))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Copy these numbers"))))));
		for (Readout.Row row : readout.rows()) {
			source.sendFeedback(Component.literal("  " + row.label() + ": ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(row.value()).withStyle(ChatFormatting.WHITE)));
		}
		return 1;
	}

	private static int help(CommandContext<FabricClientCommandSource> ctx) {
		Consumer<String> line = text -> ctx.getSource().sendFeedback(Component.literal(text).withStyle(ChatFormatting.GRAY));
		String tool = TapeMeasureClient.get().config().toolItem;
		ctx.getSource().sendFeedback(Component.literal("Tape Measure").withStyle(ChatFormatting.GOLD));
		line.accept("Hold " + tool + ": left click sets the first point, right click the next one.");
		line.accept("Sneak + left click clears, sneak + right click undoes, sneak + scroll changes mode.");
		line.accept("Modes: box, line, path, polygon, circle  (/measure mode <name>)");
		line.accept("Ctrl + scroll grows (up) or shrinks (down) the side of the box you look at.");
		line.accept("/measure p1|p2|add [here | x y z]   set points without the tape");
		line.accept("/measure expand|contract <n> [up|down|north|south|east|west|vert] · shift <n> [side]");
		line.accept("/measure info · copy · undo · clear · count [stop]");
		line.accept("/measure hud [top_left|top_right|bottom_left|bottom_right]");
		line.accept("/measure grid|labels|preview|face|xray|scroll|ctrlscroll [on|off]");
		line.accept("/measure config   opens the settings screen");
		line.accept("/measure reach <blocks> · tool <item>|hand|none · show · hide · reload");
		MutableComponent keys = Component.literal("Keybinds: Options, Controls, Key Binds, Tape Measure").withStyle(ChatFormatting.DARK_GRAY);
		ctx.getSource().sendFeedback(keys);
		return 1;
	}
}
