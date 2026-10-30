import json

SOUNDS = (
    ("minecraft:entity.generic.drink", "Drink"),
    ("minecraft:entity.generic.eat", "Eat"),
    ("minecraft:entity.player.burp", "Burp"),
    ("minecraft:entity.player.levelup", "Level up"),
    ("minecraft:entity.experience_orb.pickup", "Experience"),
    ("minecraft:entity.item.pickup", "Item pickup"),
    ("minecraft:entity.villager.yes", "Villager: yes"),
    ("minecraft:entity.villager.no", "Villager: no"),
    ("minecraft:entity.villager.celebrate", "Villager celebrates"),
    ("minecraft:entity.witch.celebrate", "Witch laughs"),
    ("minecraft:entity.cat.ambient", "Cat"),
    ("minecraft:entity.chicken.egg", "Chicken lays an egg"),
    ("minecraft:entity.player.attack.sweep", "Sweep"),
    ("minecraft:entity.player.hurt", "Hurt"),
    ("minecraft:entity.firework_rocket.launch", "Firework: launch"),
    ("minecraft:entity.firework_rocket.blast", "Firework: blast"),
    ("minecraft:entity.firework_rocket.twinkle", "Firework: twinkle"),
    ("minecraft:block.note_block.harp", "Note block: harp"),
    ("minecraft:block.note_block.bell", "Note block: bell"),
    ("minecraft:block.note_block.pling", "Note block: pling"),
    ("minecraft:block.amethyst_block.chime", "Amethyst chime"),
    ("minecraft:block.bell.use", "Bell"),
    ("minecraft:ui.toast.challenge_complete", "Challenge complete"),
    ("minecraft:entity.generic.explode", "Explosion"),
)

PARTICLES = (
    ("minecraft:heart", "Heart", "simple"),
    ("minecraft:note", "Note", "simple"),
    ("minecraft:happy_villager", "Green sparkles", "simple"),
    ("minecraft:angry_villager", "Anger", "simple"),
    ("minecraft:end_rod", "Sparkles", "simple"),
    ("minecraft:enchant", "Enchant (glyphs)", "simple"),
    ("minecraft:poof", "Poof", "simple"),
    ("minecraft:smoke", "Smoke", "simple"),
    ("minecraft:large_smoke", "Large smoke", "simple"),
    ("minecraft:cloud", "Cloud", "simple"),
    ("minecraft:flame", "Flame", "simple"),
    ("minecraft:soul_fire_flame", "Soul fire flame", "simple"),
    ("minecraft:splash", "Splash", "simple"),
    ("minecraft:falling_water", "Drips (tears)", "simple"),
    ("minecraft:glow", "Glow", "simple"),
    ("minecraft:electric_spark", "Electric spark", "simple"),
    ("minecraft:crit", "Critical hit", "simple"),
    ("minecraft:enchanted_hit", "Enchanted hit", "simple"),
    ("minecraft:witch", "Witch magic", "simple"),
    ("minecraft:totem_of_undying", "Totem", "simple"),
    ("minecraft:firework", "Firework", "simple"),
    ("minecraft:bubble_pop", "Bubbles", "simple"),
    ("minecraft:snowflake", "Snowflakes", "simple"),
    ("minecraft:cherry_leaves", "Cherry petals", "simple"),
    ("minecraft:dust", "Dust (color)", "dust"),
    ("minecraft:dust_color_transition", "Dust (color transition)", "transition"),
    ("minecraft:entity_effect", "Potion effect (color)", "argb"),
    ("minecraft:tinted_leaves", "Tinted leaves (color)", "argb"),
    ("minecraft:flash", "Flash (color)", "argb"),
    ("minecraft:effect", "Spell (color)", "spell"),
    ("minecraft:instant_effect", "Instant spell (color)", "spell"),
    ("minecraft:block", "Block debris", "block"),
    ("minecraft:falling_dust", "Falling block dust", "block"),
    ("minecraft:item", "Item debris", "item"),
)

PRESETS = (
    ("hearts", "Hearts", "Hearts above the head"),
    ("notes", "Notes", "Notes above the head"),
    ("happy", "Joy", "Green sparkles around"),
    ("angry", "Anger", "Angry clouds above the head"),
    ("sparkles", "Sparkles", "Sparkles around"),
    ("magic", "Magic", "Enchanting glyphs around"),
    ("smoke", "Poof", "Poof under the feet"),
    ("splash", "Splash", "Splash above the head"),
    ("tears", "Tears", "Drips from the head"),
    ("fire", "Flame", "Flames around"),
    ("confetti", "Confetti", "Multicolored dust above the head"),
)

