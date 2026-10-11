package com.ztv.player.data;

/** Extended VOD metadata from Xtream `get_vod_info`. */
public final class XtreamMovieInfo {
    private final String plot;
    private final String poster;
    private final String genre;
    private final String releaseDate;
    private final String rating;
    private final String duration;
    private final String cast;
    private final String director;
    /** Wide artwork (`backdrop_path[0]`); empty when the panel only has the poster. */
    private final String backdrop;

    public XtreamMovieInfo(String plot, String poster, String genre, String releaseDate,
                           String rating, String duration, String cast, String director) {
        this(plot, poster, genre, releaseDate, rating, duration, cast, director, "");
    }

    public XtreamMovieInfo(String plot, String poster, String genre, String releaseDate,
                           String rating, String duration, String cast, String director, String backdrop) {
        this.plot = plot == null ? "" : plot;
        this.poster = poster == null ? "" : poster;
        this.genre = genre == null ? "" : genre;
        this.releaseDate = releaseDate == null ? "" : releaseDate;
        this.rating = rating == null ? "" : rating;
        this.duration = duration == null ? "" : duration;
        this.cast = cast == null ? "" : cast;
        this.director = director == null ? "" : director;
        this.backdrop = backdrop == null ? "" : backdrop;
    }

    public String backdrop() {
        return backdrop;
    }

    public String plot() {
        return plot;
    }

    public String poster() {
        return poster;
    }

    public String genre() {
        return genre;
    }

    public String releaseDate() {
        return releaseDate;
    }

    public String rating() {
        return rating;
    }

    public String duration() {
        return duration;
    }

    public String cast() {
        return cast;
    }

    public String director() {
        return director;
    }
}
