# Draftout goal artwork

`goal/` contains unchanged PNGs from Draftout 1.18.0, copied from the user-provided
`draftout-mod-original/draftout-1.18.0 copy/assets/draftout/textures/goal/` directory.
The artwork belongs to its original authors; it is not newly generated artwork.

`../icons.json` records the source goal class, base texture, alternative textures,
overlays and counts, checked against the original jar's goal renderers and base
classes. Reference: <https://draftoutmc.com/wiki/goals>.

Run `python3 scripts/draftout/generate.py` from the repository root (requires
Pillow). `gen_icons.py` exports the card images into the datapack images directory;
the normal goal generator assigns `display.image`. The server sends these images
to Bingo clients and uses the same art on map cards.

The current Bingo image format is static. Cycling textures use their first frame;
the `jeb_` sheep sprite sheet uses its first 100×100 frame, with the name-tag
overlay. Alternative texture paths are retained in the manifest for future
animation support. Original item-plus-overlay icons use Bingo's existing item
renders from `common/images.zip`, and number labels use Bingo's existing font.

The original mod has colored-sheep artwork for ten colors. Bingo's six additional
color variants retain their exact wool color and use the original skull overlay.
Goal rules and the number of generated objectives are unchanged.

Vanilla selection dialogs still use `display.item`: those dialogs do not consume
Bingo's custom card-image packets. This change affects card/HUD/map artwork.

The two original Draftout GUI reroll sprites (`draft_reroll.png` and `draft_reroll_used.png`) are also reused in `common/src/main/resources/assets/yet-another-minecraft-bingo/textures/gui/sprites/draft/`, preserving the original pixels.
