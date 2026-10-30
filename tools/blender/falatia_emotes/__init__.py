import os

import bpy
from bpy.app.handlers import persistent
from bpy.props import (BoolProperty, CollectionProperty, EnumProperty, FloatProperty, FloatVectorProperty,
                       IntProperty, PointerProperty, StringProperty)

from . import effects, export, rig

LOOP_ITEMS = (
    ("NONE", "Once", "Plays once and ends"),
    ("LOOP", "Loop", "Repeats until the player moves"),
    ("HOLD", "Hold", "Holds the last frame until the player moves"),
)


def _default_emotes_dir():
    return os.path.join(os.path.expanduser("~"), ".minecraft", "config", "falatia-emotes", "emotes")


class FalatiaEmotesPreferences(bpy.types.AddonPreferences):
    bl_idname = __package__

    emotes_dir: StringProperty(
        name="Game emotes folder",
        description="config/falatia-emotes/emotes in the game folder",
        subtype="DIR_PATH",
        default=_default_emotes_dir(),
    )
    author: StringProperty(
        name="Author",
        description="Your nickname, shown in game under the emote. Used for every emote you export",
    )

    def draw(self, context):
        self.layout.prop(self, "emotes_dir")
        self.layout.prop(self, "author")


EVENT_KINDS = (
    ("SOUND", "Sound", "Game sound", "SPEAKER", 0),
    ("PARTICLE", "Particles", "Game particles", "PARTICLES", 1),
    ("PRESET", "Preset", "Ready-made particle set", "PRESET", 2),
)


class FalatiaEmoteEvent(bpy.types.PropertyGroup):
    kind: EnumProperty(name="Type", items=EVENT_KINDS, default="SOUND")
    frame: IntProperty(name="Frame", description="Animation frame", default=1)

    sound: EnumProperty(name="Sound", items=effects.sound_items())
    sound_custom: StringProperty(name="Sound id", description="For example minecraft:entity.cat.purr, as in /playsound")
    volume: FloatProperty(name="Volume", default=1.0, min=0.0, max=4.0)
    pitch: FloatProperty(name="Pitch", default=1.0, min=0.1, max=2.0)

    particle: EnumProperty(name="Particle", items=effects.particle_items())
    particle_custom: StringProperty(name="Particle id", description="For example minecraft:sculk_soul, as in /particle")
    particle_options: StringProperty(
        name="Options",
        description='Particle options as JSON, as the game writes them, e.g. {"color": [1, 0, 0], "scale": 1}')
    color: FloatVectorProperty(name="Color", subtype="COLOR_GAMMA", size=3, min=0.0, max=1.0, default=(1.0, 0.3, 0.3))
    color2: FloatVectorProperty(name="To color", subtype="COLOR_GAMMA", size=3, min=0.0, max=1.0, default=(0.3, 0.5, 1.0))
    scale: FloatProperty(name="Scale", default=1.0, min=0.01, max=4.0)
    power: FloatProperty(name="Power", default=1.0, min=0.0, max=10.0)
    block: StringProperty(name="Block", default="minecraft:stone")
    item: StringProperty(name="Item", default="minecraft:diamond")
    locator: EnumProperty(name="Origin", items=effects.locator_items(), default="body")
    count: IntProperty(name="Count", default=5, min=1, max=64)
    spread: FloatVectorProperty(name="Spread", description="Blocks along X / Y / Z", size=3, min=0.0, max=8.0, default=(0.3, 0.3, 0.3))
    speed: FloatProperty(name="Speed", default=0.0, min=0.0, max=2.0)
    offset: FloatVectorProperty(name="Offset", description="Blocks: right / up / forward from the point", size=3, default=(0.0, 0.0, 0.0))

    preset: EnumProperty(name="Preset", items=effects.preset_items())
    locator_preset: EnumProperty(name="Origin", items=effects.locator_items(with_default=True), default="DEFAULT")


