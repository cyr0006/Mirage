package com.aryan.mirage;

import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aryan.mirage.data.Card;

import java.util.List;

/**
 * 52-card picker grid for SettingsActivity. See SPEC.md "2. SettingsActivity". Shows a
 * plain rank+suit label per cell rather than a card thumbnail -- most of the 52 assets
 * aren't photographed yet (see "Card assets"), and CardRepository deliberately throws
 * on a missing one rather than silently showing nothing, so loading 52 thumbnails here
 * would crash the whole grid until every card exists.
 */
public class CardGridAdapter extends RecyclerView.Adapter<CardGridAdapter.CardViewHolder> {

    public interface OnCardSelectedListener {
        void onCardSelected(Card card);
    }

    private final List<Card> cards = Card.all();
    private final OnCardSelectedListener listener;
    private Card selectedCard;

    public CardGridAdapter(Card initiallySelected, OnCardSelectedListener listener) {
        this.selectedCard = initiallySelected;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TextView textView = new TextView(parent.getContext());
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(8, 24, 8, 24);
        textView.setTextSize(16f);
        textView.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
        return new CardViewHolder(textView);
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        Card card = cards.get(position);
        holder.bind(card, card.equals(selectedCard));
        holder.itemView.setOnClickListener(v -> {
            int previousIndex = cards.indexOf(selectedCard);
            selectedCard = card;
            if (previousIndex >= 0) {
                notifyItemChanged(previousIndex);
            }
            notifyItemChanged(position);
            listener.onCardSelected(card);
        });
    }

    @Override
    public int getItemCount() {
        return cards.size();
    }

    static class CardViewHolder extends RecyclerView.ViewHolder {
        private final TextView textView;

        CardViewHolder(TextView textView) {
            super(textView);
            this.textView = textView;
        }

        void bind(Card card, boolean selected) {
            textView.setText(card.rank.code + suitSymbol(card.suit));
            boolean isRed = card.suit == Card.Suit.HEARTS || card.suit == Card.Suit.DIAMONDS;
            textView.setTextColor(isRed ? Color.RED : Color.BLACK);
            textView.setBackgroundColor(selected ? Color.LTGRAY : Color.WHITE);
        }

        private static String suitSymbol(Card.Suit suit) {
            switch (suit) {
                case SPADES: return "♠";
                case HEARTS: return "♥";
                case DIAMONDS: return "♦";
                case CLUBS: return "♣";
                default: return "";
            }
        }
    }
}
