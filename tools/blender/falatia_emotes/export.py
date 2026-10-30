import json
import math
import os
import re

import bpy
from mathutils import Matrix, Quaternion, Vector

from . import effects
from .rig import BENDS, BONE_NAMES, CAMERA, MESH, PIVOTS, mesh_of, to_blender

TO_MODEL = Matrix(((1.0, 0.0, 0.0), (0.0, 0.0, -1.0), (0.0, 1.0, 0.0)))

TOLERANCE = {"rotation": 0.1, "position": 0.02, "scale": 0.002, "bend": 0.1}
REST = {"rotation": (0.0, 0.0, 0.0), "position": (0.0, 0.0, 0.0), "scale": (1.0, 1.0, 1.0)}
GAME_SCALE = 0.9375
FOLLOW_TORSO = ("head", "right_arm", "left_arm")

ICON_SIZE = 128
ICON_EXPOSURE = 1.7
ICON_MARGIN = 1.06

ID_PATTERN = re.compile(r"^[a-z0-9_-]+$")
VECTOR = re.compile(r"\[\s*([-0-9.,\s]+?)\s*\]")


def frame_range(scene, action):
    if action is not None and action.use_frame_range:
        return int(action.frame_start), int(action.frame_end)
    return scene.frame_start, scene.frame_end


def part_values(delta, pivot, previous_euler):
    _, rotation, scale = delta.decompose()
    rotation_model = TO_MODEL @ rotation.to_matrix() @ TO_MODEL.transposed()
    euler = rotation_model.to_euler("XYZ", previous_euler) if previous_euler else rotation_model.to_euler("XYZ")
    offset = delta @ pivot - pivot
    return euler, {
        "rotation": tuple(angle * 57.29577951308232 for angle in euler),
        "position": (offset.x * 16.0, offset.z * 16.0, offset.y * 16.0),
        "scale": (scale.x, scale.z, scale.y),
    }


def _unwrap(angle, near):
    return angle + 2.0 * math.pi * round((near - angle) / (2.0 * math.pi))


def bend_values(relative, previous):
    rotation = TO_MODEL @ relative.to_3x3().normalized() @ TO_MODEL.transposed()
    q = rotation.to_quaternion()
    twist = Quaternion((q.w, 0.0, q.y, 0.0))
    swing = q @ twist.normalized().inverted() if twist.magnitude > 1e-6 else q
    if swing.w < 0.0:
        swing.negate()
    sin_half = math.hypot(swing.x, swing.z)
    angle = 2.0 * math.atan2(sin_half, swing.w)
    near = previous[0] if previous else 0.0
    if angle < 1e-5:
        return (near, 0.0), Matrix.Identity(3)
    axis = math.atan2(-swing.z / sin_half, swing.x / sin_half)
    candidates = [(_unwrap(axis, near), angle), (_unwrap(axis + math.pi, near), -angle)]
    if previous:
        values = min(candidates, key=lambda value: abs(value[0] - near))
    else:
        values = min(candidates, key=lambda value: abs(value[0]))
    swing_blender = TO_MODEL.transposed() @ swing.to_matrix() @ TO_MODEL
    return values, swing_blender


def sample(context, armature_object, start, end):
    scene = context.scene
    fps = scene.render.fps / scene.render.fps_base
    current = scene.frame_current
    pivots = {name: to_blender(*PIVOTS[name]) for name in BONE_NAMES}
    bones = armature_object.pose.bones
    bends = {part: (child, bones[child].bone.head_local.copy())
             for part, child in BENDS.items() if part in bones and child in bones}
    tracks = {}
    eulers = {}
    previous_bends = {}
    try:
        for frame in range(start, end + 1):
            scene.frame_set(frame)
            time = (frame - start) / fps
            root = bones.get("root")
            root_delta = root.matrix @ root.bone.matrix_local.inverted() if root else Matrix.Identity(4)
            root_inverse = root_delta.inverted()
            deltas = {}
            for name in BONE_NAMES + tuple(child for child, _ in bends.values()):
                pose_bone = bones.get(name)
                if pose_bone is None:
                    continue
                delta = pose_bone.matrix @ pose_bone.bone.matrix_local.inverted()
                deltas[name] = delta if name == "root" else root_inverse @ delta
            bend_values_now = {}
            for part, (child, joint) in bends.items():
                relative = deltas[part].inverted() @ deltas[child]
                values, swing = bend_values(relative, previous_bends.get(part))
                previous_bends[part] = values
                bend_values_now[part] = values
                if part == "body":
                    bend = Matrix.Translation(joint) @ swing.to_4x4() @ Matrix.Translation(-joint)
                    follow = deltas["body"] @ bend @ deltas["body"].inverted()
                    follow_inverse = follow.inverted()
                    for name in FOLLOW_TORSO:
                        if name in deltas:
                            deltas[name] = follow_inverse @ deltas[name]
            for name in BONE_NAMES:
                if name not in deltas:
                    continue
                eulers[name], values = part_values(deltas[name], pivots[name], eulers.get(name))
                if name in bend_values_now:
                    axis, angle = bend_values_now[name]
                    values["bend"] = (math.degrees(axis), math.degrees(angle), 0.0)
                channels = tracks.setdefault(name, {channel: [] for channel in values})
                for channel, value in values.items():
                    channels[channel].append((time, value))
    finally:
        scene.frame_set(current)
    return tracks, (end - start) / fps


