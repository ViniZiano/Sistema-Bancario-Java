package br.com.vini.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode
@ToString
@Getter

public class Money {

    private final List<MoneyAudit> history = new ArrayList<>();

    public Money( final MoneyAudit history) {
        this.history.add(history);
    }

    public void addhistory( final MoneyAudit history) {
        this.history.add(history);
    }
}
