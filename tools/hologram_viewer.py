#!/usr/bin/env python3
"""
Monster Hologram viewer -- open a sprite sheet, cut it up, watch it move.

A billboard in Duel Dimension is a rectangle of a sheet cut into equal cells and
played as a run of frames, with one cell held while the card lies down. Getting
that right is four numbers and a judgement, and the judgement is one you can
only make by watching: whether the run reads as an animation or as a stutter,
and which cell is the pose.

So this shows the sheet with its cuts drawn on, plays the run beside it, and
writes out the entry the mod actually reads. What you watch here is what the
game will do.

TWO THINGS IT KNOWS THAT A PAIR OF EYES DOES NOT:

  Empty cells.  A grid rarely divides evenly into the art. Morphing Jar's
  sheet has eight cells and only seven pictures, and animating through the
  blank one blinks the monster out of existence once a second -- which reads
  as a rendering fault rather than a frame count one too high. Every cell is
  measured on load and the empty ones are tagged before anybody counts wrong.

  What the format can express.  The mod plays a CONTIGUOUS run: a first cell
  and a length. Tagging a frame after a gap is a thing this tool will let you
  do and the mod cannot represent, so it says so plainly instead of writing an
  entry that quietly drops it.

Needs Pillow (`pip install pillow`); tkinter ships with Python.
"""

import json
import os
import tkinter as tk
from tkinter import filedialog, ttk

try:
    from PIL import Image, ImageTk
except ImportError:  # pragma: no cover - the message is the whole handling
    raise SystemExit("This needs Pillow:  pip install pillow")


# ----------------------------------------------------------------- palette --
# The mod's own, so a sheet judged here looks like it will in the duel.
BG = "#12161C"
PANEL = "#1B222B"
EDGE = "#2A3441"
INK = "#DDE3EC"
DIM = "#8C97A6"
GOLD = "#F4D089"
BLUE = "#66B2FF"
GREEN = "#7CE38B"
RED = "#FF4C4C"

FRAME, EMPTY, POSE = "frame", "empty", "pose"
TAG_COLOUR = {FRAME: GOLD, EMPTY: RED, POSE: GREEN}
TAG_NEXT = {FRAME: EMPTY, EMPTY: POSE, POSE: FRAME}

# A cell with fewer opaque pixels than this is blank. Not zero: a stray
# half-transparent pixel at the edge of a cut is not a picture.
INK_FLOOR = 40
TICK_MS = 50  # one game tick, so the speed here is the speed in the duel


