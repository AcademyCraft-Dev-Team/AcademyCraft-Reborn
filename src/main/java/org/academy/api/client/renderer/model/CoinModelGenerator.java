package org.academy.api.client.renderer.model;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Either;
import net.minecraft.Util;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import net.minecraft.client.renderer.block.model.ItemModelGenerator.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class CoinModelGenerator {
    public static final BlockModel COIN = Util.make(BlockModel.fromString("{\"gui_light\": \"front\"}"),
            (p_119359_) -> p_119359_.name = "coin marker");
    public static final CoinModelGenerator INSTANCE = new CoinModelGenerator();

    public static final String TEXTURE_KEY_FRONT = "front";
    public static final String TEXTURE_KEY_BACK = "back";
    public static final String TEXTURE_KEY_PARTICLE = "particle";

    private static final float MIN_Z = 7.5F;
    private static final float MAX_Z = 8.5F;

    public BlockModel generateBlockModel(Function<Material, TextureAtlasSprite> spriteGetter, BlockModel templateModel) {
        Map<String, Either<Material, String>> outputTextureMap = Maps.newHashMap();
        List<BlockElement> outputElements = Lists.newArrayList();

        Material frontMaterial = templateModel.getMaterial(TEXTURE_KEY_FRONT);
        Material backMaterial = templateModel.getMaterial(TEXTURE_KEY_BACK);

        String frontTextureRef = "#" + TEXTURE_KEY_FRONT;
        String backTextureRef = "#" + TEXTURE_KEY_BACK;

        outputTextureMap.put(TEXTURE_KEY_FRONT, Either.left(frontMaterial));
        outputTextureMap.put(TEXTURE_KEY_BACK, Either.left(backMaterial));
        outputTextureMap.put(TEXTURE_KEY_PARTICLE, Either.left(frontMaterial));

        Map<Direction, BlockElementFace> coreFaces = Maps.newHashMap();
        coreFaces.put(Direction.SOUTH, new BlockElementFace(null, 0, frontTextureRef, new BlockFaceUV(new float[]{0.0F, 0.0F, 16.0F, 16.0F}, 0)));
        coreFaces.put(Direction.NORTH, new BlockElementFace(null, 0, backTextureRef, new BlockFaceUV(new float[]{16.0F, 0.0F, 0.0F, 16.0F}, 0)));

        BlockElement coreElement = new BlockElement(
                new Vector3f(0.0F, 0.0F, MIN_Z),
                new Vector3f(16.0F, 16.0F, MAX_Z),
                coreFaces,
                null,
                true
        );
        outputElements.add(coreElement);

        SpriteContents frontSpriteContents = spriteGetter.apply(frontMaterial).contents();
        outputElements.addAll(this.createSideElements(frontSpriteContents, frontTextureRef));

        return new BlockModel(
                null,
                outputElements,
                outputTextureMap,
                templateModel.hasAmbientOcclusion(),
                templateModel.getGuiLight(),
                templateModel.getTransforms(),
                templateModel.getOverrides()
        );
    }

    private List<BlockElement> createSideElements(SpriteContents sprite, String texture) {
        float f = (float)sprite.width();
        float f1 = (float)sprite.height();
        List<BlockElement> list = Lists.newArrayList();

        for(Span itemmodelgenerator$span : this.getSpans(sprite)) {
            float f2 = 0.0F;
            float f3 = 0.0F;
            float f4 = 0.0F;
            float f5 = 0.0F;
            float f6 = 0.0F;
            float f7 = 0.0F;
            float f8 = 0.0F;
            float f9 = 0.0F;
            float f10 = 16.0F / f;
            float f11 = 16.0F / f1;
            float f12 = (float)itemmodelgenerator$span.getMin();
            float f13 = (float)itemmodelgenerator$span.getMax();
            float f14 = (float)itemmodelgenerator$span.getAnchor();
            SpanFacing itemmodelgenerator$spanfacing = itemmodelgenerator$span.getFacing();
            switch (itemmodelgenerator$spanfacing) {
                case UP:
                    f6 = f12;
                    f2 = f12;
                    f4 = f7 = f13 + 1.0F;
                    f8 = f14;
                    f3 = f14;
                    f5 = f14;
                    f9 = f14 + 1.0F;
                    break;
                case DOWN:
                    f8 = f14;
                    f9 = f14 + 1.0F;
                    f6 = f12;
                    f2 = f12;
                    f4 = f7 = f13 + 1.0F;
                    f3 = f14 + 1.0F;
                    f5 = f14 + 1.0F;
                    break;
                case LEFT:
                    f6 = f14;
                    f2 = f14;
                    f4 = f14;
                    f7 = f14 + 1.0F;
                    f9 = f12;
                    f3 = f12;
                    f5 = f8 = f13 + 1.0F;
                    break;
                case RIGHT:
                    f6 = f14;
                    f7 = f14 + 1.0F;
                    f2 = f14 + 1.0F;
                    f4 = f14 + 1.0F;
                    f9 = f12;
                    f3 = f12;
                    f5 = f8 = f13 + 1.0F;
            }

            f2 *= f10;
            f4 *= f10;
            f3 *= f11;
            f5 *= f11;
            f3 = 16.0F - f3;
            f5 = 16.0F - f5;
            f6 *= f10;
            f7 *= f10;
            f8 *= f11;
            f9 *= f11;
            Map<Direction, BlockElementFace> map = Maps.newHashMap();
            map.put(itemmodelgenerator$spanfacing.getDirection(), new BlockElementFace(null, 0, texture, new BlockFaceUV(new float[]{f6, f8, f7, f9}, 0)));
            switch (itemmodelgenerator$spanfacing) {
                case UP:
                    list.add(new BlockElement(new Vector3f(f2, f3, 7.5F), new Vector3f(f4, f3, 8.5F), map, null, true));
                    break;
                case DOWN:
                    list.add(new BlockElement(new Vector3f(f2, f5, 7.5F), new Vector3f(f4, f5, 8.5F), map, null, true));
                    break;
                case LEFT:
                    list.add(new BlockElement(new Vector3f(f2, f3, 7.5F), new Vector3f(f2, f5, 8.5F), map, null, true));
                    break;
                case RIGHT:
                    list.add(new BlockElement(new Vector3f(f4, f3, 7.5F), new Vector3f(f4, f5, 8.5F), map, null, true));
            }
        }

        return list;
    }

    private List<Span> getSpans(SpriteContents pSprite) {
        int width = pSprite.width();
        int height = pSprite.height();
        List<Span> list = Lists.newArrayList();
        pSprite.getUniqueFrames().forEach((frameIndex) -> {
            for (int y = 0; y < height; ++y) {
                for (int x = 0; x < width; ++x) {
                    boolean isCurrentOpaque = !this.isTransparent(pSprite, frameIndex, x, y, width, height);
                    this.checkTransition(SpanFacing.UP, list, pSprite, frameIndex, x, y, width, height, isCurrentOpaque);
                    this.checkTransition(SpanFacing.DOWN, list, pSprite, frameIndex, x, y, width, height, isCurrentOpaque);
                    this.checkTransition(SpanFacing.LEFT, list, pSprite, frameIndex, x, y, width, height, isCurrentOpaque);
                    this.checkTransition(SpanFacing.RIGHT, list, pSprite, frameIndex, x, y, width, height, isCurrentOpaque);
                }
            }
        });
        return list;
    }

    private void checkTransition(SpanFacing pSpanFacing, List<Span> pListSpans, SpriteContents pContents, int pFrameIndex, int pPixelX, int pPixelY, int pSpriteWidth, int pSpriteHeight, boolean pIsCurrentPixelOpaque) {
        boolean isNeighborTransparent = this.isTransparent(pContents, pFrameIndex, pPixelX + pSpanFacing.getXOffset(), pPixelY + pSpanFacing.getYOffset(), pSpriteWidth, pSpriteHeight);
        if (pIsCurrentPixelOpaque && isNeighborTransparent) {
            this.createOrExpandSpan(pListSpans, pSpanFacing, pPixelX, pPixelY);
        }
    }

    private void createOrExpandSpan(List<Span> pListSpans, SpanFacing pSpanFacing, int pPixelX, int pPixelY) {
        Span spanToExtend = null;
        int anchorCoord = pSpanFacing.isHorizontal() ? pPixelY : pPixelX;
        int perpCoord = pSpanFacing.isHorizontal() ? pPixelX : pPixelY;

        for (Span currentSpan : pListSpans) {
            if (currentSpan.getFacing() == pSpanFacing && currentSpan.getAnchor() == anchorCoord) {
                if (perpCoord >= currentSpan.getMin() - 1 && perpCoord <= currentSpan.getMax() + 1) {
                    spanToExtend = currentSpan;
                    break;
                }
            }
        }

        if (spanToExtend == null) {
            pListSpans.add(new Span(pSpanFacing, perpCoord, anchorCoord));
        } else {
            spanToExtend.expand(perpCoord);
        }
    }

    private boolean isTransparent(SpriteContents pSprite, int pFrameIndex, int pPixelX, int pPixelY, int pSpriteWidth, int pSpriteHeight) {
        if (pPixelX < 0 || pPixelY < 0 || pPixelX >= pSpriteWidth || pPixelY >= pSpriteHeight) {
            return true;
        }
        return pSprite.isTransparent(pFrameIndex, pPixelX, pPixelY);
    }
}