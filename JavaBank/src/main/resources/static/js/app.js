/* JavaBank — front-end. Conversa com a API Java na mesma origem. Valores trafegam em centavos. */
(() => {
  'use strict';

  const $ = (id) => document.getElementById(id);
  const brl = (centavos) => (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });

  /* ---------- sessão: cada visitante tem o próprio banco no servidor ---------- */
  const SESSION_KEY = 'javabank.session';
  function novoId() {
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
    return 'sess-' + Math.random().toString(36).slice(2) + Date.now().toString(36) + '0000000000';
  }
  let sessionId;
  try { sessionId = localStorage.getItem(SESSION_KEY); } catch { sessionId = null; }
  if (!sessionId) {
    sessionId = novoId();
    try { localStorage.setItem(SESSION_KEY, sessionId); } catch { /* sem armazenamento: vale só nesta aba */ }
  }

  let estado = null;

  /* ---------- API ---------- */
  class ApiError extends Error {}

  async function api(path, body) {
    const opcoes = { headers: { 'X-Session': sessionId } };
    if (body !== undefined) {
      opcoes.method = 'POST';
      opcoes.headers['Content-Type'] = 'application/json';
      opcoes.body = JSON.stringify(body);
    }
    let resp;
    try {
      resp = await fetch(path, opcoes);
    } catch {
      throw new ApiError('Não consegui falar com o servidor. Verifique sua conexão.');
    }
    let dados = null;
    try { dados = await resp.json(); } catch { /* corpo vazio */ }
    if (!resp.ok) throw new ApiError((dados && dados.erro) || 'Algo deu errado. Tente novamente.');
    return dados;
  }

  /* ---------- valores ---------- */
  // "12,50" ou "12.5" ou "12" -> centavos (sem usar ponto flutuante)
  function paraCentavos(texto) {
    const t = String(texto).trim();
    if (!/^\d{1,7}([.,]\d{1,2})?$/.test(t)) return null;
    const [inteiro, frac = ''] = t.replace(',', '.').split('.');
    return Number(inteiro) * 100 + Number((frac + '00').slice(0, 2));
  }

  /* ---------- avisos ---------- */
  let toastTimer;
  function aviso(texto) {
    const el = $('toast');
    el.textContent = texto;
    el.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.hidden = true; }, 4000);
  }
  function erroNa(el, texto) { el.textContent = texto || ''; el.hidden = !texto; }

  /* ---------- helpers de DOM (tudo com textContent: nada digitado vira HTML) ---------- */
  function el(tag, classe, texto) {
    const e = document.createElement(tag);
    if (classe) e.className = classe;
    if (texto !== undefined) e.textContent = texto;
    return e;
  }
  function botao(texto, classe, aoClicar) {
    const b = el('button', classe, texto);
    b.type = 'button';
    b.addEventListener('click', aoClicar);
    return b;
  }

  /* ---------- diálogo genérico ---------- */
  const dialogo = $('opDialog');
  let aoEnviar = null;

  // campos: { id, label, tipo: 'texto'|'valor'|'inteiro'|'select', placeholder, ajuda, opcoes:[{valor,rotulo}], valor }
  function abrirOperacao({ titulo, dica, campos, enviar, textoBotao }) {
    $('opTitle').textContent = titulo;
    $('opHint').textContent = dica || '';
    $('opHint').hidden = !dica;
    $('opSubmit').textContent = textoBotao || 'Confirmar';
    erroNa($('opError'), '');

    const area = $('opFields');
    area.replaceChildren();
    campos.forEach((c) => {
      const rotulo = el('label', 'field');
      rotulo.appendChild(el('span', '', c.label));
      let entrada;
      if (c.tipo === 'select') {
        entrada = document.createElement('select');
        c.opcoes.forEach((o) => {
          const op = document.createElement('option');
          op.value = o.valor;
          op.textContent = o.rotulo;
          entrada.appendChild(op);
        });
      } else {
        entrada = document.createElement('input');
        entrada.type = 'text';
        entrada.inputMode = c.tipo === 'texto' ? 'text' : 'decimal';
        entrada.autocomplete = 'off';
        entrada.placeholder = c.placeholder || '';
        entrada.maxLength = 120;
      }
      entrada.id = 'campo-' + c.id;
      if (c.valor !== undefined) entrada.value = c.valor;
      rotulo.appendChild(entrada);
      if (c.ajuda) rotulo.appendChild(el('small', '', c.ajuda));
      area.appendChild(rotulo);
    });

    aoEnviar = async () => {
      const valores = {};
      for (const c of campos) {
        const bruto = $('campo-' + c.id).value;
        if (c.tipo === 'valor') {
          const centavos = paraCentavos(bruto);
          if (centavos === null) throw new ApiError(`Informe um valor válido em “${c.label}” (ex.: 50 ou 50,90).`);
          valores[c.id] = centavos;
        } else if (c.tipo === 'inteiro') {
          if (!/^\d{1,3}$/.test(bruto.trim())) throw new ApiError(`Informe um número inteiro em “${c.label}”.`);
          valores[c.id] = Number(bruto.trim());
        } else {
          if (!bruto.trim()) throw new ApiError(`Preencha “${c.label}”.`);
          valores[c.id] = bruto.trim();
        }
      }
      await enviar(valores);
    };

    dialogo.showModal();
    const primeiro = area.querySelector('input,select');
    if (primeiro) primeiro.focus();
  }

  $('opForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!aoEnviar) return;
    const botaoEnviar = $('opSubmit');
    botaoEnviar.disabled = true;
    erroNa($('opError'), '');
    try {
      await aoEnviar();
      dialogo.close();
    } catch (err) {
      erroNa($('opError'), err.message);
    } finally {
      botaoEnviar.disabled = false;
    }
  });
  $('opCancel').addEventListener('click', () => dialogo.close());

  // envia uma operação POST, atualiza a tela com o estado devolvido e mostra a mensagem
  async function executar(rota, corpo) {
    const resp = await api(rota, corpo);
    estado = resp.estado;
    renderizar();
    aviso(resp.mensagem);
  }

  /* ---------- renderização ---------- */
  const primeiraChave = (conta) => conta.pix[0];

  function renderizar() {
    const { contas, investimentos, resumo, limites } = estado;

    $('limitText').textContent = brl(limites.totalMaximo);
    $('sumContas').textContent = brl(resumo.saldoContas);
    $('sumInvest').textContent = brl(resumo.totalInvestido);
    $('sumTotal').textContent = brl(resumo.total);
    const pct = Math.min(100, Math.round((resumo.total / limites.totalMaximo) * 100));
    $('meterBar').style.width = pct + '%';
    $('meterText').textContent = `${pct}% do limite da demo`;

    const listaContas = $('accounts');
    listaContas.replaceChildren();
    contas.forEach((c) => listaContas.appendChild(cartaoConta(c)));
    $('accountsEmpty').hidden = contas.length > 0;

    const listaProdutos = $('products');
    listaProdutos.replaceChildren();
    investimentos.forEach((p) => listaProdutos.appendChild(cartaoProduto(p)));
    $('productsEmpty').hidden = investimentos.length > 0;
  }

  function cartaoConta(conta) {
    const card = el('article', 'card');

    const topo = el('div', 'card-top');
    const esquerda = el('div');
    esquerda.appendChild(el('p', 'label', 'Saldo'));
    esquerda.appendChild(el('p', 'balance', brl(conta.saldo)));
    topo.appendChild(esquerda);
    card.appendChild(topo);

    const chips = el('div', 'chips');
    conta.pix.forEach((k) => chips.appendChild(el('span', 'chip', k)));
    card.appendChild(chips);

    if (conta.investimento) {
      const caixa = el('div', 'invest-box');
      const linha = el('div', 'invest-line');
      linha.appendChild(el('span', '', `Investimento #${conta.investimento.id}`));
      linha.appendChild(el('span', 'tag', `${conta.investimento.taxa}% por rodada`));
      caixa.appendChild(linha);
      const valor = el('div', 'invest-line');
      valor.appendChild(el('span', '', 'Investido'));
      valor.appendChild(el('strong', '', brl(conta.investimento.saldo)));
      caixa.appendChild(valor);
      const acoes = el('div', 'actions');
      acoes.appendChild(botao('Aportar', 'mini', () => abrirAporte(conta)));
      acoes.appendChild(botao('Resgatar', 'mini', () => abrirResgate(conta)));
      caixa.appendChild(acoes);
      card.appendChild(caixa);
    }

    const acoes = el('div', 'actions');
    acoes.appendChild(botao('Depositar', 'mini accent', () => abrirDeposito(conta)));
    acoes.appendChild(botao('Sacar', 'mini', () => abrirSaque(conta)));
    acoes.appendChild(botao('Transferir', 'mini', () => abrirTransferencia(conta)));
    acoes.appendChild(botao('Extrato', 'mini', () => abrirExtrato(conta)));
    if (!conta.investimento) acoes.appendChild(botao('Investir', 'mini', () => abrirAdesao(conta)));
    card.appendChild(acoes);
    return card;
  }

  function cartaoProduto(p) {
    const card = el('article', 'card product');
    card.appendChild(el('p', 'label', `Investimento #${p.id}`));
    const grande = el('p', 'big', `${p.taxa}%`);
    grande.appendChild(el('small', '', ' por rodada'));
    card.appendChild(grande);
    card.appendChild(el('p', '', `Aplicação mínima: ${brl(p.valorMinimo)}`));
    return card;
  }

  /* ---------- operações ---------- */
  const rotuloConta = (c) => `${primeiraChave(c)} — ${brl(c.saldo)}`;
  const opcoesContas = (lista) => lista.map((c) => ({ valor: primeiraChave(c), rotulo: rotuloConta(c) }));

  $('newAccountBtn').addEventListener('click', () => {
    abrirOperacao({
      titulo: 'Nova conta',
      dica: `Até ${estado.limites.maxContas} contas na demo.`,
      textoBotao: 'Criar conta',
      campos: [
        { id: 'chaves', label: 'Chaves Pix', tipo: 'texto', placeholder: 'ana@email.com, 11999990000', ajuda: 'Separe por vírgula. De 1 a 3 chaves.' },
        { id: 'valor', label: 'Depósito inicial (R$)', tipo: 'valor', placeholder: '100,00', valor: '0' },
      ],
      enviar: (v) => executar('/api/contas', {
        pix: v.chaves.split(',').map((s) => s.trim()).filter(Boolean),
        valor: v.valor,
      }),
    });
  });

  function abrirDeposito(conta) {
    abrirOperacao({
      titulo: 'Depositar', dica: `Conta ${primeiraChave(conta)}`, textoBotao: 'Depositar',
      campos: [{ id: 'valor', label: 'Valor (R$)', tipo: 'valor', placeholder: '50,00' }],
      enviar: (v) => executar('/api/depositos', { pix: primeiraChave(conta), valor: v.valor }),
    });
  }

  function abrirSaque(conta) {
    abrirOperacao({
      titulo: 'Sacar', dica: `Saldo disponível: ${brl(conta.saldo)}`, textoBotao: 'Sacar',
      campos: [{ id: 'valor', label: 'Valor (R$)', tipo: 'valor', placeholder: '50,00' }],
      enviar: (v) => executar('/api/saques', { pix: primeiraChave(conta), valor: v.valor }),
    });
  }

  function abrirTransferencia(conta) {
    const outras = estado.contas.filter((c) => c !== conta && primeiraChave(c) !== primeiraChave(conta));
    if (outras.length === 0) {
      aviso('Crie uma segunda conta para poder transferir.');
      return;
    }
    abrirOperacao({
      titulo: 'Transferir (Pix)', dica: `De ${primeiraChave(conta)} · saldo ${brl(conta.saldo)}`, textoBotao: 'Transferir',
      campos: [
        { id: 'destino', label: 'Para a conta', tipo: 'select', opcoes: opcoesContas(outras) },
        { id: 'valor', label: 'Valor (R$)', tipo: 'valor', placeholder: '50,00' },
      ],
      enviar: (v) => executar('/api/transferencias', { origem: primeiraChave(conta), destino: v.destino, valor: v.valor }),
    });
  }

  function abrirAdesao(conta) {
    if (estado.investimentos.length === 0) {
      aviso('Crie um investimento primeiro (seção “Investimentos”).');
      return;
    }
    abrirOperacao({
      titulo: 'Investir', dica: `Conta ${primeiraChave(conta)} · saldo ${brl(conta.saldo)}. O valor mínimo do investimento sai da conta.`, textoBotao: 'Aderir',
      campos: [{
        id: 'investimentoId', label: 'Investimento', tipo: 'select',
        opcoes: estado.investimentos.map((p) => ({ valor: String(p.id), rotulo: `#${p.id} · ${p.taxa}% por rodada · mínimo ${brl(p.valorMinimo)}` })),
      }],
      enviar: (v) => executar('/api/carteiras', { pix: primeiraChave(conta), investimentoId: Number(v.investimentoId) }),
    });
  }

  function abrirAporte(conta) {
    abrirOperacao({
      titulo: 'Aportar', dica: `Da conta ${primeiraChave(conta)} (saldo ${brl(conta.saldo)}) para o investimento.`, textoBotao: 'Aportar',
      campos: [{ id: 'valor', label: 'Valor (R$)', tipo: 'valor', placeholder: '50,00' }],
      enviar: (v) => executar('/api/aportes', { pix: primeiraChave(conta), valor: v.valor }),
    });
  }

  function abrirResgate(conta) {
    abrirOperacao({
      titulo: 'Resgatar', dica: `Investido: ${brl(conta.investimento.saldo)}`, textoBotao: 'Resgatar',
      campos: [{ id: 'valor', label: 'Valor (R$)', tipo: 'valor', placeholder: '50,00' }],
      enviar: (v) => executar('/api/resgates', { pix: primeiraChave(conta), valor: v.valor }),
    });
  }

  $('newProductBtn').addEventListener('click', () => {
    abrirOperacao({
      titulo: 'Novo investimento',
      dica: `Cada rodada de rendimento soma essa taxa sobre o valor investido. Taxa de 1% a ${estado.limites.taxaMaxima}%.`,
      textoBotao: 'Criar investimento',
      campos: [
        { id: 'taxa', label: 'Taxa por rodada (%)', tipo: 'inteiro', placeholder: '5' },
        { id: 'valorMinimo', label: 'Aplicação mínima (R$)', tipo: 'valor', placeholder: '100,00' },
      ],
      enviar: (v) => executar('/api/investimentos', { taxa: v.taxa, valorMinimo: v.valorMinimo }),
    });
  });

  $('yieldBtn').addEventListener('click', async () => {
    try { await executar('/api/rendimentos', {}); } catch (err) { erroNa($('pageError'), err.message); aviso(err.message); }
  });

  $('resetBtn').addEventListener('click', async () => {
    if (!confirm('Reiniciar a demonstração? Todas as contas e investimentos serão apagados.')) return;
    try { await executar('/api/reset', {}); erroNa($('pageError'), ''); } catch (err) { erroNa($('pageError'), err.message); }
  });

  /* ---------- extrato ---------- */
  async function abrirExtrato(conta) {
    try {
      const dados = await api('/api/extrato?pix=' + encodeURIComponent(primeiraChave(conta)));
      $('stmtPix').textContent = dados.pix.join(' · ');
      $('stmtBalance').textContent = brl(dados.saldo);
      const lista = $('stmtList');
      lista.replaceChildren();
      dados.movimentos.forEach((m) => {
        const item = el('li', 'stmt-item');
        const esq = el('div');
        const titulo = el('strong', '', m.descricao);
        titulo.appendChild(el('span', 'where' + (m.onde === 'INVESTIMENTO' ? ' inv' : ''), m.onde === 'INVESTIMENTO' ? 'INVEST.' : 'CONTA'));
        esq.appendChild(titulo);
        esq.appendChild(el('small', '', new Date(m.data).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'medium' })));
        const dir = el('div', 'stmt-amount ' + (m.valor >= 0 ? 'pos' : 'neg'), (m.valor >= 0 ? '+ ' : '− ') + brl(Math.abs(m.valor)));
        dir.appendChild(el('small', '', 'saldo ' + brl(m.saldoApos)));
        item.append(esq, dir);
        lista.appendChild(item);
      });
      $('stmtEmpty').hidden = dados.movimentos.length > 0;
      $('statementDialog').showModal();
    } catch (err) {
      aviso(err.message);
    }
  }
  $('stmtClose').addEventListener('click', () => $('statementDialog').close());

  /* ---------- início ---------- */
  api('/api/estado')
    .then((dados) => { estado = dados; renderizar(); })
    .catch((err) => erroNa($('pageError'), err.message));
})();
