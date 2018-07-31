package com.smithsmodding.smithscore.client.model.unbaked;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.PerspectiveMapWrapper;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import javax.vecmath.Vector4f;
import java.util.*;

/**
 * Copy of Forges ItemLayerModel Class of Minecraft 1.9.
 * Removed the Final though.
 */
public class ItemLayerModel implements IModel
{
    public static final ItemLayerModel INSTANCE = new ItemLayerModel(ImmutableList.of());

    private final ImmutableList<ResourceLocation> textures;
    private final ItemOverrideList                overrides;

    public ItemLayerModel(@Nonnull ImmutableList<ResourceLocation> textures)
    {
        this(textures, ItemOverrideList.NONE);
    }

    public ItemLayerModel(@Nonnull ImmutableList<ResourceLocation> textures, @Nonnull ItemOverrideList overrides)
    {
        this.textures = textures;
        this.overrides = overrides;
    }

    public ItemLayerModel(@Nonnull ModelBlock model)
    {
        this(getTextures(model), model.createOverrides());
    }

    @Nonnull
    private static ImmutableList<ResourceLocation> getTextures(@Nonnull ModelBlock model)
    {
        ImmutableList.Builder<ResourceLocation> builder = ImmutableList.builder();
        for (int i = 0; model.isTexturePresent("layer" + i); i++)
        {
            builder.add(new ResourceLocation(model.resolveTextureName("layer" + i)));
        }
        return builder.build();
    }

    @Nonnull
    public static ImmutableList<BakedQuad> getQuadsForSprite(
                                                              int tint,
                                                              @Nonnull TextureAtlasSprite sprite,
                                                              @Nonnull VertexFormat format,
                                                              @Nonnull Optional<TRSRTransformation> transform)
    {
        ImmutableList.Builder<BakedQuad> builder = ImmutableList.builder();

        int uMax = sprite.getIconWidth();
        int vMax = sprite.getIconHeight();

        BitSet faces = new BitSet((uMax + 1) * (vMax + 1) * 4);
        for (int f = 0; f < sprite.getFrameCount(); f++)
        {
            int[] pixels = sprite.getFrameTextureData(f)[0];
            boolean ptu;
            boolean[] ptv = new boolean[uMax];
            Arrays.fill(ptv, true);
            for (int v = 0; v < vMax; v++)
            {
                ptu = true;
                for (int u = 0; u < uMax; u++)
                {
                    boolean t = isTransparent(pixels, uMax, vMax, u, v);
                    if (ptu && !t) // left - transparent, right - opaque
                    {
                        addSideQuad(builder, faces, format, transform, EnumFacing.WEST, tint, sprite, uMax, vMax, u, v);
                    }
                    if (!ptu && t) // left - opaque, right - transparent
                    {
                        addSideQuad(builder, faces, format, transform, EnumFacing.EAST, tint, sprite, uMax, vMax, u, v);
                    }
                    if (ptv[u] && !t) // up - transparent, down - opaque
                    {
                        addSideQuad(builder, faces, format, transform, EnumFacing.UP, tint, sprite, uMax, vMax, u, v);
                    }
                    if (!ptv[u] && t) // up - opaque, down - transparent
                    {
                        addSideQuad(builder, faces, format, transform, EnumFacing.DOWN, tint, sprite, uMax, vMax, u, v);
                    }
                    ptu = t;
                    ptv[u] = t;
                }
                if (!ptu) // last - opaque
                {
                    addSideQuad(builder, faces, format, transform, EnumFacing.EAST, tint, sprite, uMax, vMax, uMax, v);
                }
            }
            // last line
            for (int u = 0; u < uMax; u++)
            {
                if (!ptv[u])
                {
                    addSideQuad(builder, faces, format, transform, EnumFacing.DOWN, tint, sprite, uMax, vMax, u, vMax);
                }
            }
        }
        // front
        builder.add(buildQuad(format, transform, EnumFacing.NORTH, sprite, tint,
          0, 0, 7.5f / 16f, sprite.getMinU(), sprite.getMaxV(),
          0, 1, 7.5f / 16f, sprite.getMinU(), sprite.getMinV(),
          1, 1, 7.5f / 16f, sprite.getMaxU(), sprite.getMinV(),
          1, 0, 7.5f / 16f, sprite.getMaxU(), sprite.getMaxV()
        ));
        // back
        builder.add(buildQuad(format, transform, EnumFacing.SOUTH, sprite, tint,
          0, 0, 8.5f / 16f, sprite.getMinU(), sprite.getMaxV(),
          1, 0, 8.5f / 16f, sprite.getMaxU(), sprite.getMaxV(),
          1, 1, 8.5f / 16f, sprite.getMaxU(), sprite.getMinV(),
          0, 1, 8.5f / 16f, sprite.getMinU(), sprite.getMinV()
        ));
        return builder.build();
    }

