# Tape Measure

A client side Fabric mod for Minecraft 26.3 that measures things in the world and draws them the way
WorldEditCUI draws selections: bright outlines in front of terrain, faint ones behind it, so you can read a
shape through walls.

Nothing is sent to the server. Clicks made with the tape item are cancelled on your client before any packet
goes out, so it works on any server (WorldEdit or not) and never breaks or places blocks.

## Build

You need JDK 25 installed (Fabric Loom for 26.3 runs Gradle on Java 25 and refuses older versions). It does
not have to be your default Java: `gradle/gradle-daemon-jvm.properties` tells Gradle to find and use an
installed JDK 25 by itself.

* macOS: `brew install --cask temurin@25`, or the .pkg from https://adoptium.net
* Windows: the Temurin 25 .msi from https://adoptium.net

Then:

```
./gradlew build
```

If Gradle still says it is using an older Java, point it at JDK 25 for this terminal and try again
(`export JAVA_HOME=$(/usr/libexec/java_home -v 25)` on macOS, or set JAVA_HOME to the JDK 25 folder on
Windows). In IntelliJ, set Settings, Build Tools, Gradle, Gradle JVM to 25.

The mod jar ends up in `build/libs/tapemeasure-1.0.0.jar`. Drop it in your `mods` folder next to Fabric API
(0.162.0+26.3 or newer) and Fabric Loader 0.19.5 or newer.

## Using it

Hold **string** (change it with `/measure tool hand`, see below).

| Action | What it does |
| --- | --- |
| Left click | First point: corner 1, start, or center. In Path and Polygon it starts over. |
| Right click | Next point: corner 2, end, or edge. In Path and Polygon it adds a point. |
| Sneak + left click | Clear everything |
| Sneak + right click | Remove the last point |
| Sneak + scroll | Change mode |
| Ctrl + scroll | Grow (scroll up) or shrink (scroll down) the side of the box you look at, one block per notch. Look up or down to change the height. |

Boxes can grow on any side; polygons and circles grow up and down (their height). Points can be picked up to
128 blocks away. While you hold the tape, the next point follows your crosshair
(live preview) so you can see the numbers before you click.

The readout panel sits in the top left corner. Floating labels in the world show the size along each edge.

## Modes

**Box** (two corners, inclusive like WorldEdit)
Size X × Y × Z, volume, floor area, walls (one ring of blocks per layer times the height), hollow box (walls,
floor and ceiling), how many stacks and shulker boxes it takes to fill, and how many chunks it touches.
`/measure info` adds both corners, min and max corners, the corner to corner distance and region files.

**Line** (start and end)
Straight distance, horizontal distance, change on each axis, compass heading with the F3 yaw you need to face
that way, slope and grade, how many blocks a straight line of blocks needs, and the matching distance in the
Nether (or Overworld when you measure in the Nether). `/measure info` adds the Nether coordinates of both ends
and the walking distance with no diagonals.

**Path** (any number of points)
Total length, horizontal length, total climb and drop, blocks needed, the last leg and the straight distance
from start to end. Each leg gets its own floating label.

**Polygon** (three or more corners, like WorldEdit poly selections)
Blocks per layer (every block whose position is inside or on the outline), height from the lowest to the
highest corner, volume, outline blocks per layer, wall blocks, perimeter and the exact geometric area.

**Circle** (center, then a block on the edge)
Radius and width in blocks, filled disc blocks, ring (outline) blocks, circumference and exact area. If the
edge block is higher or lower than the center you also get a cylinder and its tube walls. Sphere and dome
block counts for the same radius are included up to a radius of 1024.

## Settings screen

Open it with `/measure config`, the "Open settings" keybind, or the Config button in Mod Menu (if you have
Mod Menu installed). Every option has a tooltip on its name and a Reset button. Colors show a live swatch
as you type. Done or Escape saves, Cancel throws your changes away, and Reset all puts everything back to
the defaults (still only saved when you press Done).

## Counting blocks

`/measure count` scans the Box, Polygon or Circle you picked and lists the blocks inside by type, most common
first, with stack counts. Click **[Copy full list]** to put a CSV of every block on your clipboard. Big areas
are scanned a slice per tick so the game never freezes, and the panel shows progress. Only chunks your client
has loaded can be read; anything else is reported as skipped.

## Commands