class FalatiaEmoteSettings(bpy.types.PropertyGroup):
    emote_id: StringProperty(
        name="Id",
        description="File name: lowercase Latin letters, digits, _ and -",
    )
    display_name: StringProperty(name="Name", description="Emote name shown in the wheel")
    description: StringProperty(name="Description", description="A few words about the emote, shown in the wheel and the layout screen")
    loop: EnumProperty(name="Playback", items=LOOP_ITEMS, default="NONE")
    icon: BoolProperty(name="Icon", description="Render a wheel icon from the FalatiaIconCamera camera", default=True)
    icon_frame: IntProperty(
        name="Icon frame",
        description="Frame to render the icon from; -1 uses the current frame",
        default=-1,
        min=-1,
    )
    events: CollectionProperty(type=FalatiaEmoteEvent)
    events_index: IntProperty()
    paired: BoolProperty(name="Paired")
    partner_action: PointerProperty(name="Partner action", type=bpy.types.Action)
    wait_start: IntProperty(name="From", description="First frame of the loop played while the leader waits for a partner")
    wait_end: IntProperty(name="To", description="Last waiting frame; once someone joins, both play from here")


RIG_ITEMS = (
    ("SIMPLE", "Simple", "Bones match the game model parts: body, head, arms and legs don't bend"),
    ("BENDS", "With bends", "Adds chest, *_forearm and *_shin bones: waist, elbow and knee bends (bend channel)"),
)


class FalatiaSceneSettings(bpy.types.PropertyGroup):
    skin: StringProperty(name="Skin", description="64×64 skin PNG; without it the player is plain colored", subtype="FILE_PATH")
    slim: BoolProperty(name="Slim arms", description="Alex model (3-pixel arms)")
    rig: EnumProperty(name="Rig", items=RIG_ITEMS, default="SIMPLE")
    partner_skin: StringProperty(name="Partner skin", description="64×64 skin PNG for the second player of a paired emote",
                                 subtype="FILE_PATH")
    partner_slim: BoolProperty(name="Partner slim arms", description="The partner uses the Alex model (3-pixel arms)")


def find_player(context):
    obj = context.active_object
    if obj is not None and obj.type == "ARMATURE" and "root" in obj.data.bones and not obj.get("falatia_partner"):
        return obj
    return _main(context.scene)


def _main(scene):
    obj = scene.objects.get(rig.ARMATURE)
    return obj if obj is not None and obj.type == "ARMATURE" else None


def find_partner(scene, player):
    name = player.get("falatia_partner_name", rig.PARTNER) if player is not None else rig.PARTNER
    obj = scene.objects.get(name)
    return obj if obj is not None and obj.type == "ARMATURE" else None


def _current_settings(context):
    action = _current_action(context)
    return action.falatia_emote if action is not None else None


def _clean_id(text):
    return "".join(ch for ch in text.strip().lower().replace(" ", "_") if ch.isascii() and (ch.isalnum() or ch in "_-"))


class FALATIA_OT_create_player(bpy.types.Operator):
    bl_idname = "falatia.create_player"
    bl_label = "Create Player"
    bl_description = "An armature with the game's bones, a mesh with the skin and an icon camera"
    bl_options = {"REGISTER", "UNDO"}

    def execute(self, context):
        if context.mode != "OBJECT":
            bpy.ops.object.mode_set(mode="OBJECT")
        settings = context.scene.falatia
        rig.create_player(context, slim=settings.slim, skin_path=settings.skin, bends=settings.rig == "BENDS")
        return {"FINISHED"}


class FALATIA_OT_apply_skin(bpy.types.Operator):
    bl_idname = "falatia.apply_skin"
    bl_label = "Update Skin"
    bl_description = "Apply the selected skin to the player"
    bl_options = {"REGISTER", "UNDO"}

    def execute(self, context):
        rig.set_skin(context.scene.falatia.skin)
        return {"FINISHED"}


