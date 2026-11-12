package br.com.vini.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionStoreTest {

    private static final String ANA = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String BIA = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String CAL = "cccccccc-cccc-cccc-cccc-cccccccccccc";

    private static long minutos(long m) {
        return m * 60_000_000_000L;
    }

    @Test
    @DisplayName("O mesmo id devolve o mesmo banco; ids diferentes, bancos separados")
    void isolamentoEntreSessoes() {
        SessionStore store = new SessionStore(10, 30);

        BankFacade deAna = store.obter(ANA);
        assertSame(deAna, store.obter(ANA));
        assertNotSame(deAna, store.obter(BIA));
    }

    @Test
    @DisplayName("Id ausente ou fora do formato é recusado com 400")
    void recusaIdInvalido() {
        SessionStore store = new SessionStore(10, 30);

        for (String ruim : new String[]{null, "", "curto", "tem espaco no meio 123456", "../../etc/passwd-1234567890"}) {
            ApiException erro = assertThrows(ApiException.class, () -> store.obter(ruim));
            assertEquals(400, erro.status());
        }
    }

    @Test
    @DisplayName("Passando do máximo, a sessão menos usada é descartada")
    void descartaAMaisAntiga() {
        SessionStore store = new SessionStore(2, 30);

        BankFacade deAna = store.obter(ANA);
        store.obter(BIA);
        store.obter(ANA);   // Ana foi usada de novo: a menos usada agora é a Bia
        store.obter(CAL);   // estoura o limite: Bia sai

        assertEquals(2, store.tamanho());
        assertSame(deAna, store.obter(ANA));
    }

    @Test
    @DisplayName("Sessão parada além do prazo expira e volta zerada")
    void expiraPorInatividade() {
        long[] agora = {0};
        SessionStore store = new SessionStore(10, 30, () -> agora[0]);

        BankFacade antes = store.obter(ANA);
        agora[0] = minutos(31);
        store.obter(BIA);   // qualquer acesso limpa as expiradas

        assertEquals(1, store.tamanho());
        assertNotSame(antes, store.obter(ANA));
    }

    @Test
    @DisplayName("Usar a sessão renova o prazo")
    void usoRenovaOPrazo() {
        long[] agora = {0};
        SessionStore store = new SessionStore(10, 30, () -> agora[0]);

        BankFacade banco = store.obter(ANA);
        agora[0] = minutos(20);
        store.obter(ANA);
        agora[0] = minutos(40); // 40 min desde a criação, mas só 20 desde o último uso

        assertSame(banco, store.obter(ANA));
    }
}