def _deviation(a, b, c):
    span = b[0] - a[0]
    alpha = (c[0] - a[0]) / span if span else 0.0
    return max(abs(a[1][i] + (b[1][i] - a[1][i]) * alpha - c[1][i]) for i in range(3))


def simplify(points, tolerance):
    if len(points) <= 2:
        return list(points)
    keep = {0, len(points) - 1}
    stack = [(0, len(points) - 1)]
    while stack:
        first, last = stack.pop()
        worst, index = 0.0, -1
        for i in range(first + 1, last):
            deviation = _deviation(points[first], points[last], points[i])
            if deviation > worst:
                worst, index = deviation, i
        if worst > tolerance:
            keep.add(index)
            stack.append((first, index))
            stack.append((index, last))
    return [points[i] for i in sorted(keep)]


def _number(value):
    value = round(value, 3)
    return 0 if value == 0 else (int(value) if value == int(value) else value)


def _vector(value):
    return [_number(component) for component in value]


def build_animation(tracks, length, name, description, loop, sounds=None, particles=None, pair=None, author=""):
    bones = {}
    for bone, channels in tracks.items():
        out = {}
        for channel, points in channels.items():
            tolerance = TOLERANCE[channel]
            if channel == "bend":
                if all(abs(value[1]) <= tolerance for _, value in points):
                    continue
            elif all(max(abs(value[i] - REST[channel][i]) for i in range(3)) <= tolerance for _, value in points):
                continue
            size = 2 if channel == "bend" else 3
            keys = simplify(points, tolerance)
            if len(keys) == 1 or all(_deviation(keys[0], keys[0], key) <= tolerance for key in keys):
                out[channel] = _vector(keys[0][1][:size])
            else:
                out[channel] = {f"{round(time, 4):g}": _vector(value[:size]) for time, value in keys}
        if out:
            bones[bone] = out
    animation = {"animation_length": _number(length)}
    if loop == "LOOP":
        animation["loop"] = True
    elif loop == "HOLD":
        animation["loop"] = "hold_on_last_frame"
    if name is not None:
        animation["falatia"] = {"name": name}
        if description:
            animation["falatia"]["description"] = description
        if author:
            animation["falatia"]["author"] = author
        if pair:
            animation["falatia"]["pair"] = pair
    animation["bones"] = bones
    if sounds:
        animation["sound_effects"] = sounds
    if particles:
        animation["particle_effects"] = particles
    return animation


def write_json(path, animations):
    data = {"format_version": "1.8.0", "animations": animations}
    text = json.dumps(data, ensure_ascii=False, indent="\t")
    text = VECTOR.sub(lambda match: "[" + ", ".join(match.group(1).split()).replace(",,", ",") + "]", text)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as file:
        file.write(text + "\n")
    os.replace(tmp, path)


def _fit_camera(context, camera, extra_meshes=()):
    mesh = context.scene.objects.get(MESH)
    if mesh is None:
        return
    context.view_layer.update()
    depsgraph = context.evaluated_depsgraph_get()
    points = []
    for obj in (mesh, *extra_meshes):
        evaluated = obj.evaluated_get(depsgraph)
        to_camera = camera.matrix_world.inverted() @ evaluated.matrix_world
        points.extend(to_camera @ vertex.co for vertex in evaluated.data.vertices)
    if not points:
        return
    min_x, max_x = min(p.x for p in points), max(p.x for p in points)
    min_y, max_y = min(p.y for p in points), max(p.y for p in points)
    scale = max(max_x - min_x, max_y - min_y) * ICON_MARGIN
    camera.data.ortho_scale = scale
    camera.data.shift_x = (max_x + min_x) / 2.0 / scale
    camera.data.shift_y = (max_y + min_y) / 2.0 / scale


