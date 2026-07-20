# Geração de Títulos

Gere automaticamente títulos de conversa baseados na primeira troca de mensagens.

## O Que Faz

Quando você inicia uma nova conversa, o 橘子岛 pode gerar automaticamente um título curto e significativo baseado na sua primeira mensagem e na resposta do modelo. Isso substitui o título genérico "Nova Conversa".

## Configuração

1. Vá em **Configurações → Geração de Títulos**
2. Alterne **Gerar títulos automaticamente**
3. Opcionalmente, escolha um **Modelo** para geração de títulos (usa o modelo atual da conversa por padrão)

!!! tip "Escolha do Modelo"
    A geração de títulos usa muito poucos tokens. Você pode usar um modelo barato e rápido (como GPT-4o Mini ou um modelo local) sem afetar a qualidade da sua conversa.

## Como Funciona

1. Você envia sua primeira mensagem em uma nova conversa
2. O modelo responde (como de costume)
3. Após a conclusão da resposta, o 橘子岛 envia uma requisição pequena e separada para gerar um título
4. O título gerado é salvo e exibido na lista de conversas

A geração de títulos é executada apenas uma vez por conversa, na primeira troca.

## Modelo de Geração de Títulos

Você pode usar um modelo diferente especificamente para geração de títulos:

- **Padrão** (sem seleção) — Usa o mesmo modelo da conversa
- **Modelo específico** — Sempre usa esse modelo para toda geração de títulos, independentemente de qual modelo é usado na conversa

Usar um modelo rápido dedicado para títulos pode reduzir latência e custo.