class FALATIA_OT_new_emote(bpy.types.Operator):
    bl_idname = "falatia.new_emote"
    bl_label = "New Emote"
    bl_description = "A new Action for the player: one action is one emote"
    bl_options = {"REGISTER", "UNDO"}

    emote_id: StringProperty(name="Id", default="new_emote")
    display_name: StringProperty(name="Name", default="")

    @classmethod
    def poll(cls, context):
        return find_player(context) is not None

    def invoke(self, context, event):
        return context.window_manager.invoke_props_dialog(self)

    def execute(self, context):
        player = find_player(context)
        emote_id = _clean_id(self.emote_id) or "emote"
        action = bpy.data.actions.new(emote_id)
        action.use_fake_user = True
        action.falatia_emote.emote_id = emote_id
        action.falatia_emote.display_name = self.display_name.strip() or emote_id
        if player.animation_data is None:
            player.animation_data_create()
        player.animation_data.action = action
        return {"FINISHED"}


class FALATIA_OT_export_emote(bpy.types.Operator):
    bl_idname = "falatia.export_emote"
    bl_label = "Export to Game"
    bl_description = "Save the emote and its icon to the game's emotes folder; the mod picks them up automatically"

    @classmethod
    def poll(cls, context):
        player = find_player(context)
        return player is not None and player.animation_data is not None and player.animation_data.action is not None

    def execute(self, context):
        player = find_player(context)
        action = player.animation_data.action
        preferences = context.preferences.addons[__package__].preferences
        if not action.falatia_emote.emote_id:
            action.falatia_emote.emote_id = _clean_id(action.name)
        try:
            path = export.export(context, player, action.falatia_emote, preferences.emotes_dir, find_partner(context.scene, player),
                                 preferences.author.strip())
        except (ValueError, OSError) as error:
            self.report({"ERROR"}, str(error))
            return {"CANCELLED"}
        self.report({"INFO"}, f"Emote saved: {path}")
        return {"FINISHED"}


def _current_action(context):
    player = find_player(context)
    if player is None or player.animation_data is None:
        return None
    return player.animation_data.action


class FALATIA_OT_make_pair(bpy.types.Operator):
    bl_idname = "falatia.make_pair"
    bl_label = "Make Paired"
    bl_description = "Adds a second player (FalatiaPartner) in front, facing the main one, with its own action for this emote"
    bl_options = {"REGISTER", "UNDO"}

    @classmethod
    def poll(cls, context):
        return _current_action(context) is not None

    def execute(self, context):
        global _syncing
        if context.mode != "OBJECT":
            bpy.ops.object.mode_set(mode="OBJECT")
        player = find_player(context)
        action = _current_action(context)
        settings = action.falatia_emote
        if settings.partner_action is None:
            emote_id = settings.emote_id or _clean_id(action.name)
            partner_action = bpy.data.actions.new(f"{emote_id}.partner")
            partner_action.use_fake_user = True
            settings.partner_action = partner_action
        if not settings.paired:
            start, end = export.frame_range(context.scene, action)
            settings.wait_start = start
            settings.wait_end = start
        settings.paired = True
        if find_partner(context.scene, player) is None:
            scene_settings = context.scene.falatia
            _syncing = True
            try:
                partner = rig.create_player(context, slim=scene_settings.partner_slim, skin_path=scene_settings.partner_skin,
                                            bends=rig.has_bends(player), main=player)
                partner.select_set(False)
                context.view_layer.objects.active = player
                player.select_set(True)
            finally:
                _syncing = False
        sync_partner(context.scene)
        return {"FINISHED"}


class FALATIA_OT_unpair(bpy.types.Operator):
    bl_idname = "falatia.unpair"
    bl_label = "Make Single"
    bl_description = "Remove the second player from the emote (the partner action is kept and can be restored)"
    bl_options = {"REGISTER", "UNDO"}

    @classmethod
    def poll(cls, context):
        settings = _current_settings(context)
        return settings is not None and settings.paired

    def execute(self, context):
        _current_settings(context).paired = False
        sync_partner(context.scene)
        return {"FINISHED"}


