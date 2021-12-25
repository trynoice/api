package com.trynoice.api.sound.entities;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.List;

/**
 * An entity representing the library manifest as specified in the <a
 * href="https://github.com/trynoice/sound-library/blob/main/library-manifest.schema.json">Sound
 * Library Manifest JSON schema</a>.
 */
@Data
@NoArgsConstructor
public class LibraryManifest {

    /**
     * The path where individual segment clips are accessible at
     * `${segmentsBasePath}/${sound.id}/${segment.name}.m3u8`. It must be a valid path relative to
     * the library manifest.
     */
    @NonNull
    private String segmentsBasePath;

    /**
     * The path where icons are accessible at `${iconsBasePath}/${sound.icon}`. It must be a valid
     * path relative to the library manifest.
     */
    @NonNull
    private String iconsBasePath;

    /**
     * A list of groups for categorising sounds.
     */
    @NonNull
    private List<SoundGroup> groups;

    /**
     * A list of definitions available sounds in the library.
     */
    @NonNull
    private List<Sound> sounds;


    @Data
    @NoArgsConstructor
    public static class SoundGroup {

        /**
         * A unique stable snake-cased identifier for a group.
         */
        @NonNull
        private String id;

        /**
         * A user-presentable name for this group.
         */
        @NonNull
        private String name;
    }


    @Data
    @NoArgsConstructor
    public static class Sound {

        /**
         * A unique stable snake-cased identifier for a sound.
         */
        @NonNull
        private String id;

        /**
         * ID of an existing `Group` to which this sound belongs.
         */
        @NonNull
        private String groupId;

        /**
         * A user-presentable name for this sound.
         */
        @NonNull
        private String name;

        /**
         * A user-presentable icon for this sound. The path must be relative to `baseURL`
         */
        @NonNull
        private String icon;

        /**
         * The upper limit (in seconds) for the amount of silence to add in-between segments for
         * non-contiguous sounds. Clients should randomly choose the length of silence in this range
         * to add after each segment. Moreover, clients must treat sounds as contiguous if
         * `maxSilence` is set to 0.
         */
        @NonNull
        private Integer maxSilence;

        /**
         * A list of segments for this sound.
         */
        @NonNull
        private List<Segment> segments;

        /**
         * A list of details attributing original clip sources, author and license.
         */
        @NonNull
        private List<Source> sources;
    }


    @Data
    @NoArgsConstructor
    public static class Segment {

        /**
         * Clients should use `${name}.m3u8` key to find segments in
         * `${segmentsBasePath}/${soundKey}/` path. If the sound is non-discrete, client can find
         * bridge segments by appending source segment's name to destination segment's name, e.g.
         * `raindrops_light_raindrops_heavy.m3u8`.
         */
        @NonNull
        private String name;

        /**
         * A hint whether a segment is available to unsubscribed users. If a user attempts to access
         * resources despite this hint being `false`, the CDN server must return HTTP 403.
         */
        @NonNull
        private Boolean isFree;
    }


    @Data
    @NoArgsConstructor
    public static class Source {

        /**
         * Name of the source clip(s).
         */
        @NonNull
        private String name;

        /**
         * URL of the source clip.
         */
        @NonNull
        private String url;

        /**
         * SPDX license code for the source clip.
         */
        @NonNull
        private String license;

        /**
         * Author of the source (optional).
         */
        private SourceAuthor author;
    }


    @Data
    @NoArgsConstructor
    public static class SourceAuthor {

        /**
         * Name of the author of the source clip.
         */
        @NonNull
        private String name;

        /**
         * URL of the author of the source clip.
         */
        @NonNull
        private String url;
    }
}
