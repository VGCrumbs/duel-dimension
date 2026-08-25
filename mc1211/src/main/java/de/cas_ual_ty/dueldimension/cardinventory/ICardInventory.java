package de.cas_ual_ty.dueldimension.cardinventory;

import de.cas_ual_ty.dueldimension.card.CardHolder;

import java.util.List;

public interface ICardInventory
{
    int getPagesAmount();
    
    List<CardHolder> getCardsForPage(int page);
    
    void addCard(CardHolder card, int currentPage);
    
    CardHolder extractCard(int page, int index);
    
    void updateCardsList(String search);
    
    int totalCardsSize();
}