| Command | |
| --- | --- |
| `/measure` or `/measure info` | Print the full readout in chat, with a [Copy] button |
| `/measure copy` | Copy the readout to the clipboard |
| `/measure mode box\|line\|path\|polygon\|circle` | Change mode |
| `/measure p1 [here \| x y z]` | First point at your crosshair, your feet, or exact coordinates |
| `/measure p2 [here \| x y z]` and `/measure add ...` | Next point the same way |
| `/measure expand <n> [side]` | Push a side out by n blocks. Sides: up, down, north, south, east, west, or vert (up and down). No side means the way you look. |
| `/measure contract <n> [side]` | Pull that side in by n blocks (unlike WorldEdit, the side you name is the one that moves) |
| `/measure shift <n> [side]` | Move the whole shape |
| `/measure undo`, `/measure clear` | Remove the last point, or everything |
| `/measure count [stop]` | Count blocks in the selection |
| `/measure hud [top_left\|top_right\|bottom_left\|bottom_right]` | Toggle or move the panel |
| `/measure grid\|labels\|preview\|face\|xray\|scroll [on\|off]` | Toggle features (see Config) |
| `/measure reach <4 to 512>` | How far away points can be picked |
| `/measure tool <item>`, `/measure tool hand`, `/measure tool none` | Change or turn off the tape item |
| `/measure show`, `/measure hide` | Show or hide everything |
| `/measure config` | Open the settings screen |
| `/measure reload` | Reload the config file |
| `/measure help` | Short help in chat |

## Keybinds

All unbound by default so nothing clashes with Litematica, Xaero or vanilla. Set them under Options, Controls,
Key Binds, Tape Measure: first point, next point, remove last point, clear, next mode, show or hide the
panel, show or hide everything, count blocks, open settings, grow or shrink the side you look at by one. Keybinds work without holding the tape.

## Config

`config/tapemeasure.json`, created on first launch. Everything here is also on the settings screen, and
most options have a command too.

| Option | Default | |
| --- | --- | --- |
| `toolItem` | `minecraft:string` | The tape item. Litematica already uses the stick. |
| `toolEnabled` | `true` | Turn the tape item off and use keybinds or commands only |
| `reach` | `128` | How far away points can be picked |
| `targetFace` | `false` | Pick the air block in front of the face you look at (handy for room interiors) |
| `livePreview` | `true` | Next point follows the crosshair while you hold the tape |
| `sneakScrollChangesMode` | `true` | Sneak + scroll changes mode instead of hotbar slot |
| `ctrlScrollResizes` | `true` | Ctrl + scroll grows or shrinks the side you look at |
| `showGrid` | `true` | One line per block on the faces of a box |
| `showLabels` | `true` | Floating size labels |
| `showHud` | `true` | The readout panel |
| `hudCorner`, `hudOffsetX`, `hudOffsetY` | `TOP_LEFT`, `4`, `4` | Panel position |
| `hideObstructedLines` | `false` | Hide the faint lines that show through blocks |
| `lineWidth` | `3.0` | Outline thickness |
| `gridRange` | `96` | Grid lines further than this from you are skipped |
| `countBlocksPerTick` | `200000` | Block count speed |
| `countTopEntries` | `15` | Block types listed in chat |
| `countMaxVolume` | `64000000` | Largest selection the counter accepts |
| `colors` | | `#RRGGBB` or `#RRGGBBAA` for box, grid, corner1, corner2, line, path, polygon, circle, preview |

## How it works

* `render/LineTypes.java` builds two line render types with custom depth tests, the same approach as
  WorldEditCUI: one draws the parts of a line in front of terrain, the other draws hidden parts faintly.
* `render/MeasureRenderer.java` queues the lines and labels in Fabric's `LevelRenderEvents.COLLECT_SUBMITS`.
  (WorldEditCUI submits in `END_MAIN`, which in 26.3 runs after the frame's submits were already collected,
  so its lines land a frame late.)
* `math/` holds all the measuring math with no Minecraft classes, so it can be tested on its own.
* `count/BlockCounter.java` scans a selection a slice per tick.
* `gui/` is the settings screen. It edits a copy of the config so Cancel really cancels. Mod Menu is an
  optional compile only dependency; the Config button only appears when Mod Menu is installed.
* `input/ToolInput.java` cancels clicks with the tape item using Fabric's `ClientPreAttackCallback`,
  `UseBlockCallback` and `UseItemCallback`, so nothing reaches the server.

See NOTICE.md for the WorldEditCUI license notice on the adapted render file.