def render_icon(context, path, frame, extra_meshes=()):
    scene = context.scene
    camera = scene.objects.get(CAMERA)
    if camera is None:
        return False
    render = scene.render
    shading = scene.display.shading
    saved = {
        "camera": scene.camera, "frame": scene.frame_current, "engine": render.engine,
        "x": render.resolution_x, "y": render.resolution_y, "percentage": render.resolution_percentage,
        "transparent": render.film_transparent, "filepath": render.filepath,
        "format": render.image_settings.file_format, "color": render.image_settings.color_mode,
        "light": shading.light, "color_type": shading.color_type, "aa": scene.display.render_aa,
        "view": scene.view_settings.view_transform, "look": scene.view_settings.look,
        "exposure": scene.view_settings.exposure,
        "ortho": camera.data.ortho_scale, "shift": (camera.data.shift_x, camera.data.shift_y),
    }
    tmp = path + ".tmp.png"
    try:
        scene.camera = camera
        scene.frame_set(frame)
        _fit_camera(context, camera, extra_meshes)
        render.engine = "BLENDER_WORKBENCH"
        render.resolution_x = render.resolution_y = ICON_SIZE
        render.resolution_percentage = 100
        render.film_transparent = True
        render.filepath = tmp
        render.image_settings.file_format = "PNG"
        render.image_settings.color_mode = "RGBA"
        shading.light = "STUDIO"
        shading.color_type = "TEXTURE"
        scene.display.render_aa = "16"
        scene.view_settings.view_transform = "Standard"
        scene.view_settings.look = "None"
        scene.view_settings.exposure = ICON_EXPOSURE
        bpy.ops.render.render(write_still=True)
    finally:
        scene.camera = saved["camera"]
        scene.frame_set(saved["frame"])
        render.engine = saved["engine"]
        render.resolution_x, render.resolution_y = saved["x"], saved["y"]
        render.resolution_percentage = saved["percentage"]
        render.film_transparent = saved["transparent"]
        render.filepath = saved["filepath"]
        render.image_settings.file_format = saved["format"]
        render.image_settings.color_mode = saved["color"]
        shading.light, shading.color_type = saved["light"], saved["color_type"]
        scene.display.render_aa = saved["aa"]
        scene.view_settings.view_transform = saved["view"]
        scene.view_settings.look = saved["look"]
        scene.view_settings.exposure = saved["exposure"]
        camera.data.ortho_scale = saved["ortho"]
        camera.data.shift_x, camera.data.shift_y = saved["shift"]
    os.replace(tmp, path)
    return True


def pair_placement(main, partner):
    relative = main.matrix_world.inverted() @ partner.matrix_world
    offset = relative.to_translation() * GAME_SCALE
    forward = relative.to_3x3() @ Vector((0.0, -1.0, 0.0))
    turn = math.degrees(math.atan2(forward.x, -forward.y))
    facing = (-turn) % 360.0
    return [-offset.x, offset.z, -offset.y], facing


def sample_with_action(context, armature_object, action, start, end):
    animation = armature_object.animation_data or armature_object.animation_data_create()
    saved = animation.action
    try:
        animation.action = action
        return sample(context, armature_object, start, end)
    finally:
        animation.action = saved


def export(context, armature_object, settings, directory, partner=None, author=""):
    emote_id = settings.emote_id.strip().lower()
    if not ID_PATTERN.match(emote_id):
        raise ValueError("Emote id: only lowercase Latin letters, digits, _ and -")
    action = armature_object.animation_data.action if armature_object.animation_data else None
    start, end = frame_range(context.scene, action)
    if end <= start:
        raise ValueError("The animation must be longer than one frame")
    paired = settings.paired and partner is not None
    if settings.paired and partner is None:
        raise ValueError("Paired emote: the partner rig (FalatiaPartner) is missing")
    if paired and settings.partner_action is None:
        raise ValueError("Paired emote: the partner has no action of its own")
    tracks, length = sample(context, armature_object, start, end)
    fps = context.scene.render.fps / context.scene.render.fps_base
    sounds, particles = effects.build(settings.events, start, fps)
    key = f"animation.falatia.{emote_id}"
    pair = None
    animations = {}
    if paired:
        partner_tracks, _ = sample_with_action(context, partner, settings.partner_action, start, end)
        offset, facing = pair_placement(armature_object, partner)
        wait_start = min(max(settings.wait_start, start), end)
        wait_end = min(max(settings.wait_end, wait_start), end)
        pair = {"partner": key + ".partner", "offset": _vector(offset), "facing": _number(facing),
                "wait": [_number((wait_start - start) / fps), _number((wait_end - start) / fps)]}
        animations[key + ".partner"] = build_animation(partner_tracks, length, None, "", settings.loop)
    animations = {key: build_animation(tracks, length, settings.display_name.strip() or emote_id,
                                       settings.description.strip(), settings.loop, sounds, particles, pair, author),
                  **animations}

    directory = bpy.path.abspath(directory)
    os.makedirs(directory, exist_ok=True)
    if settings.icon:
        frame = settings.icon_frame if settings.icon_frame >= 0 else context.scene.frame_current
        extra = ()
        if paired:
            (partner.animation_data or partner.animation_data_create()).action = settings.partner_action
            partner_mesh = mesh_of(partner)
            extra = (partner_mesh,) if partner_mesh is not None else ()
        render_icon(context, os.path.join(directory, emote_id + ".png"), frame, extra)
    path = os.path.join(directory, emote_id + ".animation.json")
    write_json(path, animations)
    return path
