package me.chunklock.models;

public enum Difficulty {
    EASY,
    NORMAL,
    HARD,
    IMPOSSIBLE;

    public Difficulty getNext() {
        return switch (this) {
            case EASY -> NORMAL;
            case NORMAL -> HARD;
            case HARD -> IMPOSSIBLE;
            case IMPOSSIBLE -> IMPOSSIBLE;
        };
    }
}