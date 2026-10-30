import math

import bmesh
import bpy
from mathutils import Matrix, Vector

PX = 1.0 / 16.0

ARMATURE = "FalatiaPlayer"
MESH = "FalatiaPlayerMesh"
CAMERA = "FalatiaIconCamera"
MATERIAL = "FalatiaSkin"
SKIN_IMAGE = "FalatiaSkin"
PARTNER = "FalatiaPartner"
PARTNER_MESH = "FalatiaPartnerMesh"
PARTNER_MATERIAL = "FalatiaPartnerSkin"
PARTNER_SKIN_IMAGE = "FalatiaPartnerSkin"

MAIN_ROLE = {"armature": ARMATURE, "mesh": MESH, "material": MATERIAL, "image": SKIN_IMAGE,
             "shirt": (0.24, 0.55, 0.6), "pants": (0.2, 0.22, 0.45)}
PARTNER_ROLE = {"armature": PARTNER, "mesh": PARTNER_MESH, "material": PARTNER_MATERIAL, "image": PARTNER_SKIN_IMAGE,
                "shirt": (0.72, 0.36, 0.52), "pants": (0.24, 0.24, 0.26)}
PARTNER_LOCATION = (0.0, -1.0, 0.0)

PIVOTS = {
    "root": (0.0, 24.0, 0.0),
    "body": (0.0, 0.0, 0.0),
    "head": (0.0, 0.0, 0.0),
    "right_arm": (-5.0, 2.0, 0.0),
    "left_arm": (5.0, 2.0, 0.0),
    "right_leg": (-1.9, 12.0, 0.0),
    "left_leg": (1.9, 12.0, 0.0),
}

BONE_NAMES = ("root", "body", "head", "right_arm", "left_arm", "right_leg", "left_leg")

BENDS = {
    "body": "chest",
    "right_arm": "right_forearm",
    "left_arm": "left_forearm",
    "right_leg": "right_shin",
    "left_leg": "left_shin",
}

JOINT_Y = 6.0
LEG_JOINT_Y = 18.0


def arm_joint_x(side, slim):
    return side * (5.5 if slim else 6.0)


def bones(bends, slim):
    if not bends:
        return (
            ("root", None, (0.0, 24.0, 0.0), (0.0, 24.0, -8.0)),
            ("body", "root", (0.0, 12.0, 0.0), (0.0, 0.0, 0.0)),
            ("head", "body", (0.0, 0.0, 0.0), (0.0, -8.0, 0.0)),
            ("right_arm", "body", (-5.0, 2.0, 0.0), (-5.0, 12.0, 0.0)),
            ("left_arm", "body", (5.0, 2.0, 0.0), (5.0, 12.0, 0.0)),
            ("right_leg", "root", (-1.9, 12.0, 0.0), (-1.9, 24.0, 0.0)),
            ("left_leg", "root", (1.9, 12.0, 0.0), (1.9, 24.0, 0.0)),
        )
    right_x = arm_joint_x(-1.0, slim)
    left_x = arm_joint_x(1.0, slim)
    return (
        ("root", None, (0.0, 24.0, 0.0), (0.0, 24.0, -8.0)),
        ("body", "root", (0.0, 12.0, 0.0), (0.0, JOINT_Y, 0.0)),
        ("chest", "body", (0.0, JOINT_Y, 0.0), (0.0, 0.0, 0.0)),
        ("head", "chest", (0.0, 0.0, 0.0), (0.0, -8.0, 0.0)),
        ("right_arm", "chest", (-5.0, 2.0, 0.0), (-5.0, JOINT_Y, 0.0)),
        ("right_forearm", "right_arm", (right_x, JOINT_Y, 0.0), (right_x, 12.0, 0.0)),
        ("left_arm", "chest", (5.0, 2.0, 0.0), (5.0, JOINT_Y, 0.0)),
        ("left_forearm", "left_arm", (left_x, JOINT_Y, 0.0), (left_x, 12.0, 0.0)),
        ("right_leg", "root", (-1.9, 12.0, 0.0), (-1.9, LEG_JOINT_Y, 0.0)),
        ("right_shin", "right_leg", (-1.9, LEG_JOINT_Y, 0.0), (-1.9, 24.0, 0.0)),
        ("left_leg", "root", (1.9, 12.0, 0.0), (1.9, LEG_JOINT_Y, 0.0)),
        ("left_shin", "left_leg", (1.9, LEG_JOINT_Y, 0.0), (1.9, 24.0, 0.0)),
    )


