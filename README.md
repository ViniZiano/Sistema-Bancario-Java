💰 Sistema Bancário com Investimentos (Java)

Sistema bancário desenvolvido em Java que simula operações reais como criação de contas, transferências, investimentos e auditoria de transações.

🚀 Visão Geral

Este projeto implementa um sistema completo de gerenciamento financeiro com:

Contas bancárias com suporte a múltiplas chaves Pix
Transferências entre contas
Sistema de investimentos com rendimento
Auditoria detalhada de transações
Tratamento de exceções de negócio
🧠 Arquitetura

O projeto segue uma estrutura em camadas:

Model → Representação das entidades (Wallet, AccountWallet, InvestmentWallet)
Repository → Gerenciamento de dados e regras de negócio
Exception → Exceções customizadas
Main → Interface de linha de comando (CLI)
📁 Estrutura do Projeto
src/main/java/br/com/vini/
│

├── model/

│   ├── Wallet.java

│   ├── AccountWallet.java

│   ├── InvestmentWallet.java

│   ├── Investments.java

│   ├── Money.java

│   └── MoneyAudit.java

│

├── repository/

│   ├── AccountRepository.java

│   ├── InvestmentsRepository.java

│   └── CommonsRepository.java
│
├── exception/

│   ├── AccountNotFoundException.java

│   ├── NoFundsEnoughException.java

│   ├── PixInUseException.java

│   ├── WalletNotFoundException.java

│   ├── AccountWithInvestmentException.java

│   └── InvestimentNotFoundException.java

│

└── Main.java

⚙️ Funcionalidades

💳 Contas
Criar conta com múltiplas chaves Pix
Depósito
Saque
Transferência entre contas
Consulta de histórico de transações
📈 Investimentos
Criação de investimentos com taxa de rendimento
Vinculação de investimento a conta
Aplicação de dinheiro em investimento
Resgate de valores investidos
Atualização automática de rendimento
🔍 Auditoria de Transações

Cada operação gera um registro contendo:

ID da transação (UUID)
Tipo de serviço (ACCOUNT ou INVESTMENT)
Descrição
Data e hora (OffsetDateTime)
🛠️ Tecnologias Utilizadas
Java 11+
Lombok
API de Streams
Programação Orientada a Objetos (POO)
▶️ Como Executar
1. Clone o repositório
git clone https://github.com/ViniZiano/Sistema-Bancario-Java.git
2. Compile o projeto
javac -d out src/main/java/br/com/vini/**/*.java
3. Execute
java -cp out br.com.vini.Main
🖥️ Menu do Sistema

O sistema funciona via terminal com opções como:

1 - Criar Conta
2 - Criar Investimento
3 - Fazer Investimento
4 - Depositar
5 - Sacar
6 - Transferir
...
⚠️ Regras de Negócio
Uma conta não pode ter mais de um investimento ativo
Não é permitido sacar valores maiores que o saldo
Não é permitido reutilizar chave Pix
Investimentos são removidos quando saldo chega a zero
💡 Diferenciais do Projeto
Modelagem baseada em unidades de dinheiro (Money)
Auditoria detalhada por transação
Separação clara de responsabilidades
Uso de exceções customizadas
Simulação de fluxo financeiro real
🚧 Melhorias Futuras
 Refatoração para uso de Service Layer
 Persistência com banco de dados
 API REST com Spring Boot
 Testes automatizados (JUnit)
 Interface gráfica (Web ou Desktop)
👨‍💻 Autor

Desenvolvido por Vinicius Ziano

⭐ Considerações

Este projeto foi desenvolvido com foco em aprendizado e aplicação de conceitos de backend, podendo ser expandido para um sistema completo com APIs e persistência.
