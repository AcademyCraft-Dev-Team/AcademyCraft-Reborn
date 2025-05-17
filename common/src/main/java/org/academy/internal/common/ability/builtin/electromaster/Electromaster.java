package org.academy.internal.common.ability.builtin.electromaster;

import org.academy.api.client.renderer.RendererManager;
import org.academy.api.common.ability.AbilityCategory;
import org.academy.internal.client.renderer.effect.ArcEffectRenderer;
import org.academy.internal.common.ability.builtin.AbilityCategoryNames;
import org.academy.internal.common.ability.builtin.electromaster.skills.ArcGenerate;
import org.academy.internal.common.ability.builtin.electromaster.skills.MagnetManipulation;
import org.academy.internal.common.ability.builtin.electromaster.skills.Railgun;

public final class Electromaster extends AbilityCategory {
    public static final AbilityCategory INSTANCE = new Electromaster();

    private Electromaster() {
        super(AbilityCategoryNames.ELECTROMASTER);
        this.skillList.add(Railgun.INSTANCE);
        this.skillList.add(ArcGenerate.INSTANCE);
        this.skillList.add(MagnetManipulation.INSTANCE);
    }

    @Override
    public void initClient() {
        RendererManager.registerEffectRenderer(ArcEffectRenderer.INSTANCE);
    }
}