def to_blender(x, y, z):
    return Vector((x * PX, z * PX, (24.0 - y) * PX))


def cubes(slim):
    arm = 3.0 if slim else 4.0
    right_arm_x = -2.0 if slim else -3.0
    result = []
    for bone, base, outer, corner, size in (
        ("head", (0, 0), (32, 0), (-4.0, -8.0, -4.0), (8.0, 8.0, 8.0)),
        ("body", (16, 16), (16, 32), (-4.0, 0.0, -2.0), (8.0, 12.0, 4.0)),
        ("right_arm", (40, 16), (40, 32), (right_arm_x, -2.0, -2.0), (arm, 12.0, 4.0)),
        ("left_arm", (32, 48), (48, 48), (-1.0, -2.0, -2.0), (arm, 12.0, 4.0)),
        ("right_leg", (0, 16), (0, 32), (-2.0, 0.0, -2.0), (4.0, 12.0, 4.0)),
        ("left_leg", (16, 48), (0, 48), (-2.0, 0.0, -2.0), (4.0, 12.0, 4.0)),
    ):
        result.append((bone, base, corner, size, 0.0))
        result.append((bone, outer, corner, size, 0.5 if bone == "head" else 0.25))
    return result


def _add_cube(bm, uv_layer, deform, group, pivot, tex, corner, size, grow):
    u, v = tex
    w, h, d = size
    min_x, min_y, min_z = (pivot[i] + corner[i] - grow for i in range(3))
    max_x, max_y, max_z = (pivot[i] + corner[i] + size[i] + grow for i in range(3))

    def vert(x, y, z):
        vertex = bm.verts.new(to_blender(x, y, z))
        vertex[deform][group] = 1.0
        return vertex

    t0 = vert(min_x, min_y, min_z)
    t1 = vert(max_x, min_y, min_z)
    t2 = vert(max_x, max_y, min_z)
    t3 = vert(min_x, max_y, min_z)
    l0 = vert(min_x, min_y, max_z)
    l1 = vert(max_x, min_y, max_z)
    l2 = vert(max_x, max_y, max_z)
    l3 = vert(min_x, max_y, max_z)
    u0, u1, u2, u22 = u, u + d, u + d + w, u + d + w + w
    u3, u4 = u + d + w + d, u + d + w + d + w
    v0, v1, v2 = v, v + d, v + d + h
    for verts, ua, va, ub, vb in (
        ((l1, l0, t0, t1), u1, v0, u2, v1),
        ((t2, t3, l3, l2), u2, v1, u22, v0),
        ((t0, l0, l3, t3), u0, v1, u1, v2),
        ((t1, t0, t3, t2), u1, v1, u2, v2),
        ((l1, t1, t2, l2), u2, v1, u3, v2),
        ((l0, l1, l2, l3), u3, v1, u4, v2),
    ):
        face = bm.faces.new(verts)
        for loop, (tu, tv) in zip(face.loops, ((ub, va), (ua, va), (ua, vb), (ub, vb))):
            loop[uv_layer].uv = (tu / 64.0, 1.0 - tv / 64.0)


