package br.com.vini.web;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Guarda um banco em memória para cada visitante (identificado por um id aleatório que o site gera).
 * Assim cada pessoa que testa a demonstração tem o seu próprio banco, sem ver nem alterar o de ninguém.
 * O número de sessões e o tempo de vida são limitados para a memória do servidor não crescer sem fim.
 */
final class SessionStore {

    static final Pattern ID_VALIDO = Pattern.compile("[A-Za-z0-9-]{16,64}");

    private record Entrada(BankFacade banco, long ultimoAcesso) {
    }

    private final int maximo;
    private final long validadeNanos;
    private final LongSupplier relogio;
    // accessOrder = true: o primeiro da fila é sempre o menos usado recentemente
    private final Map<String, Entrada> sessoes = new LinkedHashMap<>(16, 0.75f, true);

    SessionStore(int maximo, long validadeMinutos) {
        this(maximo, validadeMinutos, System::nanoTime);
    }

    SessionStore(int maximo, long validadeMinutos, LongSupplier relogio) {
        this.maximo = maximo;
        this.validadeNanos = validadeMinutos * 60_000_000_000L;
        this.relogio = relogio;
    }

    synchronized BankFacade obter(String id) {
        if (id == null || !ID_VALIDO.matcher(id).matches()) {
            throw new ApiException(400, "Sessão inválida. Recarregue a página.");
        }
        long agora = relogio.getAsLong();
        removerExpiradas(agora);

        Entrada atual = sessoes.get(id);
        BankFacade banco = atual != null ? atual.banco() : new BankFacade();
        sessoes.put(id, new Entrada(banco, agora));

        while (sessoes.size() > maximo) {
            Iterator<String> it = sessoes.keySet().iterator();
            it.next();
            it.remove(); // descarta a sessão mais antiga
        }
        return banco;
    }

    synchronized int tamanho() {
        return sessoes.size();
    }

    private void removerExpiradas(long agora) {
        Iterator<Entrada> it = sessoes.values().iterator();
        while (it.hasNext()) {
            if (agora - it.next().ultimoAcesso() > validadeNanos) {
                it.remove();
            } else {
                break; // ordenado por último acesso: o resto é mais recente
            }
        }
    }
}
