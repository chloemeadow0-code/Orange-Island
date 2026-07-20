# Transcrição de Imagens

Permita que um modelo de visão descreva imagens para que modelos somente de texto possam entendê-las.

## O Que Faz

Quando você envia uma imagem para um modelo somente de texto, o 橘子岛 pode usar um modelo de visão separado para gerar uma descrição em texto da imagem primeiro. Essa descrição é então incluída no prompt enviado ao seu modelo principal.

Isso permite que você use imagens com qualquer modelo, mesmo aqueles que não suportam visão nativamente.

## Configuração

1. Vá em **Configurações → Transcrição de Imagens**
2. Escolha um **Modelo de Transcrição** — deve ser um modelo com capacidade de visão (ex.: GPT-4o, Gemini Flash, Qwen-VL)
3. Adicione modelos em **Modelos Ativados** — estes são os modelos somente de texto que receberão descrições de imagens
4. Ajuste o **Tamanho do Lote** se você enviar muitas imagens de uma vez (quantas imagens descrever por chamada de API)

!!! tip "Modelos de Visão Locais"
    Você pode usar um modelo de visão local (com mmproj) como modelo de transcrição. Isso mantém o processamento de imagens no dispositivo.

## Como Funciona

1. Você anexa uma imagem à sua mensagem
2. O 橘子岛 detecta que seu modelo atual não suporta visão
3. A imagem é enviada primeiro ao modelo de transcrição
4. O modelo de transcrição gera uma descrição em texto
5. Essa descrição é adicionada antes do texto da sua mensagem
6. O texto combinado é enviado ao seu modelo principal

---

## Tamanho do Lote

Controla quantas imagens são descritas por chamada de API ao modelo de transcrição.

- **1** — Descreve uma imagem por vez (mais chamadas de API, mais preciso)
- **5–10** — Descreve múltiplas imagens por chamada (menos chamadas de API, pode perder detalhes)

O padrão depende do dispositivo. Valores menores dão melhores resultados, mas custam mais.

---

## Seleção de Modelo

### Modelo de Transcrição

Este é o modelo de visão que gera as descrições das imagens. Escolha o modelo de visão mais capaz disponível para você.

### Modelos Ativados

Estes são os modelos somente de texto que usarão a transcrição de imagens. Apenas modelos nesta lista receberão descrições de imagens transcritas. Outros modelos receberão imagens diretamente (se as suportarem) ou não receberão nada.
