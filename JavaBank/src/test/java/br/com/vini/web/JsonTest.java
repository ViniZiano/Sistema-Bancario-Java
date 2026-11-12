package br.com.vini.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {

    @Test
    @DisplayName("Lê objeto com texto, inteiro, lista, booleano e null")
    void leObjetoCompleto() {
        Object lido = Json.parse("""
                {"pix": ["ana@email.com", "11999990000"], "valor": 10000, "ativo": true, "obs": null}
                """);

        Map<?, ?> mapa = (Map<?, ?>) lido;
        assertEquals(List.of("ana@email.com", "11999990000"), mapa.get("pix"));
        assertEquals(10000L, mapa.get("valor"));
        assertEquals(Boolean.TRUE, mapa.get("ativo"));
        assertNull(mapa.get("obs"));
        assertTrue(mapa.containsKey("obs"));
    }

    @Test
    @DisplayName("Inteiros viram Long e decimais viram Double (a API recusa decimais)")
    void numeros() {
        assertEquals(42L, Json.parse("42"));
        assertEquals(-7L, Json.parse("-7"));
        assertEquals(1.5, Json.parse("1.5"));
    }

    @Test
    @DisplayName("Lê escapes, inclusive \\u e acentos")
    void escapes() {
        assertEquals("a\"b\\c\nd", Json.parse("\"a\\\"b\\\\c\\nd\""));
        assertEquals("é", Json.parse("\"\\u00e9\""));
        assertEquals("ação", Json.parse("\"ação\""));
    }

    @Test
    @DisplayName("Rejeita JSON malformado")
    void rejeitaMalformado() {
        String[] ruins = {"", "{", "{\"a\":}", "{\"a\" 1}", "[1,", "{\"a\":1} lixo", "\"sem fim", "tru", "{'a':1}"};
        for (String ruim : ruins) {
            assertThrows(IllegalArgumentException.class, () -> Json.parse(ruim), "deveria rejeitar: " + ruim);
        }
    }

    @Test
    @DisplayName("Rejeita JSON aninhado demais (proteção contra estouro de pilha)")
    void rejeitaAninhamentoExcessivo() {
        String fundo = "[".repeat(200) + "]".repeat(200);

        assertThrows(IllegalArgumentException.class, () -> Json.parse(fundo));
    }

    @Test
    @DisplayName("Escreve objeto, lista, números e null")
    void escreve() {
        Map<String, Object> mapa = new java.util.LinkedHashMap<>();
        mapa.put("nome", "Ana");
        mapa.put("saldo", 1050L);
        mapa.put("chaves", List.of("a", "b"));
        mapa.put("investimento", null);
        mapa.put("ok", true);

        assertEquals("{\"nome\":\"Ana\",\"saldo\":1050,\"chaves\":[\"a\",\"b\"],\"investimento\":null,\"ok\":true}",
                Json.write(mapa));
    }

    @Test
    @DisplayName("Escapa aspas, quebra de linha e < > na saída")
    void escapaNaSaida() {
        assertEquals("\"a\\\"b\\nc\"", Json.write("a\"b\nc"));
        assertEquals("\"\\u003cscript\\u003e\"", Json.write("<script>"));
    }

    @Test
    @DisplayName("O que se escreve pode ser lido de volta")
    void idaEVolta() {
        Map<String, Object> original = new java.util.LinkedHashMap<>();
        original.put("pix", List.of("ana \"a\"", "x\ny"));
        original.put("valor", 123L);

        assertEquals(original, Json.parse(Json.write(original)));
    }
}
