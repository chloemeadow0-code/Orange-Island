# Parâmetros de Geração

Controle como os modelos geram respostas — do comprimento do contexto às configurações de criatividade.

## Janela de Contexto

**Máximo de Mensagens de Contexto** define quantas mensagens recentes são enviadas ao modelo como contexto. Padrão: **20**.

- **5–20** — Contexto mais curto, respostas mais rápidas, menos uso de tokens
- **20–50** — Contexto mais longo para conversas complexas de múltiplos turnos
- **50–100** — Contexto máximo para discussões muito longas (pode atingir limites de tokens)

Isso se aplica a todos os modelos. A janela de contexto real em tokens depende do seu modelo e do comprimento das mensagens.

---

## Temperatura

Controla a aleatoriedade na saída do modelo. Faixa: **0,0 – 2,0**.

- **0,0 – 0,3** — Mais determinístico, consistente, factual
- **0,5 – 0,8** — Criatividade equilibrada (padrão recomendado)
- **1,0 – 2,0** — Mais aleatório, criativo, imprevisível

Temperatura mais alta significa que o modelo tem mais probabilidade de escolher palavras menos prováveis. Temperatura mais baixa produz saídas mais focadas e repetitivas.

!!! tip "Quando Ajustar"
    - **Código / Fatos**: Use temperatura baixa (0,0 – 0,3)
    - **Escrita Criativa**: Use temperatura alta (0,8 – 1,2)
    - **Chat Geral**: Use temperatura média (0,5 – 0,7)

---

## Top P (Amostragem por Núcleo)

Controla a diversidade da seleção de tokens. Faixa: **0,0 – 1,0**.

O modelo considera apenas o menor conjunto de tokens cuja probabilidade cumulativa exceda `top_p`.

- **0,1** — Muito focado, apenas os tokens mais prováveis
- **0,5** — Diversidade moderada
- **0,9 – 1,0** — Diversidade total (padrão recomendado)

Geralmente você ajusta *ou* a temperatura *ou* o top P — não ambos.

---

## Máximo de Tokens Padrão

Define um limite máximo de tokens para as respostas do modelo. Quando definido, o modelo não gerará mais do que esse número de tokens em uma única resposta. Quando **não definido** (padrão), o máximo próprio do modelo se aplica.

Predefinições disponíveis:

```
256   512   1024   2048
4096  8192  16384  32768
```

!!! tip "Deixe Não Definido para Flexibilidade"
    Para a maioria dos casos de uso, deixe isso não definido. Defina um limite apenas quando precisar de comprimentos de resposta consistentes (ex.: resumos curtos) ou quiser limitar custos.

---

## Penalidade de Frequência

Reduz a tendência do modelo de repetir as mesmas palavras. Faixa: **-2,0 – 2,0**.

- **Valores positivos** (0,1 – 1,0) — Desencoraja repetição
- **Zero** (0,0) — Sem penalidade (padrão)
- **Valores negativos** (-1,0 – -0,1) — Incentiva repetição

---

## Penalidade de Presença

Incentiva o modelo a falar sobre novos tópicos. Faixa: **-2,0 – 2,0**.

- **Valores positivos** (0,1 – 1,0) — Incentiva diversidade de tópicos
- **Zero** (0,0) — Sem penalidade (padrão)
- **Valores negativos** — Mantém o tópico atual

---

## Thinking / Raciocínio

Ativa o raciocínio em cadeia (chain-of-thought) para modelos suportados (ex.: DeepSeek R1, Qwen3, Claude).

Quando ativado, o modelo gera raciocínio interno antes de produzir a resposta final. Isso melhora a precisão para tarefas complexas, mas leva mais tempo e usa mais tokens.

### Nível de Thinking

- **Baixo** — Raciocínio mínimo, mais rápido
- **Médio** — Equilibrado (padrão)
- **Alto** — Raciocínio máximo para problemas complexos

!!! warning "Nem Todos os Modelos Suportam Thinking"
    O modo Thinking requer um modelo que suporte tokens de raciocínio. Se seu modelo não suportar, essa configuração não tem efeito.

---

## Visualizar Rolagem de Contexto

Quando ativado, o 橘子岛 indica visualmente quais mensagens estão incluídas na janela de contexto atual versus quais foram excluídas (devido ao limite da janela de contexto). Isso ajuda você a entender:

- Quanto da sua conversa o modelo pode "ver"
- Quando mensagens mais antigas saem do contexto
- Se você precisa aumentar a janela de contexto

A visualização aparece como um marcador sutil na visualização da conversa.

---

## Como os Parâmetros Funcionam

Todos os parâmetros de geração são **anuláveis** — quando não definidos explicitamente, não são enviados ao modelo, e o modelo usa seus próprios padrões. Cada parâmetro tem uma opção de redefinir para limpar o valor de volta para "não definido".

---

## Substituições por Conversa

Você pode substituir os parâmetros de geração para conversas individuais usando o diálogo de **Configurações Avançadas** na tela de chat (pressione longamente o botão de envio ou use o menu ⋮).
