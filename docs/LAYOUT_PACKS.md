# Layout Packs & Sub-Layouts Guide (v2)

Moonlight KB v2 introduces **Layout Packs** so a single game or desktop profile can contain multiple switchable control layers (**Sub-layouts**) instead of just one flat set of buttons.

---

## 1. Layout Packs vs. Sub-Layouts

- **Layout Pack**: The top-level profile you see in the **Layout Packs** manager (e.g., *Cyberpunk 2077*, *MMO Setup*, *Desktop*). Exporting, importing, or cloning from the Layout Packs screen operates on the entire pack.
- **Sub-Layout**: An individual control screen inside a pack (e.g., *Combat*, *Inventory*, *Driving*, *Menu*).
  - **Active Layout**: Turn on **Active Layout** in a sub-layout's settings to make it the default screen that loads when a stream starts.
  - **Mouse Input Mode**: Each sub-layout chooses its own touch mode independently:
    - **Touchpad**: Relative trackpad-style mouse cursor.
    - **Direct**: Native Windows multi-touch input passed directly to the host.

---

## 2. Switching Sub-Layouts Mid-Stream

You can switch between sub-layouts on the fly without opening any menu:

1. Open a sub-layout in the **Layout Editor** and select (or add) a button.
2. Enable **Change layout** in the button inspector and pick a **Target** sub-layout.
3. *(Optional)* Set **Key assignment** to **None** if the button is only meant to switch layouts, or keep a key/combo bound to trigger both at once.

During a stream (and in **Preview Mode**), pressing that button switches immediately to the target sub-layout, applies its **Mouse Input Mode**, and briefly shows `"Currently on layout: <name>"` at the bottom of the screen.

---

## 3. Layout Editor & Precision Controls

- **Smart Edge & Center Alignment Guides**: Dragging or resizing a button snaps cleanly to nearby button edges, canvas edges, and centerlines with live guide lines.
- **Lock 1:1 (Square)**: Keeps width and height equal when resizing via handles or sliders.
- **Corner Radius (0–100%)**: Adjusts button roundness proportionally (`0%` = sharp rectangle, `100%` = circle/pill).
- **0–4 Key Combo Picker**: The **Keys** tab supports binding 0 to 4 simultaneous keys (tap **Apply** to save), alongside **Mouse** and **Actions** tabs.
- **Temporary Menu Side Swap (`⇄`)**: Tap the swap icon in the editor sidebar header to move the menu between left and right; the button inspector still adapts automatically to the selected button's side.
- **Stream-Accurate Preview Mode**: Tap **Preview** to test multi-touch, sub-layout switching, and the **PC Keyboard** / **Mouse** overlays using the exact same touch layer as a live stream.

---

## 4. Button Behaviors & Multi-Touch

- **Absorb Touch**: When enabled, the button captures touches on itself. When disabled, touches pass through to the stream (useful for camera/look areas).
- **Slide**: Activates when your finger slides into the button from another spot or across adjacent slide buttons.
- **Keep hover outside button**: Keeps the button held down even if your finger drifts outside its bounds until you lift that finger.
- **D-Pad Controller**: Multi-directional pad with an outer directional arc and independent pointer tracking so movement stays isolated from other fingers.

---

## 5. Import & Export

- **Export** saves the full **Layout Pack** (all sub-layouts, mouse modes, layout-switch targets, and button properties) as a v2 JSON file or clipboard text.
- **Import** supports both **v2 Layout Packs** and **v1 single-layout JSON** files (v1 layouts are automatically wrapped into a Layout Pack).