    private static boolean isTransparent(int[] pixels, int uMax, int vMax, int u, int v)
    {
        return (pixels[u + (vMax - 1 - v) * uMax] >> 24 & 0xFF) == 0;
    }

    private static void addSideQuad(
                                     @Nonnull ImmutableList.Builder<BakedQuad> builder,
                                     @Nonnull BitSet faces,
                                     @Nonnull VertexFormat format,
                                     @Nonnull Optional<TRSRTransformation> transform,
                                     @Nonnull EnumFacing side,
                                     int tint,
                                     @Nonnull TextureAtlasSprite sprite,
                                     int uMax,
                                     int vMax,
                                     int u,
                                     int v)
    {
        int si = side.ordinal();
        if (si > 4)
        {
            si -= 2;
        }
        int index = (vMax + 1) * ((uMax + 1) * si + u) + v;
        if (!faces.get(index))
        {
            faces.set(index);
            builder.add(buildSideQuad(format, transform, side, tint, sprite, u, v));
        }
    }

    @Nonnull
    private static BakedQuad buildSideQuad(
                                            @Nonnull VertexFormat format,
                                            @Nonnull Optional<TRSRTransformation> transform,
                                            @Nonnull EnumFacing side,
                                            int tint,
                                            @Nonnull TextureAtlasSprite sprite,
                                            int u,
                                            int v)
    {
        final float eps0 = 30e-5f;
        final float eps1 = 45e-5f;
        final float eps2 = .5f;
        final float eps3 = .5f;
        float x0 = (float) u / sprite.getIconWidth();
        float y0 = (float) v / sprite.getIconHeight();
        float x1 = x0, y1 = y0;
        float z1 = 7.5f / 16f - eps1, z2 = 8.5f / 16f + eps1;
        switch (side)
        {
            case WEST:
                z1 = 8.5f / 16f + eps1;
                z2 = 7.5f / 16f - eps1;
            case EAST:
                y1 = (v + 1f) / sprite.getIconHeight();
                break;
            case DOWN:
                z1 = 8.5f / 16f + eps1;
                z2 = 7.5f / 16f - eps1;
            case UP:
                x1 = (u + 1f) / sprite.getIconWidth();
                break;
            default:
                throw new IllegalArgumentException("can't handle z-oriented side");
        }
        float u0 = 16f * (x0 - side.getDirectionVec().getX() * eps3 / sprite.getIconWidth());
        float u1 = 16f * (x1 - side.getDirectionVec().getX() * eps3 / sprite.getIconWidth());
        float v0 = 16f * (1f - y0 - side.getDirectionVec().getY() * eps3 / sprite.getIconHeight());
        float v1 = 16f * (1f - y1 - side.getDirectionVec().getY() * eps3 / sprite.getIconHeight());
        switch (side)
        {
            case WEST:
            case EAST:
                y0 -= eps1;
                y1 += eps1;
                v0 -= eps2 / sprite.getIconHeight();
                v1 += eps2 / sprite.getIconHeight();
                break;
            case DOWN:
            case UP:
                x0 -= eps1;
                x1 += eps1;
                u0 += eps2 / sprite.getIconWidth();
                u1 -= eps2 / sprite.getIconWidth();
                break;
            default:
                throw new IllegalArgumentException("can't handle z-oriented side");
        }
        switch (side)
        {
            case WEST:
                x0 += eps0;
                x1 += eps0;
                break;
            case EAST:
                x0 -= eps0;
                x1 -= eps0;
                break;
            case DOWN:
                y0 -= eps0;
                y1 -= eps0;
                break;
            case UP:
                y0 += eps0;
                y1 += eps0;
                break;
            default:
                throw new IllegalArgumentException("can't handle z-oriented side");
        }
        return buildQuad(
          format, transform, side.getOpposite(), sprite, tint, // getOpposite is related either to the swapping of V direction, or something else
          x0, y0, z1, sprite.getInterpolatedU(u0), sprite.getInterpolatedV(v0),
          x1, y1, z1, sprite.getInterpolatedU(u1), sprite.getInterpolatedV(v1),
          x1, y1, z2, sprite.getInterpolatedU(u1), sprite.getInterpolatedV(v1),
          x0, y0, z2, sprite.getInterpolatedU(u0), sprite.getInterpolatedV(v0)
        );
    }

