package br.com.vini.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitor e escritor de JSON mínimo, feito à mão para o projeto não depender de bibliotecas.
 * Lê objetos (Map), listas (List), textos, números (Long ou Double), booleanos e null.
 */
final class Json {

    private static final int PROFUNDIDADE_MAXIMA = 16;

    private final String texto;
    private int pos;

    private Json(String texto) {
        this.texto = texto;
    }

    // ------------------------------------------------------------------ leitura

    static Object parse(String texto) {
        if (texto == null) {
            throw new IllegalArgumentException("JSON vazio");
        }
        Json leitor = new Json(texto);
        leitor.pularEspacos();
        Object valor = leitor.lerValor(0);
        leitor.pularEspacos();
        if (leitor.pos != texto.length()) {
            throw leitor.erro("texto sobrando depois do JSON");
        }
        return valor;
    }

    private Object lerValor(int profundidade) {
        if (profundidade > PROFUNDIDADE_MAXIMA) {
            throw erro("JSON aninhado demais");
        }
        if (pos >= texto.length()) {
            throw erro("fim inesperado");
        }
        char c = texto.charAt(pos);
        return switch (c) {
            case '{' -> lerObjeto(profundidade);
            case '[' -> lerLista(profundidade);
            case '"' -> lerTexto();
            case 't' -> lerLiteral("true", Boolean.TRUE);
            case 'f' -> lerLiteral("false", Boolean.FALSE);
            case 'n' -> lerLiteral("null", null);
            default -> lerNumero();
        };
    }

    private Map<String, Object> lerObjeto(int profundidade) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        pos++; // {
        pularEspacos();
        if (consumir('}')) {
            return mapa;
        }
        do {
            pularEspacos();
            if (pos >= texto.length() || texto.charAt(pos) != '"') {
                throw erro("esperava o nome de um campo");
            }
            String chave = lerTexto();
            pularEspacos();
            if (!consumir(':')) {
                throw erro("esperava ':'");
            }
            pularEspacos();
            mapa.put(chave, lerValor(profundidade + 1));
            pularEspacos();
        } while (consumir(','));
        if (!consumir('}')) {
            throw erro("esperava '}'");
        }
        return mapa;
    }

    private List<Object> lerLista(int profundidade) {
        List<Object> lista = new ArrayList<>();
        pos++; // [
        pularEspacos();
        if (consumir(']')) {
            return lista;
        }
        do {
            pularEspacos();
            lista.add(lerValor(profundidade + 1));
            pularEspacos();
        } while (consumir(','));
        if (!consumir(']')) {
            throw erro("esperava ']'");
        }
        return lista;
    }

    private String lerTexto() {
        pos++; // aspas de abertura
        StringBuilder sb = new StringBuilder();
        while (pos < texto.length()) {
            char c = texto.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c < 0x20) {
                throw erro("caractere de controle dentro do texto");
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= texto.length()) {
                break;
            }
            char e = texto.charAt(pos++);
            switch (e) {
                case '"', '\\', '/' -> sb.append(e);
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (pos + 4 > texto.length()) {
                        throw erro("escape \\u incompleto");
                    }
                    try {
                        sb.append((char) Integer.parseInt(texto.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw erro("escape \\u inválido");
                    }
                    pos += 4;
                }
                default -> throw erro("escape inválido");
            }
        }
        throw erro("texto sem aspas de fechamento");
    }

    private Object lerLiteral(String literal, Object valor) {
        if (!texto.startsWith(literal, pos)) {
            throw erro("valor inválido");
        }
        pos += literal.length();
        return valor;
    }

    private Object lerNumero() {
        int inicio = pos;
        if (pos < texto.length() && texto.charAt(pos) == '-') {
            pos++;
        }
        int digitos = 0;
        while (pos < texto.length() && Character.isDigit(texto.charAt(pos))) {
            pos++;
            digitos++;
        }
        if (digitos == 0) {
            throw erro("valor inválido");
        }
        boolean decimal = false;
        if (pos < texto.length() && (texto.charAt(pos) == '.' || texto.charAt(pos) == 'e' || texto.charAt(pos) == 'E')) {
            decimal = true;
            pos++;
            while (pos < texto.length() && "0123456789+-eE.".indexOf(texto.charAt(pos)) >= 0) {
                pos++;
            }
        }
        String numero = texto.substring(inicio, pos);
        try {
            if (!decimal && digitos <= 18) {
                return Long.parseLong(numero);
            }
            return Double.parseDouble(numero);
        } catch (NumberFormatException e) {
            throw erro("número inválido");
        }
    }

    private void pularEspacos() {
        while (pos < texto.length() && " \t\r\n".indexOf(texto.charAt(pos)) >= 0) {
            pos++;
        }
    }

    private boolean consumir(char esperado) {
        if (pos < texto.length() && texto.charAt(pos) == esperado) {
            pos++;
            return true;
        }
        return false;
    }

    private IllegalArgumentException erro(String motivo) {
        return new IllegalArgumentException("JSON inválido (posição " + pos + "): " + motivo);
    }

    // ------------------------------------------------------------------ escrita

    static String write(Object valor) {
        StringBuilder sb = new StringBuilder();
        escrever(sb, valor);
        return sb.toString();
    }

    private static void escrever(StringBuilder sb, Object valor) {
        if (valor == null) {
            sb.append("null");
        } else if (valor instanceof String s) {
            escreverTexto(sb, s);
        } else if (valor instanceof Boolean || valor instanceof Long || valor instanceof Integer) {
            sb.append(valor);
        } else if (valor instanceof Number n) {
            double d = n.doubleValue();
            sb.append(Double.isFinite(d) ? n.toString() : "null");
        } else if (valor instanceof Map<?, ?> mapa) {
            sb.append('{');
            boolean primeiro = true;
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                if (!primeiro) {
                    sb.append(',');
                }
                primeiro = false;
                escreverTexto(sb, String.valueOf(e.getKey()));
                sb.append(':');
                escrever(sb, e.getValue());
            }
            sb.append('}');
        } else if (valor instanceof Iterable<?> itens) {
            sb.append('[');
            boolean primeiro = true;
            for (Object item : itens) {
                if (!primeiro) {
                    sb.append(',');
                }
                primeiro = false;
                escrever(sb, item);
            }
            sb.append(']');
        } else {
            escreverTexto(sb, valor.toString());
        }
    }

    private static void escreverTexto(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20 || c == '<' || c == '>') {
                        // < e > escapados por segurança caso o JSON seja embutido em HTML
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
