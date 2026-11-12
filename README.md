# 💰 JavaBank — Sistema bancário em Java

Sistema bancário em **Java 21** com contas de várias chaves Pix, transferências, investimentos com rendimento e auditoria de transações. Funciona de **duas formas**, sobre as mesmas regras de negócio:

- 🖥️ **Menu no terminal** (`br.com.vini.Main`), a versão original do projeto
- 🌐 **Site + API REST** (`br.com.vini.web.WebServer`), feitos com o servidor HTTP do próprio JDK, **sem frameworks e sem dependências externas**

> 🔗 **Demo online:** _em breve (o link entra aqui depois do deploy)_

---

## 💡 Problema e solução

**Problema:** simular operações bancárias exige regras que não podem falhar: não sacar mais do que o saldo, não repetir chave Pix, não deixar uma conta com dois investimentos, e saber de onde veio cada centavo.

**Solução:** o domínio modela o dinheiro como unidades (`Money`) que carregam o próprio histórico de transações (auditoria), e as regras ficam nos repositórios, com exceções de negócio próprias. A camada web só valida a entrada, aplica limites da demonstração e expõe tudo em JSON.

---

## ⚙️ Funcionalidades

**💳 Contas**
- Criar conta com até 3 chaves Pix
- Depósito, saque e transferência entre contas
- Extrato com data, descrição, valor e saldo após cada movimento

**📈 Investimentos**
- Criar investimento com taxa por rodada e aplicação mínima
- Conta aderir a um investimento, aportar e resgatar
- Aplicar uma rodada de rendimento em todas as carteiras

**🔍 Auditoria (no domínio)**
- Cada unidade de dinheiro guarda o ID da transação (UUID), o serviço (`ACCOUNT` ou `INVESTMENT`), a descrição e a data/hora

---

## 🌐 Site e API

Com o servidor no ar, o site abre em `http://localhost:8080`. Cada visitante recebe um id de sessão (gerado no navegador e enviado no cabeçalho `X-Session`) e tem **o próprio banco em memória**, sem ver nem alterar o de ninguém.

Valores sempre em **centavos** (`10000` = R$ 100,00). Respostas de erro têm o formato `{"erro": "mensagem"}`.

| Método | Rota                      | Corpo (JSON)                                  | Descrição                              |
|--------|---------------------------|-----------------------------------------------|----------------------------------------|
| GET    | `/api/health`             | —                                             | Health check (sem sessão)              |
| GET    | `/api/estado`             | —                                             | Contas, investimentos, carteiras e totais |
| GET    | `/api/extrato?pix=...`    | —                                             | Extrato da conta                       |
| POST   | `/api/contas`             | `{"pix":["a","b"],"valor":10000}`             | Cria conta                             |
| POST   | `/api/depositos`          | `{"pix":"a","valor":500}`                     | Deposita                               |
| POST   | `/api/saques`             | `{"pix":"a","valor":500}`                     | Saca                                   |
| POST   | `/api/transferencias`     | `{"origem":"a","destino":"b","valor":500}`    | Transfere                              |
| POST   | `/api/investimentos`      | `{"taxa":5,"valorMinimo":1000}`               | Cria investimento                      |
| POST   | `/api/carteiras`          | `{"pix":"a","investimentoId":1}`              | Conta adere ao investimento            |
| POST   | `/api/aportes`            | `{"pix":"a","valor":500}`                     | Aporta no investimento                 |
| POST   | `/api/resgates`           | `{"pix":"a","valor":500}`                     | Resgata para a conta                   |
| POST   | `/api/rendimentos`        | —                                             | Aplica uma rodada de rendimento        |
| POST   | `/api/reset`              | —                                             | Zera o banco da sessão                 |

Status: `400` dado inválido · `404` conta/investimento não encontrado · `409` chave Pix em uso ou conta já investindo · `422` saldo insuficiente ou limite da demo · `405` método errado.

### Exemplo com curl