    @Nonnull
    private static final BakedQuad buildQuad(
                                              @Nonnull VertexFormat format,
                                              @Nonnull Optional<TRSRTransformation> transform,
                                              @Nonnull EnumFacing side,
                                              TextureAtlasSprite sprite,
                                              int tint,
                                              float x0,
                                              float y0,
                                              float z0,
                                              float u0,
                                              float v0,
                                              float x1,
                                              float y1,
                                              float z1,
                                              float u1,
                                              float v1,
                                              float x2,
                                              float y2,
                                              float z2,
                                              float u2,
                                              float v2,
                                              float x3,
                                              float y3,
                                              float z3,
                                              float u3,
                                              float v3)
    {
        UnpackedBakedQuad.Builder builder = new UnpackedBakedQuad.Builder(format);
        builder.setQuadTint(tint);
        builder.setQuadOrientation(side);
        builder.setTexture(sprite);
        putVertex(builder, format, transform, side, x0, y0, z0, u0, v0);
        putVertex(builder, format, transform, side, x1, y1, z1, u1, v1);
        putVertex(builder, format, transform, side, x2, y2, z2, u2, v2);
        putVertex(builder, format, transform, side, x3, y3, z3, u3, v3);
        return builder.build();
    }

    private static void putVertex(
                                   @Nonnull UnpackedBakedQuad.Builder builder,
                                   @Nonnull VertexFormat format,
                                   @Nonnull Optional<TRSRTransformation> transform,
                                   @Nonnull EnumFacing side,
                                   float x,
                                   float y,
                                   float z,
                                   float u,
                                   float v)
    {
        Vector4f vec = new Vector4f();
        for (int e = 0; e < format.getElementCount(); e++)
        {
            switch (format.getElement(e).getUsage())
            {
                case POSITION:
                    if (transform.isPresent())
                    {
                        vec.x = x;
                        vec.y = y;
                        vec.z = z;
                        vec.w = 1;
                        transform.get().getMatrix().transform(vec);
                        builder.put(e, vec.x, vec.y, vec.z, vec.w);
                    }
                    else
                    {
                        builder.put(e, x, y, z, 1);
                    }
                    break;
                case COLOR:
                    builder.put(e, 1f, 1f, 1f, 1f);
                    break;
                case UV:
                    if (format.getElement(e).getIndex() == 0)
                    {
                        builder.put(e, u, v, 0f, 1f);
                        break;
                    }
                case NORMAL:
                    builder.put(e, (float) side.getFrontOffsetX(), (float) side.getFrontOffsetY(), (float) side.getFrontOffsetZ(), 0f);
                    break;
                default:
                    builder.put(e);
                    break;
            }
        }
    }

    @Nonnull
    public Collection<ResourceLocation> getDependencies()
    {
        return ImmutableList.of();
    }

    @Nonnull
    public Collection<ResourceLocation> getTextures()
    {
        return textures;
    }

