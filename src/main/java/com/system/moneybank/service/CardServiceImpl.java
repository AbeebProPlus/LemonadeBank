package com.system.moneybank.service;

import com.system.moneybank.models.Card;
import com.system.moneybank.repository.CardRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CardServiceImpl implements CardService{
    private final CardRepo cardRepo;
    @Override
    public void saveCard(Card card) {
        cardRepo.save(card);
    }

    @Override
    public Card findCardByNumber(String cardNumber) {
        return cardRepo.findByCardNumber(cardNumber);
    }

    @Override
    public Card findCardByAccountNumber(String accountNumber) {
        return cardRepo.findByAccountNumber(accountNumber);
    }
}