class FALATIA_OT_wait_frame(bpy.types.Operator):
    bl_idname = "falatia.wait_frame"
    bl_label = "Waiting Frame"
    bl_description = "Set the waiting boundary to the current frame"
    bl_options = {"REGISTER", "UNDO"}

    end: BoolProperty(default=False)

    @classmethod
    def poll(cls, context):
        return FALATIA_OT_unpair.poll(context)

    def execute(self, context):
        settings = _current_settings(context)
        frame = context.scene.frame_current
        if self.end:
            settings.wait_end = frame
            settings.wait_start = min(settings.wait_start, frame)
        else:
            settings.wait_start = frame
            settings.wait_end = max(settings.wait_end, frame)
        return {"FINISHED"}


class FALATIA_OT_select_rig(bpy.types.Operator):
    bl_idname = "falatia.select_rig"
    bl_label = "Select Rig"
    bl_description = "Switch to the main or partner rig (keeping pose or object mode)"
    bl_options = {"REGISTER", "UNDO"}

    partner: BoolProperty(default=False)

    @classmethod
    def poll(cls, context):
        return find_player(context) is not None

    def execute(self, context):
        player = find_player(context)
        target = find_partner(context.scene, player) if self.partner else player
        if target is None:
            return {"CANCELLED"}
        mode = "POSE" if context.mode == "POSE" else "OBJECT"
        if context.mode != "OBJECT":
            bpy.ops.object.mode_set(mode="OBJECT")
        for other in context.selected_objects:
            other.select_set(False)
        context.view_layer.objects.active = target
        target.select_set(True)
        bpy.ops.object.mode_set(mode=mode)
        return {"FINISHED"}


_syncing = False


def sync_partner(scene):
    global _syncing
    if _syncing:
        return
    player = _main(scene)
    partner = find_partner(scene, player) if player is not None else None
    if partner is None:
        return
    action = player.animation_data.action if player.animation_data else None
    settings = action.falatia_emote if action is not None else None
    paired = settings is not None and settings.paired
    wanted = settings.partner_action if paired else None
    _syncing = True
    try:
        animation = partner.animation_data or partner.animation_data_create()
        if animation.action != wanted:
            animation.action = wanted
        mesh = rig.mesh_of(partner)
        for obj in (partner, mesh):
            if obj is not None and obj.hide_viewport == paired:
                obj.hide_viewport = not paired
            if obj is not None and obj.hide_render == paired:
                obj.hide_render = not paired
    finally:
        _syncing = False


@persistent
def _on_depsgraph_update(scene, depsgraph):
    sync_partner(scene)


class FALATIA_UL_events(bpy.types.UIList):
    def draw_item(self, context, layout, data, item, icon, active_data, active_propname, index):
        kind_icon = {"SOUND": "SPEAKER", "PARTICLE": "PARTICLES", "PRESET": "PRESET"}[item.kind]
        row = layout.row(align=True)
        row.label(text=str(item.frame), icon=kind_icon)
        row.label(text=effects.summary(item))


class FALATIA_OT_event_add(bpy.types.Operator):
    bl_idname = "falatia.event_add"
    bl_label = "Add Event"
    bl_description = "A sound or particles on the current frame"
    bl_options = {"REGISTER", "UNDO"}

    kind: EnumProperty(items=EVENT_KINDS)

    @classmethod
    def poll(cls, context):
        return _current_action(context) is not None

    def execute(self, context):
        settings = _current_action(context).falatia_emote
        event = settings.events.add()
        event.kind = self.kind
        event.frame = context.scene.frame_current
        settings.events_index = len(settings.events) - 1
        return {"FINISHED"}


class FALATIA_OT_event_remove(bpy.types.Operator):
    bl_idname = "falatia.event_remove"
    bl_label = "Remove Event"
    bl_options = {"REGISTER", "UNDO"}

    @classmethod
    def poll(cls, context):
        action = _current_action(context)
        return action is not None and 0 <= action.falatia_emote.events_index < len(action.falatia_emote.events)

    def execute(self, context):
        settings = _current_action(context).falatia_emote
        settings.events.remove(settings.events_index)
        settings.events_index = min(settings.events_index, len(settings.events) - 1)
        return {"FINISHED"}