class Viewer(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("Monster Hologram viewer")
        self.configure(bg=BG)
        self.geometry("1180x760")
        self.minsize(940, 620)

        self.sheet = None          # the PIL image
        self.path = None
        self.tags = []             # one of FRAME/EMPTY/POSE per cell
        self.playing = True
        self.step = 0              # which step of the loop we are on
        self.frame_cache = []      # PhotoImage per cell, at preview size
        self.grid_photo = None
        self.after_id = None

        self.columns = tk.IntVar(value=4)
        self.rows = tk.IntVar(value=2)
        self.ticks = tk.IntVar(value=5)
        self.loop = tk.StringVar(value="LOOP")
        self.zoom = tk.DoubleVar(value=2.0)
        self.bob = tk.IntVar(value=0)
        self.card = tk.StringVar(value="")
        self.sheet_name = tk.StringVar(value="")

        self._style()
        self._build()
        self._tick()

    # ------------------------------------------------------------- styling --
    def _style(self):
        style = ttk.Style(self)
        style.theme_use("clam")
        style.configure(".", background=PANEL, foreground=INK,
                        fieldbackground=PANEL, bordercolor=EDGE, lightcolor=EDGE,
                        darkcolor=EDGE, focuscolor=BLUE)
        style.configure("TFrame", background=BG)
        style.configure("Panel.TFrame", background=PANEL)
        style.configure("TLabel", background=PANEL, foreground=INK, font=("Segoe UI", 10))
        style.configure("Head.TLabel", background=PANEL, foreground=GOLD,
                        font=("Segoe UI Semibold", 11))
        style.configure("Dim.TLabel", background=PANEL, foreground=DIM, font=("Segoe UI", 9))
        style.configure("TButton", background=EDGE, foreground=INK, borderwidth=0,
                        padding=(12, 6), font=("Segoe UI", 10))
        style.map("TButton", background=[("active", "#3A4757"), ("pressed", "#222C38")])
        style.configure("Accent.TButton", background="#2F6FB5", foreground="#FFFFFF")
        style.map("Accent.TButton", background=[("active", "#3D86D6")])
        style.configure("TSpinbox", arrowsize=13, padding=4)
        style.configure("TCombobox", padding=4)
        style.configure("Horizontal.TScale", background=PANEL)

    def _build(self):
        root = ttk.Frame(self, style="TFrame", padding=10)
        root.pack(fill="both", expand=True)
        root.columnconfigure(0, weight=3, minsize=430)
        root.columnconfigure(1, weight=2, minsize=380)
        root.rowconfigure(1, weight=1)

        # --- the bar across the top --------------------------------------
        bar = ttk.Frame(root, style="Panel.TFrame", padding=10)
        bar.grid(row=0, column=0, columnspan=2, sticky="ew", pady=(0, 10))
        ttk.Button(bar, text="Open sheet…", style="Accent.TButton",
                   command=self.open_sheet).pack(side="left")
        ttk.Button(bar, text="Find empty cells",
                   command=self.detect_empty).pack(side="left", padx=(8, 0))
        ttk.Button(bar, text="All frames",
                   command=lambda: self.set_all(FRAME)).pack(side="left", padx=(8, 0))
        ttk.Label(bar, textvariable=self.sheet_name, style="Dim.TLabel").pack(side="left",
                                                                              padx=(16, 0))

        # --- the sheet, with its cuts ------------------------------------
        left = ttk.Frame(root, style="Panel.TFrame", padding=10)
        left.grid(row=1, column=0, sticky="nsew", padx=(0, 10))
        left.rowconfigure(1, weight=1)
        left.columnconfigure(0, weight=1)
        ttk.Label(left, text="The sheet", style="Head.TLabel").grid(row=0, column=0, sticky="w")
        self.grid_canvas = tk.Canvas(left, bg="#0C1014", highlightthickness=0)
        self.grid_canvas.grid(row=1, column=0, sticky="nsew", pady=(8, 6))
        self.grid_canvas.bind("<Button-1>", self.click_cell)
        self.grid_canvas.bind("<Configure>", lambda event: self.redraw_grid())
        legend = ttk.Frame(left, style="Panel.TFrame")
        legend.grid(row=2, column=0, sticky="w")
        for text, colour in (("frame", GOLD), ("empty", RED), ("defence pose", GREEN)):
            chip = tk.Canvas(legend, width=11, height=11, bg=PANEL, highlightthickness=0)
            chip.create_rectangle(0, 0, 10, 10, fill=colour, outline="")
            chip.pack(side="left", padx=(0, 4))
            ttk.Label(legend, text=text, style="Dim.TLabel").pack(side="left", padx=(0, 14))
        ttk.Label(left, text="Click a cell to change what it is.",
                  style="Dim.TLabel").grid(row=3, column=0, sticky="w", pady=(6, 0))

        # --- the right-hand column ---------------------------------------
        right = ttk.Frame(root, style="TFrame")
        right.grid(row=1, column=1, sticky="nsew")
        right.rowconfigure(0, weight=1)
        right.columnconfigure(0, weight=1)

        play = ttk.Frame(right, style="Panel.TFrame", padding=10)
        play.grid(row=0, column=0, sticky="nsew")
        play.rowconfigure(1, weight=1)
        play.columnconfigure(0, weight=1)
        head = ttk.Frame(play, style="Panel.TFrame")
        head.grid(row=0, column=0, sticky="ew")
        ttk.Label(head, text="The hologram", style="Head.TLabel").pack(side="left")
        self.play_button = ttk.Button(head, text="Pause", width=8, command=self.toggle_play)
        self.play_button.pack(side="right")
        self.status = ttk.Label(head, text="", style="Dim.TLabel")
        self.status.pack(side="right", padx=(0, 10))
        self.preview = tk.Canvas(play, bg="#0C1014", highlightthickness=0)
        self.preview.grid(row=1, column=0, sticky="nsew", pady=(8, 0))

        controls = ttk.Frame(right, style="Panel.TFrame", padding=10)
        controls.grid(row=1, column=0, sticky="ew", pady=(10, 0))
        controls.columnconfigure(1, weight=1)
        controls.columnconfigure(3, weight=1)

        self._number(controls, 0, 0, "Columns", self.columns, 1, 16)
        self._number(controls, 0, 2, "Rows", self.rows, 1, 16)
        self._number(controls, 1, 0, "Speed (ticks)", self.ticks, 1, 20)
        self._number(controls, 1, 2, "Bob steps", self.bob, 0, 32)

        ttk.Label(controls, text="Loop").grid(row=2, column=0, sticky="w", pady=(8, 0))
        box = ttk.Combobox(controls, textvariable=self.loop, state="readonly", width=12,
                           values=("LOOP", "PING_PONG"))
        box.grid(row=2, column=1, sticky="ew", padx=(8, 16), pady=(8, 0))
        box.bind("<<ComboboxSelected>>", lambda event: self.restart())

        ttk.Label(controls, text="Zoom").grid(row=2, column=2, sticky="w", pady=(8, 0))
        scale = ttk.Scale(controls, from_=1.0, to=6.0, variable=self.zoom,
                          command=lambda value: self.rebuild_frames())
        scale.grid(row=2, column=3, sticky="ew", padx=(8, 0), pady=(8, 0))

        ttk.Label(controls, text="Card passcode").grid(row=3, column=0, sticky="w", pady=(8, 0))
        ttk.Entry(controls, textvariable=self.card).grid(row=3, column=1, sticky="ew",
                                                        padx=(8, 16), pady=(8, 0))
        ttk.Label(controls, text="Sheet name").grid(row=3, column=2, sticky="w", pady=(8, 0))
        ttk.Entry(controls, textvariable=self.sheet_name).grid(row=3, column=3, sticky="ew",
                                                              padx=(8, 0), pady=(8, 0))

        out = ttk.Frame(right, style="Panel.TFrame", padding=10)
        out.grid(row=2, column=0, sticky="ew", pady=(10, 0))
        out.columnconfigure(0, weight=1)
        head2 = ttk.Frame(out, style="Panel.TFrame")
        head2.grid(row=0, column=0, sticky="ew")
        ttk.Label(head2, text="monster_sprites.json", style="Head.TLabel").pack(side="left")
        ttk.Button(head2, text="Copy", width=8, command=self.copy_json).pack(side="right")
        self.json_box = tk.Text(out, height=9, bg="#0C1014", fg=INK, insertbackground=INK,
                                relief="flat", font=("Consolas", 9), wrap="none")
        self.json_box.grid(row=1, column=0, sticky="ew", pady=(8, 0))
        self.warning = ttk.Label(out, text="", style="Dim.TLabel", wraplength=380)
        self.warning.grid(row=2, column=0, sticky="w", pady=(6, 0))

        for var in (self.columns, self.rows):
            var.trace_add("write", lambda *_: self.regrid())
        for var in (self.ticks, self.bob):
            var.trace_add("write", lambda *_: self.write_json())

    def _number(self, parent, row, column, label, var, low, high):
        ttk.Label(parent, text=label).grid(row=row, column=column, sticky="w",
                                           pady=(0 if row == 0 else 8, 0))
        spin = ttk.Spinbox(parent, from_=low, to=high, textvariable=var, width=6)
        spin.grid(row=row, column=column + 1, sticky="ew",
                  padx=(8, 16 if column == 0 else 0), pady=(0 if row == 0 else 8, 0))

    # ------------------------------------------------------------- loading --
    def open_sheet(self):
        path = filedialog.askopenfilename(
            title="Open a sprite sheet",
            filetypes=[("PNG images", "*.png"), ("All files", "*.*")])
        if not path:
            return
        self.sheet = Image.open(path).convert("RGBA")
        self.path = path
        stem = os.path.splitext(os.path.basename(path))[0]
        self.sheet_name.set(stem.lower().replace(" ", "_"))
        self.regrid()
        self.detect_empty()

    def cells(self):
        return max(1, self.columns.get()) * max(1, self.rows.get())

    def cell_box(self, cell):
        """The pixel rectangle of one cell, or None without a sheet."""
        if self.sheet is None:
            return None
        across = max(1, self.columns.get())
        down = max(1, self.rows.get())
        width, height = self.sheet.size
        cell_w, cell_h = width / across, height / down
        column, row = cell % across, cell // across
        return (int(column * cell_w), int(row * cell_h),
                int((column + 1) * cell_w), int((row + 1) * cell_h))

    def regrid(self):
        """Resize the tag list to the grid, keeping what still fits."""
        try:
            wanted = self.cells()
        except tk.TclError:
            return
        if len(self.tags) < wanted:
            self.tags += [FRAME] * (wanted - len(self.tags))
        del self.tags[wanted:]
        self.restart()

    def detect_empty(self):
        """Tag every cell with no picture in it, and leave the rest alone.

        This is the one thing a sheet will not tell you by being looked at: a
        blank cell in the middle of a grid is indistinguishable from a pause in
        the animation until it plays, and then it reads as a bug."""
        if self.sheet is None:
            return
        found = 0
        for cell in range(self.cells()):
            box = self.cell_box(cell)
            alpha = self.sheet.crop(box).getchannel("A")
            if sum(1 for value in alpha.getdata() if value > 8) < INK_FLOOR:
                self.tags[cell] = EMPTY
                found += 1
            elif self.tags[cell] == EMPTY:
                self.tags[cell] = FRAME
        self.warning.configure(
            text=f"{found} empty cell(s) found and tagged." if found
                 else "Every cell has a picture in it.",
            foreground=RED if found else DIM)
        self.restart()

    def set_all(self, tag):
        self.tags = [tag] * self.cells()
        self.restart()

    def click_cell(self, event):
        if self.sheet is None or not self.grid_geometry:
            return
        x0, y0, scale = self.grid_geometry
        across = max(1, self.columns.get())
        down = max(1, self.rows.get())
        width, height = self.sheet.size
        column = int((event.x - x0) / (width * scale / across))
        row = int((event.y - y0) / (height * scale / down))
        if not (0 <= column < across and 0 <= row < down):
            return
        cell = row * across + column
        if cell >= len(self.tags):
            return
        was = self.tags[cell]
        self.tags[cell] = TAG_NEXT[was]
        # One pose at a time: a card lies down in one position, and two cells
        # claiming to be it would silently mean the last one.
        if self.tags[cell] == POSE:
            for other in range(len(self.tags)):
                if other != cell and self.tags[other] == POSE:
                    self.tags[other] = FRAME
        self.restart()

    # ------------------------------------------------------------- the run --
    def run(self):
        """The contiguous run of frames the mod would play, as (first, count).

        Contiguous because that is what the format is: a first cell and a
        length. The longest run wins, so tagging the middle of a sheet works
        without having to say where it starts."""
        best_first, best_count = 0, 0
        first, count = 0, 0
        for cell, tag in enumerate(self.tags):
            if tag == FRAME:
                if count == 0:
                    first = cell
                count += 1
                if count > best_count:
                    best_first, best_count = first, count
            else:
                count = 0
        return best_first, best_count

    def pose_cell(self):
        for cell, tag in enumerate(self.tags):
            if tag == POSE:
                return cell
        return -1

    def order(self):
        """Which cell each step of the loop shows."""
        first, count = self.run()
        if count <= 0:
            return []
        if count == 1 or self.loop.get() == "LOOP":
            return [first + step for step in range(count)]
        # There and back again, without repeating either end -- holding the
        # first and last cell twice is a stutter at both ends of every sweep.
        return ([first + step for step in range(count)]
                + [first + count - 2 - step for step in range(count - 2)])

    def restart(self):
        self.step = 0
        self.rebuild_frames()
        self.redraw_grid()
        self.write_json()

    # ------------------------------------------------------------ drawing --
    grid_geometry = None

    def redraw_grid(self):
        canvas = self.grid_canvas
        canvas.delete("all")
        if self.sheet is None:
            canvas.create_text(canvas.winfo_width() / 2, canvas.winfo_height() / 2,
                               text="Open a sprite sheet to begin",
                               fill=DIM, font=("Segoe UI", 11))
            self.grid_geometry = None
            return
        room_w = max(1, canvas.winfo_width() - 16)
        room_h = max(1, canvas.winfo_height() - 16)
        width, height = self.sheet.size
        scale = min(room_w / width, room_h / height)
        shown = self.sheet.resize((max(1, int(width * scale)), max(1, int(height * scale))),
                                 Image.NEAREST)
        self.grid_photo = ImageTk.PhotoImage(shown)
        x0 = (canvas.winfo_width() - shown.width) / 2
        y0 = (canvas.winfo_height() - shown.height) / 2
        canvas.create_image(x0, y0, image=self.grid_photo, anchor="nw")
        self.grid_geometry = (x0, y0, scale)

        across = max(1, self.columns.get())
        down = max(1, self.rows.get())
        cell_w = shown.width / across
        cell_h = shown.height / down
        first, count = self.run()
        for cell in range(min(len(self.tags), across * down)):
            column, row = cell % across, cell // across
            left = x0 + column * cell_w
            top = y0 + row * cell_h
            tag = self.tags[cell]
            colour = TAG_COLOUR[tag]
            playing = first <= cell < first + count
            canvas.create_rectangle(left, top, left + cell_w, top + cell_h,
                                    outline=colour, width=2 if playing else 1)
            if playing:
                canvas.create_text(left + 5, top + 4, text=str(cell - first + 1),
                                   fill=colour, anchor="nw", font=("Segoe UI", 8))
            elif tag == POSE:
                canvas.create_text(left + 5, top + 4, text="pose", fill=GREEN,
                                   anchor="nw", font=("Segoe UI", 8))
            elif tag == EMPTY:
                canvas.create_line(left, top, left + cell_w, top + cell_h, fill=RED)

    def rebuild_frames(self):
        """One PhotoImage per cell of the run, at the current zoom."""
        self.frame_cache = []
        if self.sheet is None:
            return
        zoom = max(1.0, float(self.zoom.get()))
        for cell in self.order():
            box = self.cell_box(cell)
            piece = self.sheet.crop(box)
            piece = piece.resize((max(1, int(piece.width * zoom)),
                                  max(1, int(piece.height * zoom))), Image.NEAREST)
            self.frame_cache.append(ImageTk.PhotoImage(piece))

    def _tick(self):
        """One frame of playback, at the pace the duel would run it."""
        if self.playing and self.frame_cache:
            self.step = (self.step + 1) % len(self.frame_cache)
        self.draw_preview()
        self.after_id = self.after(max(1, self.ticks.get()) * TICK_MS, self._tick)

    def draw_preview(self):
        canvas = self.preview
        canvas.delete("all")
        middle_x = canvas.winfo_width() / 2
        middle_y = canvas.winfo_height() / 2
        if not self.frame_cache:
            canvas.create_text(middle_x, middle_y, fill=DIM, font=("Segoe UI", 11),
                               text="No frames — tag at least one cell as a frame")
            self.status.configure(text="")
            return
        photo = self.frame_cache[self.step % len(self.frame_cache)]
        lift = 0
        steps = max(0, self.bob.get())
        if steps >= 2:
            import math
            lift = -int(round(math.sin(2 * math.pi * (self.step % steps) / steps)
                              * photo.height() * 0.04))
        # Standing on a line, because a billboard stands on its card and a
        # sprite judged floating in the middle of a box will be drawn sunk.
        floor = middle_y + photo.height() / 2
        canvas.create_line(middle_x - photo.width() / 2 - 10, floor,
                           middle_x + photo.width() / 2 + 10, floor, fill=EDGE)
        canvas.create_image(middle_x, floor + lift, image=photo, anchor="s")
        first, count = self.run()
        self.status.configure(
            text=f"cell {self.order()[self.step % len(self.frame_cache)]}  ·  {count} frames")

    def toggle_play(self):
        self.playing = not self.playing
        self.play_button.configure(text="Pause" if self.playing else "Play")

    # -------------------------------------------------------------- output --
    def write_json(self):
        if not hasattr(self, "json_box"):
            return
        first, count = self.run()
        sheet = self.sheet_name.get().strip() or "type/name"
        try:
            code = int(self.card.get().strip() or 0)
        except ValueError:
            code = 0

        def layer(start, frames):
            return {"sheet": sheet, "x": 0, "y": 0, "w": 0, "h": 0,
                    "columns": max(1, self.columns.get()), "rows": max(1, self.rows.get()),
                    "first": start, "frames": frames,
                    "ticks": max(1, self.ticks.get()), "loop": self.loop.get(),
                    "trimX": 0, "trimY": 0, "bob": max(0, self.bob.get())}

        entry = {"card": code, "body": layer(first, max(1, count))}
        pose = self.pose_cell()
        if pose >= 0:
            entry["defence"] = layer(pose, 1)
        entry["scale"] = 1.0
        self.json_box.delete("1.0", "end")
        self.json_box.insert("1.0", json.dumps(entry, indent=2))

        # What the format cannot say. A frame outside the run is one the mod
        # would never play, and saying so here is cheaper than finding out
        # from a monster that skips.
        stranded = sum(1 for cell, tag in enumerate(self.tags)
                       if tag == FRAME and not (first <= cell < first + count))
        notes = []
        if stranded:
            notes.append(f"{stranded} frame cell(s) sit outside the run and would not play — "
                         "the mod plays one unbroken run, so move them together or tag them empty.")
        if code == 0:
            notes.append("No passcode yet.")
        self.warning.configure(text="  ".join(notes) if notes else "",
                               foreground=RED if stranded else DIM)

    def copy_json(self):
        self.clipboard_clear()
        self.clipboard_append(self.json_box.get("1.0", "end").strip())


if __name__ == "__main__":
    Viewer().mainloop()
