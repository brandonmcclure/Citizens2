package net.citizensnpcs.npc.skin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import net.citizensnpcs.Settings.Setting;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.npc.profile.ProfileFetchHandler;
import net.citizensnpcs.npc.profile.ProfileFetchResult;
import net.citizensnpcs.npc.profile.ProfileFetcher;
import net.citizensnpcs.npc.profile.ProfileRequest;

/**
 * Stores data for a single skin.
 */
public class Skin {
    private volatile boolean isValid = true;
    private final Map<SkinnableEntity, Void> pending = new WeakHashMap<SkinnableEntity, Void>(15);
    private volatile Property skinData;
    private volatile UUID skinId;
    private final String skinName;

    /**
     * Constructor.
     *
     * @param skinName
     *            The name of the player the skin belongs to.
     */
    Skin(String skinName) {
        this.skinName = skinName.toLowerCase();

        synchronized (CACHE) {
            if (CACHE.containsKey(this.skinName))
                throw new IllegalArgumentException("There is already a skin named " + skinName);

            CACHE.put(this.skinName, this);
        }

        ProfileFetcher.fetch(this.skinName, new ProfileFetchHandler() {
            @Override
            public void onResult(ProfileRequest request) {

                if (request.getResult() == ProfileFetchResult.NOT_FOUND) {
                    isValid = false;
                    return;
                }

                if (request.getResult() == ProfileFetchResult.SUCCESS) {
                    GameProfile profile = request.getProfile();
                    setData(profile);
                }
            }
        });
    }

    /**
     * Apply the skin data to the specified skinnable entity.
     *
     * <p>
     * If invoked before the skin data is ready, the skin is retrieved and the skin is automatically applied to the
     * entity at a later time.
     * </p>
     *
     * @param entity
     *            The skinnable entity.
     *
     * @return True if the skin data was available and applied, false if the data is being retrieved.
     */
    public boolean apply(SkinnableEntity entity) {
        Preconditions.checkNotNull(entity);

        NPC npc = entity.getNPC();

        if (!hasSkinData()) {
            // Use npc cached skin if available.
            // If npc requires latest skin, cache is used for faster
            // availability until the latest skin can be loaded.
            String cachedName = npc.data().get(CACHED_SKIN_UUID_NAME_METADATA);
            if (this.skinName.equals(cachedName)) {
                skinData = new Property(this.skinName,
                        npc.data().<String> get(NPC.PLAYER_SKIN_TEXTURE_PROPERTIES_METADATA),
                        npc.data().<String> get(NPC.PLAYER_SKIN_TEXTURE_PROPERTIES_SIGN_METADATA));

                skinId = UUID.fromString(npc.data().<String> get(CACHED_SKIN_UUID_METADATA));
                setNPCSkinData(entity, skinName, skinId, skinData);

                // check if NPC prefers to use cached skin over the latest skin.
                if (!entity.getNPC().data().get("update-skin", Setting.NPC_SKIN_UPDATE.asBoolean())) {
                    // cache preferred
                    return true;
                }

                if (!Setting.NPC_SKIN_UPDATE.asBoolean()) {
                    // cache preferred
                    return true;
                }
            }

            pending.put(entity, null);
            return false;
        }

        setNPCSkinData(entity, skinName, skinId, skinData);

        return true;
    }

    /**
     * Apply the skin data to the specified skinnable entity and respawn the NPC.
     *
     * @param entity
     *            The skinnable entity.
     */
    public void applyAndRespawn(SkinnableEntity entity) {
        Preconditions.checkNotNull(entity);

        if (!apply(entity))
            return;

        NPC npc = entity.getNPC();

        if (npc.isSpawned()) {
            npc.despawn(DespawnReason.PENDING_RESPAWN);
            npc.spawn(npc.getStoredLocation());
        }
    }

    /**
     * Get the ID of the player the skin belongs to.
     *
     * @return The skin ID or null if it has not been retrieved yet or the skin is invalid.
     */
    @Nullable
    public UUID getSkinId() {
        return skinId;
    }

    /**
     * Get the name of the skin.
     */
    public String getSkinName() {
        return skinName;
    }

    /**
     * Determine if the skin data has been retrieved.
     */
    public boolean hasSkinData() {
        return skinData != null;
    }

    /**
     * Determine if the skin is valid.
     */
    public boolean isValid() {
        return isValid;
    }

    private void setData(@Nullable GameProfile profile) {
        if (profile == null) {
            isValid = false;
            return;
        }

        if (!profile.getName().toLowerCase().equals(skinName)) {
            throw new IllegalArgumentException(
                    "GameProfile name (" + profile.getName() + ") and " + "skin name (" + skinName + ") do not match.");
        }

        skinId = profile.getId();
        skinData = Iterables.getFirst(profile.getProperties().get("textures"), null);

        for (SkinnableEntity entity : pending.keySet()) {
            applyAndRespawn(entity);
        }
    }

    /**
     * Get a skin for a skinnable entity.
     *
     * <p>
     * If a Skin instance does not exist, a new one is created and the skin data is automatically fetched.
     * </p>
     *
     * @param entity
     *            The skinnable entity.
     */
    public static Skin get(SkinnableEntity entity) {
        Preconditions.checkNotNull(entity);

        String skinName = entity.getSkinName().toLowerCase();

        Skin skin;
        synchronized (CACHE) {
            skin = CACHE.get(skinName);
        }

        if (skin == null) {
            skin = new Skin(skinName);
        }

        return skin;
    }

    private static void setNPCSkinData(SkinnableEntity entity, String skinName, UUID skinId, Property skinProperty) {
        NPC npc = entity.getNPC();

        // cache skins for faster initial skin availability
        npc.data().setPersistent(CACHED_SKIN_UUID_NAME_METADATA, skinName);
        npc.data().setPersistent(CACHED_SKIN_UUID_METADATA, skinId.toString());
        if (skinProperty.getValue() != null) {
            npc.data().setPersistent(NPC.PLAYER_SKIN_TEXTURE_PROPERTIES_METADATA, skinProperty.getValue());
            npc.data().setPersistent(NPC.PLAYER_SKIN_TEXTURE_PROPERTIES_SIGN_METADATA, skinProperty.getSignature());

            GameProfile profile = entity.getProfile();
            profile.getProperties().removeAll("textures"); // ensure client does not crash due to duplicate properties.
            profile.getProperties().put("textures", skinProperty);
        } else {
            npc.data().remove(NPC.PLAYER_SKIN_TEXTURE_PROPERTIES_METADATA);
            npc.data().remove(NPC.PLAYER_SKIN_TEXTURE_PROPERTIES_SIGN_METADATA);
        }
    }

    private static final Map<String, Skin> CACHE = new HashMap<String, Skin>(20);
    public static final String CACHED_SKIN_UUID_METADATA = "cached-skin-uuid";
    public static final String CACHED_SKIN_UUID_NAME_METADATA = "cached-skin-uuid-name";
}
