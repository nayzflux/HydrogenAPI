package fr.nayz.hydrogen.api.account;

public enum RankColor {
    GRAY(0xAAAAAA),
    GOLD(0xFFAA00),
    YELLOW(0xFFFF55),
    AQUA(0x55FFFF),
    BLUE(0x5555FF),
    RED(0xFF5555);

    private final int rgb;

    RankColor(int rgb) {
        this.rgb = rgb;
    }

    public int rgb() {
        return rgb;
    }
}