    @Override
    public IBakedModel bake(
      final IModelState state, final VertexFormat format, final java.util.function.Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter)
    {
        ImmutableList.Builder<BakedQuad> builder = ImmutableList.builder();
        Optional<TRSRTransformation> transform = state.apply(Optional.empty());
        for (int i = 0; i < textures.size(); i++)
        {
            TextureAtlasSprite sprite = bakedTextureGetter.apply(textures.get(i));
            builder.addAll(getQuadsForSprite(i, sprite, format, transform));
        }
        TextureAtlasSprite particle = bakedTextureGetter.apply(textures.isEmpty() ? new ResourceLocation("missingno") : textures.get(0));
        ImmutableMap<TransformType, TRSRTransformation> map = PerspectiveMapWrapper.getTransforms(state);
        return new BakedItemModel(builder.build(), particle, map, overrides, null);
    }

    @Nonnull
    public IModelState getDefaultState()
    {
        return TRSRTransformation.identity();
    }

    @Nonnull
    public ItemLayerModel retexture(@Nonnull ImmutableMap<String, String> textures)
    {
        ImmutableList.Builder<ResourceLocation> builder = ImmutableList.builder();
        for (int i = 0; i < textures.size() + this.textures.size(); i++)
        {
            if (textures.containsKey("layer" + i))
            {
                builder.add(new ResourceLocation(textures.get("layer" + i)));
            }
            else if (i < this.textures.size())
            {
                builder.add(this.textures.get(i));
            }
        }
        return new ItemLayerModel(builder.build(), overrides);
    }

    public static class BakedItemModel implements IBakedModel
    {
        @Nonnull
        private final ImmutableList<BakedQuad> quads;
        private final TextureAtlasSprite particle;
        private final ImmutableMap<TransformType, TRSRTransformation> transforms;
        @Nullable
        private final IBakedModel otherModel;
        private final boolean isCulled;
        private final ItemOverrideList overrides;

        public BakedItemModel(
                               @Nonnull ImmutableList<BakedQuad> quads,
                               @Nonnull TextureAtlasSprite particle,
                               @Nonnull ImmutableMap<TransformType, TRSRTransformation> transforms,
                               @Nonnull ItemOverrideList overrides,
                               @Nullable IBakedModel otherModel)
        {
            this.quads = quads;
            this.particle = particle;
            this.transforms = transforms;
            this.overrides = overrides;
            if (otherModel != null)
            {
                this.otherModel = otherModel;
                this.isCulled = true;
            }
            else
            {
                ImmutableList.Builder<BakedQuad> builder = ImmutableList.builder();
                for (BakedQuad quad : quads)
                {
                    if (quad.getFace() == EnumFacing.SOUTH)
                    {
                        builder.add(quad);
                    }
                }
                this.otherModel = new BakedItemModel(builder.build(), particle, transforms, overrides, this);
                isCulled = false;
            }
        }

        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand)
        {
            if (side == null)
            {
                return quads;
            }
            return ImmutableList.of();
        }

        public boolean isAmbientOcclusion()
        {
            return true;
        }

        public boolean isGui3d()
        {
            return false;
        }

        public boolean isBuiltInRenderer()
        {
            return false;
        }

        @Nonnull
        public TextureAtlasSprite getParticleTexture()
        {
            return particle;
        }

        @Nonnull
        public ItemCameraTransforms getItemCameraTransforms()
        {
            return ItemCameraTransforms.DEFAULT;
        }

        @Nonnull
        public ItemOverrideList getOverrides()
        {
            return overrides;
        }

        @Nonnull
        public Pair<? extends IBakedModel, Matrix4f> handlePerspective(@Nonnull TransformType type)
        {
            Pair<? extends IBakedModel, Matrix4f> pair = PerspectiveMapWrapper.handlePerspective(this, transforms, type);
            if (type == TransformType.GUI && !isCulled && pair.getRight() == null)
            {
                return Pair.of(otherModel, null);
            }
            else if (type != TransformType.GUI && isCulled)
            {
                return Pair.of(otherModel, pair.getRight());
            }
            return pair;
        }
    }
}