```bash
S="minha-sessao-0123456789"     # qualquer texto de 16 a 64 letras, números ou hífen

curl -X POST localhost:8080/api/contas -H "X-Session: $S" -H "Content-Type: application/json" \
  -d '{"pix":["ana@email.com"],"valor":10000}'

curl -X POST localhost:8080/api/depositos -H "X-Session: $S" -H "Content-Type: application/json" \
  -d '{"pix":"ana@email.com","valor":2500}'

curl localhost:8080/api/estado -H "X-Session: $S"
```

---

## ⚠️ Limites da demonstração (de propósito)

O domínio cria **um objeto `Money` por centavo**. Isso deixa a auditoria muito detalhada, mas significa que valores altos consomem muita memória. Por isso a versão web é limitada:

- até **R$ 500,00 por operação** e **R$ 1.000,00 somando contas e investimentos** por visitante
- até 5 contas e 5 investimentos por visitante; até 3 chaves Pix por conta
- até 25 visitantes ao mesmo tempo; sessão some após 30 minutos parada
- **os dados ficam só na memória**: reiniciar o servidor apaga tudo. Não há banco de dados nesta versão

---

## 🧠 Arquitetura

```
JavaBank/src/main/java/br/com/vini/
├── model/        → Wallet, AccountWallet, InvestmentWallet, Investments, Money, MoneyAudit
├── repository/   → AccountRepository, InvestmentsRepository, CommonsRepository (regras de negócio)
├── exception/    → exceções de negócio (conta não encontrada, saldo insuficiente, Pix em uso...)
├── Main.java     → menu no terminal
└── web/          → camada web (nova)
    ├── WebServer     servidor HTTP (com.sun.net.httpserver) e entrega do site
    ├── Api           rotas e status HTTP
    ├── BankFacade    valida entrada, aplica limites, traduz erros, mantém o extrato; acesso sincronizado
    ├── SessionStore  um banco por visitante, com limite e expiração
    └── Json          leitor/escritor de JSON feito à mão
JavaBank/src/main/resources/static/   → site (HTML, CSS e JavaScript)
```

As regras de negócio **não foram alteradas**: a camada web chama os mesmos repositórios do menu de terminal.

---

## ▶️ Como executar

Requer **Java 21**.

```bash
git clone https://github.com/ViniZiano/sistema-bancario-java.git
cd sistema-bancario-java/JavaBank

./gradlew runWeb     # site + API em http://localhost:8080  (porta pela variável PORT)
./gradlew run        # menu no terminal
./gradlew test       # testes
```

### Com Docker

```bash
docker build -t javabank .
docker run -p 8080:8080 javabank
```

---

## 🧪 Testes

A camada web tem 32 testes com JUnit 5 (`./gradlew test`):

- `JsonTest`: leitura/escrita de JSON, escapes, entradas malformadas e proteção contra aninhamento excessivo
- `SessionStoreTest`: isolamento entre visitantes, limite de sessões e expiração por inatividade
- `ApiTest`: fluxo completo de contas e investimentos pelas rotas, erros (400/404/409/422), limites da demo e isolamento entre sessões

O domínio original (`model`/`repository`) ainda não tem testes próprios; é o próximo passo.

---

## 🔄 Integração contínua

`.github/workflows/ci.yml` roda a cada push e pull request:

1. `test`: `./gradlew test jar` com Java 21
2. `docker`: monta a imagem, sobe o container e confere o `/api/health`

---

## 🗺️ Roadmap

- [x] Menu em terminal com regras de negócio e auditoria
- [x] API REST (com o servidor HTTP do JDK, sem frameworks)
- [x] Interface web integrada à API
- [x] Testes automatizados da camada web (JUnit 5)
- [x] Docker e CI com GitHub Actions
- [ ] Testes do domínio (`model` e `repository`)
- [ ] Persistência em banco de dados (hoje é só memória)
- [ ] Migrar a API para Spring Boot
- [ ] Deploy com link público

---

## 👨‍💻 Autor

**Vinícius Figueiredo Ziano**
Estudante de Análise e Desenvolvimento de Sistemas (ADS), UNIBRA

🔗 LinkedIn: [linkedin.com/in/ViniZiano](https://linkedin.com/in/ViniZiano)
🔗 GitHub: [github.com/ViniZiano](https://github.com/ViniZiano)