def _placeholder_skin(role):
    image = bpy.data.images.new(role["image"], 64, 64, alpha=True)
    pixels = [0.0] * (64 * 64 * 4)

    def paint(x0, y0, x1, y1, color):
        for y in range(y0, y1):
            for x in range(x0, x1):
                i = ((63 - y) * 64 + x) * 4
                pixels[i:i + 4] = (*color, 1.0)

    skin = (0.78, 0.58, 0.45)
    shirt = role["shirt"]
    pants = role["pants"]
    paint(0, 0, 32, 16, skin)
    paint(16, 16, 40, 32, shirt)
    paint(40, 16, 56, 32, skin)
    paint(32, 48, 48, 64, skin)
    paint(0, 16, 16, 32, pants)
    paint(16, 48, 32, 64, pants)
    paint(9, 12, 11, 13, (0.15, 0.1, 0.25))
    paint(13, 12, 15, 13, (0.15, 0.1, 0.25))
    image.pixels.foreach_set(pixels)
    image.pack()
    return image


def _skin_material(skin_path, role=MAIN_ROLE):
    image = None
    if skin_path:
        image = bpy.data.images.load(bpy.path.abspath(skin_path), check_existing=True)
    if image is None:
        image = bpy.data.images.get(role["image"]) or _placeholder_skin(role)
    material = bpy.data.materials.get(role["material"]) or bpy.data.materials.new(role["material"])
    if not material.node_tree:
        material.use_nodes = True
    nodes = material.node_tree.nodes
    links = material.node_tree.links
    nodes.clear()
    output = nodes.new("ShaderNodeOutputMaterial")
    bsdf = nodes.new("ShaderNodeBsdfPrincipled")
    texture = nodes.new("ShaderNodeTexImage")
    texture.image = image
    texture.interpolation = "Closest"
    bsdf.inputs["Roughness"].default_value = 1.0
    if "Specular IOR Level" in bsdf.inputs:
        bsdf.inputs["Specular IOR Level"].default_value = 0.0
    links.new(texture.outputs["Color"], bsdf.inputs["Base Color"])
    clip = nodes.new("ShaderNodeMath")
    clip.operation = "GREATER_THAN"
    clip.inputs[1].default_value = 0.5
    links.new(texture.outputs["Alpha"], clip.inputs[0])
    links.new(clip.outputs["Value"], bsdf.inputs["Alpha"])
    links.new(bsdf.outputs["BSDF"], output.inputs["Surface"])
    texture.location = (-500, 0)
    clip.location = (-200, -200)
    output.location = (300, 0)
    material.surface_render_method = "DITHERED"
    material.blend_method = "CLIP"
    material.alpha_threshold = 0.5
    return material


def _split_cube(bm, deform, before, upper_group, lower_group, joint_z):
    faces = [face for face in bm.faces if face not in before]
    geometry = list({vertex for face in faces for vertex in face.verts})
    geometry += list({edge for face in faces for edge in face.edges}) + faces
    bmesh.ops.bisect_plane(bm, geom=geometry, plane_co=(0.0, 0.0, joint_z), plane_no=(0.0, 0.0, 1.0))
    verts = {vertex for face in bm.faces if face not in before for vertex in face.verts}
    for vertex in verts:
        weights = vertex[deform]
        weights.clear()
        if abs(vertex.co.z - joint_z) < 1e-5:
            weights[upper_group] = 0.5
            weights[lower_group] = 0.5
        else:
            weights[upper_group if vertex.co.z > joint_z else lower_group] = 1.0