LOCATORS = (
    ("body", "Body", "Middle of the body"),
    ("head", "Head", "Head (eyes)"),
    ("right_hand", "Right hand", "Right hand (in the emote pose)"),
    ("left_hand", "Left hand", "Left hand (in the emote pose)"),
    ("right_foot", "Right foot", "Right foot"),
    ("left_foot", "Left foot", "Left foot"),
    ("feet", "Feet", "Under the player's feet"),
)

CUSTOM = "CUSTOM"

KIND = {particle_id: kind for particle_id, _, kind in PARTICLES}


def sound_items():
    return [(sound_id, label, sound_id) for sound_id, label in SOUNDS] + [(CUSTOM, "Custom id…", "Any game or resource pack sound")]


def particle_items():
    return [(particle_id, label, particle_id) for particle_id, label, _ in PARTICLES] + [
        (CUSTOM, "Custom particle…", "Any game particle by id, options as JSON like in the game")]


def preset_items():
    return list(PRESETS)


def locator_items(with_default=False):
    items = [(key, label, tip) for key, label, tip in LOCATORS]
    if with_default:
        items = [("DEFAULT", "Preset default", "Where the preset itself spawns them")] + items
    return items


def particle_kind(event):
    return "custom" if event.particle == CUSTOM else KIND.get(event.particle, "simple")


def _color(value):
    return [round(component, 3) for component in value[:3]]


def particle_options(event):
    kind = particle_kind(event)
    if kind == "dust":
        return {"color": _color(event.color), "scale": round(event.scale, 3)}
    if kind == "transition":
        return {"from_color": _color(event.color), "to_color": _color(event.color2), "scale": round(event.scale, 3)}
    if kind == "argb":
        return {"color": _color(event.color) + [1.0]}
    if kind == "spell":
        return {"color": _color(event.color), "power": round(event.power, 3)}
    if kind == "block":
        return {"block_state": event.block.strip() or "minecraft:stone"}
    if kind == "item":
        return {"item": event.item.strip() or "minecraft:diamond"}
    if kind == "custom" and event.particle_options.strip():
        try:
            options = json.loads(event.particle_options)
        except json.JSONDecodeError as error:
            raise ValueError(f"Particle options are not valid JSON: {error}") from error
        if not isinstance(options, dict):
            raise ValueError("Particle options must be a JSON object {...}")
        return options
    return None


def _vector(value):
    return [round(component, 3) for component in value]


def build(events, start, fps):
    sounds = {}
    particles = {}
    for event in sorted(events, key=lambda e: e.frame):
        time = f"{round(max(0, event.frame - start) / fps, 4):g}"
        if event.kind == "SOUND":
            sound_id = event.sound_custom.strip() if event.sound == CUSTOM else event.sound
            if not sound_id:
                continue
            sounds.setdefault(time, []).append(
                {"effect": sound_id, "volume": round(event.volume, 3), "pitch": round(event.pitch, 3)})
        elif event.kind == "PRESET":
            entry = {"effect": "preset:" + event.preset}
            if event.locator_preset != "DEFAULT":
                entry["locator"] = event.locator_preset
            particles.setdefault(time, []).append(entry)
        else:
            particle_id = event.particle_custom.strip() if event.particle == CUSTOM else event.particle
            if not particle_id:
                continue
            entry = {"effect": particle_id}
            options = particle_options(event)
            if options:
                entry["options"] = options
            entry["locator"] = event.locator
            entry["count"] = event.count
            entry["spread"] = _vector(event.spread)
            if event.speed:
                entry["speed"] = round(event.speed, 3)
            if any(event.offset):
                entry["offset"] = _vector(event.offset)
            particles.setdefault(time, []).append(entry)
    return _single(sounds), _single(particles)


def _single(table):
    return {time: items[0] if len(items) == 1 else items for time, items in table.items()}


def summary(event):
    if event.kind == "SOUND":
        if event.sound == CUSTOM:
            return event.sound_custom or "sound?"
        return dict(SOUNDS).get(event.sound, event.sound)
    if event.kind == "PRESET":
        return "Preset: " + {key: label for key, label, _ in PRESETS}.get(event.preset, event.preset)
    if event.particle == CUSTOM:
        return event.particle_custom or "particle?"
    return {pid: label for pid, label, _ in PARTICLES}.get(event.particle, event.particle)