class FALATIA_OT_event_frame(bpy.types.Operator):
    bl_idname = "falatia.event_frame"
    bl_label = "Event Frame"
    bl_description = "Move the event to the current frame or jump to its frame"
    bl_options = {"REGISTER", "UNDO"}

    jump: BoolProperty(default=False)

    @classmethod
    def poll(cls, context):
        return FALATIA_OT_event_remove.poll(context)

    def execute(self, context):
        settings = _current_action(context).falatia_emote
        event = settings.events[settings.events_index]
        if self.jump:
            context.scene.frame_set(event.frame)
        else:
            event.frame = context.scene.frame_current
        return {"FINISHED"}


def _draw_event(layout, event):
    row = layout.row(align=True)
    row.prop(event, "frame")
    row.operator(FALATIA_OT_event_frame.bl_idname, text="", icon="KEYFRAME").jump = False
    row.operator(FALATIA_OT_event_frame.bl_idname, text="", icon="PLAY").jump = True
    if event.kind == "SOUND":
        layout.prop(event, "sound")
        if event.sound == effects.CUSTOM:
            layout.prop(event, "sound_custom")
        row = layout.row(align=True)
        row.prop(event, "volume")
        row.prop(event, "pitch")
        return
    if event.kind == "PRESET":
        layout.prop(event, "preset")
        layout.prop(event, "locator_preset")
        return
    layout.prop(event, "particle")
    kind = effects.particle_kind(event)
    if kind == "custom":
        layout.prop(event, "particle_custom")
        layout.prop(event, "particle_options")
    elif kind in ("dust", "transition", "argb", "spell"):
        row = layout.row(align=True)
        row.prop(event, "color", text="")
        if kind == "transition":
            row.prop(event, "color2", text="")
        if kind in ("dust", "transition"):
            row.prop(event, "scale")
        if kind == "spell":
            row.prop(event, "power")
    elif kind == "block":
        layout.prop(event, "block")
    elif kind == "item":
        layout.prop(event, "item")
    layout.prop(event, "locator")
    row = layout.row(align=True)
    row.prop(event, "count")
    row.prop(event, "speed")
    layout.prop(event, "spread")
    layout.prop(event, "offset")