def _build_mesh(armature_object, names, slim, skin_path, bends, role=MAIN_ROLE):
    mesh = bpy.data.meshes.new(role["mesh"])
    bm = bmesh.new()
    uv_layer = bm.loops.layers.uv.new("UVMap")
    deform = bm.verts.layers.deform.verify()
    groups = {name: i for i, name in enumerate(names)}
    for bone, tex, corner, size, grow in cubes(slim):
        before = set(bm.faces)
        _add_cube(bm, uv_layer, deform, groups[bone], PIVOTS[bone], tex, corner, size, grow)
        if bends and bone in BENDS:
            joint_y = PIVOTS[bone][1] + corner[1] + size[1] / 2.0
            upper, lower = (BENDS[bone], bone) if bone == "body" else (bone, BENDS[bone])
            _split_cube(bm, deform, before, groups[upper], groups[lower], to_blender(0.0, joint_y, 0.0).z)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(mesh)
    bm.free()
    mesh.materials.append(_skin_material(skin_path, role))

    obj = bpy.data.objects.new(role["mesh"], mesh)
    for name in names:
        obj.vertex_groups.new(name=name)
    obj.parent = armature_object
    modifier = obj.modifiers.new("Armature", "ARMATURE")
    modifier.object = armature_object
    modifier.use_deform_preserve_volume = bends
    obj.hide_select = True
    return obj


def _build_camera(collection, armature_object):
    data = bpy.data.cameras.new(CAMERA)
    data.type = "ORTHO"
    data.ortho_scale = 2.9
    camera = bpy.data.objects.new(CAMERA, data)
    collection.objects.link(camera)
    camera.location = (-2.6, -4.2, 2.3)
    constraint = camera.constraints.new("TRACK_TO")
    constraint.track_axis = "TRACK_NEGATIVE_Z"
    constraint.up_axis = "UP_Y"
    target = bpy.data.objects.new(CAMERA + "Target", None)
    target.location = (0.0, 0.0, 1.15)
    target.empty_display_size = 0.2
    target.parent = armature_object
    collection.objects.link(target)
    constraint.target = target
    return camera


def create_player(context, slim=False, skin_path="", bends=False, main=None):
    role = PARTNER_ROLE if main is not None else MAIN_ROLE
    collection = context.collection
    armature = bpy.data.armatures.new(role["armature"])
    armature.display_type = "OCTAHEDRAL"
    obj = bpy.data.objects.new(role["armature"], armature)
    obj.show_in_front = True
    collection.objects.link(obj)
    if main is not None:
        obj["falatia_partner"] = True
        main["falatia_partner_name"] = obj.name
        obj.matrix_world = main.matrix_world @ Matrix.Translation(PARTNER_LOCATION) @ Matrix.Rotation(math.pi, 4, "Z")

    for other in context.selected_objects:
        other.select_set(False)
    context.view_layer.objects.active = obj
    obj.select_set(True)
    layout = bones(bends, slim)
    bpy.ops.object.mode_set(mode="EDIT")
    for name, parent, head, tail in layout:
        bone = armature.edit_bones.new(name)
        bone.head = to_blender(*head)
        bone.tail = to_blender(*tail)
        bone.roll = 0.0
        if parent:
            bone.parent = armature.edit_bones[parent]
            bone.use_connect = False
    bpy.ops.object.mode_set(mode="OBJECT")
    bend_bones = set(BENDS.values())
    for pose_bone in obj.pose.bones:
        pose_bone.rotation_mode = "XYZ"
        if pose_bone.name in bend_bones:
            pose_bone.lock_location = (True, True, True)
            pose_bone.lock_rotation[1] = True

    mesh = _build_mesh(obj, [bone[0] for bone in layout], slim, skin_path, bends, role)
    collection.objects.link(mesh)
    obj["falatia_mesh"] = mesh.name
    if main is None:
        camera = _build_camera(collection, obj)
        if context.scene.camera is None:
            context.scene.camera = camera
    return obj


def has_bends(armature_object):
    return any(child in armature_object.data.bones for child in BENDS.values())


def is_slim(armature_object):
    bone = armature_object.data.bones.get("right_forearm")
    return bone is not None and abs(bone.head_local.x) < 5.75 * PX


def mesh_of(armature_object):
    name = armature_object.get("falatia_mesh")
    if name:
        return bpy.data.objects.get(name)
    return next((child for child in armature_object.children if child.type == "MESH"), None)


def set_skin(skin_path):
    _skin_material(skin_path)
