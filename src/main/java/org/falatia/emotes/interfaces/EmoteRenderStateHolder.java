package org.falatia.emotes.interfaces;

import org.falatia.emotes.emotestypes.EmoteNearby;
import org.falatia.emotes.EmotePose;

public interface EmoteRenderStateHolder {

    EmotePose falatia$emotePose();

    EmoteNearby.Label falatia$emoteLabel();
}