class FALATIA_PT_emotes(bpy.types.Panel):
    bl_label = "Emotes"
    bl_idname = "FALATIA_PT_emotes"
    bl_space_type = "VIEW_3D"
    bl_region_type = "UI"
    bl_category = "Falatia"

    def draw(self, context):
        layout = self.layout
        scene_settings = context.scene.falatia
        player = find_player(context)

        box = layout.box()
        box.label(text="Player", icon="ARMATURE_DATA")
        box.prop(scene_settings, "skin")
        if player is None:
            box.prop(scene_settings, "slim")
            box.row().prop(scene_settings, "rig", expand=True)
            box.operator(FALATIA_OT_create_player.bl_idname, icon="ADD")
            return
        bends = any(child in player.data.bones for child in rig.BENDS.values())
        box.label(text="Rig with bends" if bends else "Simple rig (no bends)", icon="BONE_DATA")
        box.operator(FALATIA_OT_apply_skin.bl_idname, icon="IMAGE_DATA")

        box = layout.box()
        box.label(text="Emote", icon="ACTION")
        animation = player.animation_data
        if animation is not None:
            box.prop(animation, "action", text="")
        box.operator(FALATIA_OT_new_emote.bl_idname, icon="ADD")
        action = animation.action if animation is not None else None
        if action is None:
            box.label(text="Create an emote and keyframe the bones")
            return
        settings = action.falatia_emote
        box.prop(settings, "emote_id")
        box.prop(settings, "display_name")
        box.prop(settings, "description")
        box.prop(settings, "loop")
        start, end = export.frame_range(context.scene, action)
        fps = context.scene.render.fps / context.scene.render.fps_base
        box.label(text=f"Frames {start}–{end} ({(end - start) / fps:.2f} s)", icon="TIME")
        row = box.row()
        row.prop(settings, "icon")
        sub = row.row()
        sub.enabled = settings.icon
        sub.prop(settings, "icon_frame", text="Frame")

        self.draw_pair(context, layout, player, settings)

        box = layout.box()
        box.label(text="Sounds and particles", icon="SPEAKER")
        row = box.row()
        row.template_list("FALATIA_UL_events", "", settings, "events", settings, "events_index", rows=3)
        column = row.column(align=True)
        column.operator(FALATIA_OT_event_add.bl_idname, text="", icon="SPEAKER").kind = "SOUND"
        column.operator(FALATIA_OT_event_add.bl_idname, text="", icon="PARTICLES").kind = "PARTICLE"
        column.operator(FALATIA_OT_event_add.bl_idname, text="", icon="PRESET").kind = "PRESET"
        column.separator()
        column.operator(FALATIA_OT_event_remove.bl_idname, text="", icon="REMOVE")
        if 0 <= settings.events_index < len(settings.events):
            _draw_event(box.column(), settings.events[settings.events_index])

        preferences = context.preferences.addons[__package__].preferences
        box = layout.box()
        box.prop(preferences, "emotes_dir", text="")
        box.prop(preferences, "author")
        box.operator(FALATIA_OT_export_emote.bl_idname, icon="EXPORT")

    def draw_pair(self, context, layout, player, settings):
        box = layout.box()
        box.label(text="Paired emote", icon="COMMUNITY")
        partner = find_partner(context.scene, player)
        if not settings.paired:
            if partner is None:
                box.prop(context.scene.falatia, "partner_skin")
                box.prop(context.scene.falatia, "partner_slim")
            box.operator(FALATIA_OT_make_pair.bl_idname, icon="ADD")
            return
        if partner is None:
            box.label(text="Partner rig is missing", icon="ERROR")
            box.operator(FALATIA_OT_make_pair.bl_idname, text="Create Partner", icon="ADD")
            return
        offset, facing = export.pair_placement(player, partner)
        box.label(text=f"Partner: {offset[0]:.2f} right, {offset[2]:.2f} ahead, {facing:.0f}°", icon="ORIENTATION_GLOBAL")
        row = box.row(align=True)
        row.operator(FALATIA_OT_select_rig.bl_idname, text="Main", icon="ARMATURE_DATA").partner = False
        row.operator(FALATIA_OT_select_rig.bl_idname, text="Partner", icon="ARMATURE_DATA").partner = True
        box.label(text="Waiting (frames)", icon="TIME")
        row = box.row(align=True)
        row.prop(settings, "wait_start")
        row.operator(FALATIA_OT_wait_frame.bl_idname, text="", icon="KEYFRAME").end = False
        row.prop(settings, "wait_end")
        row.operator(FALATIA_OT_wait_frame.bl_idname, text="", icon="KEYFRAME").end = True
        box.operator(FALATIA_OT_unpair.bl_idname, icon="X")


CLASSES = (
    FalatiaEmotesPreferences,
    FalatiaEmoteEvent,
    FalatiaEmoteSettings,
    FalatiaSceneSettings,
    FALATIA_OT_create_player,
    FALATIA_OT_apply_skin,
    FALATIA_OT_new_emote,
    FALATIA_OT_export_emote,
    FALATIA_OT_event_add,
    FALATIA_OT_event_remove,
    FALATIA_OT_event_frame,
    FALATIA_OT_make_pair,
    FALATIA_OT_unpair,
    FALATIA_OT_wait_frame,
    FALATIA_OT_select_rig,
    FALATIA_UL_events,
    FALATIA_PT_emotes,
)


def register():
    for cls in CLASSES:
        bpy.utils.register_class(cls)
    bpy.types.Action.falatia_emote = PointerProperty(type=FalatiaEmoteSettings)
    bpy.types.Scene.falatia = PointerProperty(type=FalatiaSceneSettings)
    bpy.app.handlers.depsgraph_update_post.append(_on_depsgraph_update)


def unregister():
    if _on_depsgraph_update in bpy.app.handlers.depsgraph_update_post:
        bpy.app.handlers.depsgraph_update_post.remove(_on_depsgraph_update)
    del bpy.types.Scene.falatia
    del bpy.types.Action.falatia_emote
    for cls in reversed(CLASSES):
        bpy.utils.unregister_class(cls)
