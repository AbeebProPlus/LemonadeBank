package com.system.moneybank.dtos.response;

import com.system.moneybank.models.Card;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CardResponse {
    private String code;
    private String message;
}