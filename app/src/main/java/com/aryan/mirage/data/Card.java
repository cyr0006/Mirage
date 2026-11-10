package com.aryan.mirage.data;

/**
 * One playing card. See SPEC.md "Card assets": filenames are {rank}{suit}.png,
 * e.g. AS.png, 10H.png, KD.png.
 */
public class Card {

    public enum Rank {
        ACE("A"), TWO("2"), THREE("3"), FOUR("4"), FIVE("5"), SIX("6"), SEVEN("7"),
        EIGHT("8"), NINE("9"), TEN("10"), JACK("J"), QUEEN("Q"), KING("K");

        public final String code;

        Rank(String code) {
            this.code = code;
        }

        public static Rank fromCode(String code){
            for (Rank r: values()){
                if  (r.code.equals(code)) return r;
            }
            throw new IllegalArgumentException(("Unknown rank code: " + code));
        }
    }

    public enum Suit {
        SPADES("S"), HEARTS("H"), DIAMONDS("D"), CLUBS("C");

        public final String code;

        Suit(String code) {
            this.code = code;
        }

        public static Suit fromCode(String code){
            for (Suit s: values()){
                if  (s.code.equals(code)) return s;
            }
            throw new IllegalArgumentException(("Unknown suit code: " + code));
        }
    }

    public final Rank rank;
    public final Suit suit;

    public Card(Rank rank, Suit suit) {
        this.rank = rank;
        this.suit = suit;
    }

    /** Asset filename, e.g. "AS.png". */
    public String assetFileName() {
        return rank.code + suit.code + ".png";
    }

    @Override
    public String toString() {
        return rank.code + suit.code;
    }
}
